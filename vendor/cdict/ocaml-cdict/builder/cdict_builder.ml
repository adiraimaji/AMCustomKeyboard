open Constants

module Optimized = struct
  (** Encode a trie into a form closer to libcdict's format, taking advantages
      of possible optimisations. *)

  module Id : sig
    type t = private int

    val fresh : unit -> t
    val compare : t -> t -> int
  end = struct
    type t = int

    let _uniq = ref 0

    let fresh () =
      incr _uniq;
      !_uniq (* Starts at 1. *)

    let compare = Int.compare
  end

  module IdMap = Map.Make (Id)

  type tr = { final : bool; next : Id.t }

  type branches = {
    labels : string;  (** Labels encodes a binary tree. *)
    branches : tr array;  (** Same length as [labels]. *)
    numbers : int array;  (** Same length as [labels]. *)
  }

  type node = Branches of branches | Prefix of string * tr

  type t = node IdMap.t * Id.t
  (** Second argument is the root id. *)

  let add ids node =
    let id = Id.fresh () in
    (IdMap.add id node ids, id)

  let find = IdMap.find

  let encode_branches brs =
    let length = List.length brs in
    let tree = Complete_tree.(to_array (of_sorted_list brs)) in
    let labels = String.init length (fun i -> fst tree.(i)) in
    let branches = Array.map (fun (_, (_, tr)) -> tr) tree in
    let numbers = Array.map (fun (_, (n, _)) -> n) tree in
    Branches { labels; branches; numbers }

  module Seen = Dfa.Id.Map
  open Dfa

  let rec fold_prefix dfa prefix next final =
    (* The maximum prefix length is [C.c_MAX_PTR_NUMBER] because it is encoded
       in the number field. *)
    if final || String.length prefix >= C.c_PREFIX_MAX_LENGTH then
      (prefix, next, final)
    else
      match state dfa next with
      | [ { c; next; number = 0; final } ] ->
          let prefix = prefix ^ String.make 1 c in
          fold_prefix dfa prefix next final
      | _ -> (prefix, next, final)

  let rec node_of_dfa seen ids dfa sti =
    match Seen.find_opt sti !seen with
    | Some id -> (ids, id)
    | None ->
        let ids, id = node_of_dfa_uncached seen ids dfa (Dfa.state dfa sti) in
        seen := Seen.add sti id !seen;
        (ids, id)

  and node_of_dfa_uncached seen ids dfa = function
    | [ { c; next; number = 0; final } ] ->
        let prefix, next, final =
          fold_prefix dfa (String.make 1 c) next final
        in
        let ids, next = node_of_dfa seen ids dfa next in
        add ids (Prefix (prefix, { next; final }))
    | trs ->
        let ids, branches =
          List.fold_right
            (fun { c; next; number; final } (ids, acc) ->
              let ids, next = node_of_dfa seen ids dfa next in
              (ids, (c, (number, { next; final })) :: acc))
            trs (ids, [])
        in
        assert (branches = List.sort compare branches);
        let node = encode_branches branches in
        add ids node

  let of_dfa dfa =
    let seen = ref Seen.empty in
    node_of_dfa_uncached seen IdMap.empty dfa (Dfa.root_state dfa)
end

module Freq : sig
  (** Encode an array of frequency into a 4 bits integer array using linear
      interpolation, losing precision. *)

  type t = private string

  val of_int_array : int array -> t

  val size : t -> int
  (** Size in bytes *)

  val get : t -> int -> int
  (** Get the frequency at the specified index. *)

  val to_int_list : t -> int list
  (** Append an extra [0] at the end if the number of frequency was originally
      odd. *)
end = struct
  type t = string

  let of_int_array_raw freq =
    let len = Array.length freq in
    let s = Bytes.create ((len + 1) / 2) in
    for f_i = 0 to len - 1 do
      let s_i = f_i / 2 in
      let f =
        let f = freq.(f_i) land 0xF in
        if f_i land 1 = 0 then f else (f lsl 4) lor Bytes.get_uint8 s s_i
      in
      Bytes.set_uint8 s s_i f
    done;
    Bytes.unsafe_to_string s

  let of_int_array freq =
    let freq_compressed =
      (* Compute the 0x10 medians and replace every frequencies by the cluster
         index they are assigned to. This compresses frequencies to a 4-bits
         number by loosing information. *)
      K_medians.k_medians freq 0x10 ~compare:Int.compare ~renumber:(fun _ c ->
          c)
    in
    of_int_array_raw freq_compressed

  let size = String.length

  let get t i =
    let c = Char.code t.[i / 2] in
    let c = if i land 1 = 0 then c else c lsr 4 in
    c land 0xF

  let to_int_list t = List.init (size t * 2) (get t)
end

module Aliases = struct
  (** An associative table from word indexes to word indexes implemented as a
      complete binary tree encoded as arrays. *)

  type t = {
    keys : Sized_int_array.t;
    values : Sized_int_array.t;
    length : int;
  }

  (** Search for the entry in the array where [cmp] returns [0]. [cmp x] must
      return an integer smaller than [0] when the searched entry is smaller than
      [x] and an integer bigger than [0] when it is bigger than [x]. *)
  let bsearch ar cmp =
    let rec loop ar cmp lo hi =
      if lo >= hi then None
      else
        let mid = (lo + hi) / 2 in
        let d = cmp ar.(mid) in
        if d = 0 then Some mid
        else if d < 0 then loop ar cmp lo mid
        else loop ar cmp (mid + 1) hi
    in
    loop ar cmp 0 (Array.length ar)

  let empty () =
    let ar = Sized_int_array.mk U8 [||] in
    { keys = ar; values = ar; length = 0 }

  let of_sorted_list ~alias words =
    let input_length = Array.length words in
    let ar = Dynarray.create () in
    let search w = bsearch words (fun (w', _) -> String.compare w w') in
    let rec resolve w =
      match search w with
      | Some i as r -> (
          match alias (snd words.(i)) with Some w' -> resolve w' | None -> r)
      | None -> None
    in
    for i = 0 to input_length - 1 do
      match Option.bind (alias (snd words.(i))) resolve with
      | Some i2 -> Dynarray.add_last ar (i, i2)
      | None -> ()
    done;
    let ar = Dynarray.to_array ar |> Complete_tree.of_sorted_array in
    let mk_sized f = Sized_int_array.mk_detect ~signed:false (Array.map f ar) in
    { keys = mk_sized fst; values = mk_sized snd; length = Array.length ar }
end

type 'a t = {
  name : string;
  dfa : Optimized.t;
  freq : Freq.t;
  aliases : Aliases.t;
}

let of_list ~name ~freq ?alias words =
  let words = List.sort_uniq (fun (a, _) (b, _) -> String.compare a b) words in
  let words = Array.of_list words in
  let dfa =
    Dfa.of_sorted_iter (fun f -> Array.iter (fun (w, _) -> f w) words)
    |> Optimized.of_dfa
  in
  let freq = Freq.of_int_array (Array.map (fun (_, data) -> freq data) words) in
  let aliases =
    match alias with
    | Some alias -> Aliases.of_sorted_list ~alias words
    | None -> Aliases.empty ()
  in
  { name; dfa; freq; aliases }

module Buf = struct
  type t = { mutable b : bytes; mutable end_ : int }
  (** Not using [Buffer] because we need to write back into already written data
      as we want to place some nodes before others. *)

  let create initial_size = { b = Bytes.create initial_size; end_ = 0 }
  let to_string b = Bytes.sub_string b.b 0 b.end_
  let output out_chan b = Out_channel.output out_chan b.b 0 b.end_

  module Open = struct
    let w_int32 b node_off off i = Bytes.set_int32_be b.b (node_off + off) i
    let w_uint8 b node_off off i = Bytes.set_uint8 b.b (node_off + off) i

    let w_int24 b node_off off i =
      Sized_int_array.set_int24_be b.b (node_off + off) i

    (* let w_bzero b node_off off len = Bytes.fill b.b (node_off + off) len '\000' *)

    let w_str b node_off off s =
      Bytes.blit_string s 0 b.b (node_off + off) (String.length s)

    let w_bytes b node_off off s =
      Bytes.blit s 0 b.b (node_off + off) (Bytes.length s)
  end

  include Open
end

module Ptr : sig
  type t

  val v : final:bool -> int -> t
  val encode : node_off:int -> t -> int
end = struct
  type t = { final : bool; address : int }

  let v ~final address = { final; address }

  let encode ~node_off { final; address } =
    let final_flag = if final then C.flag_PTR_FLAG_FINAL else 0 in
    (* Offsets are relative *)
    let offset = address - node_off in
    assert (offset land C.mask_PTR_OFFSET_MASK = offset);
    final_flag lor offset
end

module Writer = struct
  open Buf.Open

  let[@inline] align2 offset =
    let down = offset land lnot 1 in
    if down = offset then down else down + 2

  module Seen = Optimized.IdMap

  let alloc b n =
    let off = b.Buf.end_ in
    let off = align2 off in
    let end_ = off + n in
    b.end_ <- end_;
    let b_len = Bytes.length b.b in
    if end_ > b_len then (
      let newb = Bytes.create (end_ * 2) in
      Bytes.blit b.b 0 newb 0 b_len;
      b.b <- newb);
    off

  let format_t_of_array ar =
    match Sized_int_array.format ar with
    | I4 | U4 -> C.c_FORMAT_4_BITS
    | I8 | U8 -> C.c_FORMAT_8_BITS
    | I16 | U16 -> C.c_FORMAT_16_BITS
    | I24 | U24 -> C.c_FORMAT_24_BITS

  let rec write_node seen b nodes { Optimized.final; next } =
    let offset =
      match Seen.find_opt next !seen with
      | Some node -> node
      | None ->
          let node =
            match Optimized.find next nodes with
            (* | exception Not_found -> . *)
            | Optimized.Prefix (p, tr) -> write_prefix_node seen b nodes p tr
            | Branches brs -> write_branches_node seen b nodes brs
          in
          seen := Seen.add next node !seen;
          node
    in
    Ptr.v ~final offset

  and write_prefix_node seen b nodes p next =
    let len = String.length p in
    let off = alloc b (S.prefix_t + len) in
    let next_ptr = write_node seen b nodes next in
    let next_ptr = Ptr.encode ~node_off:off next_ptr in
    let header = (len lsl C.c_PREFIX_LENGTH_OFFSET) lor C.tag_PREFIX in
    w_uint8 b off O.prefix_t_header header;
    w_int24 b off O.prefix_t_next_ptr next_ptr;
    w_str b off O.prefix_t_prefix p;
    off

  and write_branches_node seen b nodes { labels; branches; numbers } =
    (* Write all the children nodes before, to compress the 'branches' array
        according to the needs. *)
    let branches = Array.map (fun tr -> write_node seen b nodes tr) branches in
    let off = alloc b 0 in
    let branches = Array.map (Ptr.encode ~node_off:off) branches in
    let branches = Sized_int_array.mk_detect ~signed:true branches in
    let numbers = Sized_int_array.mk_detect ~signed:false numbers in
    let length = String.length labels in
    let branches_start_off = S.branches_t + length in
    let numbers_start_off =
      branches_start_off + Sized_int_array.size_bytes branches
    in
    let header =
      (format_t_of_array branches lsl C.c_BRANCHES_BRANCHES_FORMAT_OFFSET)
      lor (format_t_of_array numbers lsl C.c_BRANCHES_NUMBERS_FORMAT_OFFSET)
      lor C.tag_BRANCHES
    in
    let off' =
      alloc b (numbers_start_off + Sized_int_array.size_bytes numbers)
    in
    assert (off = off');
    w_uint8 b off O.branches_t_header header;
    w_uint8 b off O.branches_t_length length;
    w_str b off O.branches_t_labels labels;
    w_bytes b off branches_start_off (snd branches);
    w_bytes b off numbers_start_off (snd numbers);
    off

  let write_aliases b dheader_off { Aliases.keys; values; length } =
    let keys_off = alloc b (Sized_int_array.size_bytes keys) in
    let values_off = alloc b (Sized_int_array.size_bytes values) in
    let header =
      (format_t_of_array keys lsl C.c_ALIASES_KEYS_FORMAT_OFFSET)
      lor (format_t_of_array values lsl C.c_ALIASES_VALUES_FORMAT_OFFSET)
    in
    w_bytes b keys_off 0 (snd keys);
    w_bytes b values_off 0 (snd values);
    w_uint8 b dheader_off O.dict_header_t_aliases_header header;
    w_int24 b dheader_off O.dict_header_t_aliases_length length;
    w_int32 b dheader_off O.dict_header_t_aliases_keys (Int32.of_int keys_off);
    w_int32 b dheader_off O.dict_header_t_aliases_values
      (Int32.of_int values_off)

  (** Write [dict_header_t] fields to [dheader_off]. *)
  let write_tree b dheader_off { name; dfa = nodes, root_id; freq; aliases } =
    let seen = ref Seen.empty in
    let root_tr = { Optimized.final = false; next = root_id } in
    let root_ptr = write_node seen b nodes root_tr in
    let root_ptr = Ptr.encode ~node_off:0 root_ptr in
    let freq_off = alloc b (Freq.size freq) in
    let name_off = alloc b (String.length name + 1) in
    write_aliases b dheader_off aliases;
    w_int32 b dheader_off O.dict_header_t_name_off (Int32.of_int name_off);
    w_int32 b dheader_off O.dict_header_t_root_ptr (Int32.of_int root_ptr);
    w_int32 b dheader_off O.dict_header_t_freq_off (Int32.of_int freq_off);
    w_str b freq_off 0 (freq :> string);
    w_str b name_off 0 name;
    w_uint8 b name_off (String.length name) 0

  let write_trees b trees =
    let dict_count = List.length trees in
    let header_off = alloc b (S.header_t + (S.dict_header_t * dict_count)) in
    assert (header_off = 0);
    let dict_header i = header_off + S.header_t + (S.dict_header_t * i) in
    List.iteri (fun i tree -> write_tree b (dict_header i) tree) trees;
    w_str b header_off O.header_t_magic C.c_HEADER_MAGIC;
    w_uint8 b header_off O.header_t_version C.c_FORMAT_VERSION;
    w_uint8 b header_off O.header_t_dict_count dict_count
end

let to_buf trees =
  let b = Buf.create 1_000_000 in
  Writer.write_trees b trees;
  b

let to_string trees = Buf.to_string (to_buf trees)
let output trees out_chan = Buf.output out_chan (to_buf trees)

let hist (type a) to_s key ppf lst =
  let module M = Map.Make (struct
    type t = a

    let compare = compare
  end) in
  Format.fprintf ppf "@[<hov 0>|";
  List.fold_left
    (fun h e ->
      let k = key e in
      let n = try M.find k h + 1 with Not_found -> 1 in
      M.add k n h)
    M.empty lst
  |> M.iter (fun k c -> Format.fprintf ppf " %2s: %-4d@ |" (to_s k) c);
  Format.fprintf ppf "@]"

let hist_int key ppf lst = hist string_of_int key ppf lst
let hist_str key ppf lst = hist Fun.id key ppf lst

let stats ppf { name = _; dfa = tree, _root_id; freq; aliases } =
  let open Optimized in
  let str_of_node_kind = function
    | Prefix _ -> "Prefix"
    | Branches _ -> "Branches"
  in
  let str_of_node_kind' tr = str_of_node_kind (IdMap.find tr.next tree) in
  let tr_is_final tr = if tr.final then "Final" else "Non-final" in
  let pp_transitions ppf trs =
    Format.fprintf ppf "@[<v 2>Transitions: %d@ %a@]" (List.length trs)
      (hist_str tr_is_final) trs
  in
  let nodes = IdMap.fold (fun _ n acc -> n :: acc) tree [] in
  Format.fprintf ppf "Nodes: %d@\n" (List.length nodes);
  let branches =
    List.filter_map (function Branches b -> Some b | _ -> None) nodes
  in
  Format.fprintf ppf
    "@[<v 2>Branch nodes: %d@ With size:@ %a@ %a@ With numbers format: %a@]@\n"
    (List.length branches)
    (hist_int (fun b -> String.length b.labels))
    branches pp_transitions
    (List.concat_map (fun b -> Array.to_list b.branches) branches)
    (hist_str (fun b ->
         Sized_int_array.(format_to_string (detect_format b.numbers))))
    branches;
  let prefixes =
    List.filter_map
      (function Prefix (p, id) -> Some (p, id) | _ -> None)
      nodes
  in
  let prefixes_trs = List.map snd prefixes in
  Format.fprintf ppf
    "@[<v 2>Prefix nodes: %d@ Followed by:@ %a@ With size:@ %a@ %a@]@\n"
    (List.length prefixes)
    (hist_str str_of_node_kind')
    prefixes_trs
    (hist_int (fun (p, _) -> String.length p))
    prefixes pp_transitions prefixes_trs;
  let freq = Freq.to_int_list freq in
  Format.fprintf ppf "@[<v 2>Freq: %d@ With value:@ %a@]@\n" (List.length freq)
    (hist_int Fun.id) freq;
  Format.fprintf ppf "@[<v 2>Aliases: %d@]@\n" aliases.length;
  ()

let rec pp freq nodes index ppf id =
  let open Optimized in
  let fpf fmt = Format.fprintf ppf fmt in
  match IdMap.find id nodes with
  | Branches { labels; branches; numbers } ->
      fpf "@[<v 2>Branches@ ";
      for i = 0 to Array.length branches - 1 do
        let n = numbers.(i) in
        fpf "%c %a@ " labels.[i] (pp_tr freq nodes (index + n)) branches.(i)
      done;
      fpf "@]"
  | Prefix (prefix, next) ->
      fpf "@[<v 2>Prefix %S@ %a@]" prefix (pp_tr freq nodes index) next

and pp_tr freq nodes index ppf tr =
  let index = if tr.final then index + 1 else index in
  Format.fprintf ppf "freq=%d@ " (Freq.get freq index);
  pp freq nodes index ppf tr.next

let pp ppf { name = _; dfa = nodes, root_id; freq; aliases = _ } =
  pp freq nodes 0 ppf root_id

module Complete_tree = Complete_tree
module K_medians = K_medians
module Sized_int_array = Sized_int_array
