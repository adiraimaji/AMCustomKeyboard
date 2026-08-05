module IntMap = Map.Make (Int)

module Id : sig
  type t = private int

  val zero : t

  val fresh : unit -> t
  (** Returns a unique ID. *)

  val compare : t -> t -> int

  module Map : Map.S with type key = t
end = struct
  type t = int

  let compare = Int.compare
  let zero = 0
  let _uniq = ref 0

  let fresh () =
    incr _uniq;
    !_uniq (* Starts at 1. *)

  module Map = IntMap
end

module M = Id.Map

type id = Id.t

type transition = {
  c : char;
  next : id;
  number : int;
      (** Equal to [-1] during construction, computed in a second step. *)
  final : bool;
}

type state = transition list
(** Sorted by [c] *)

type t = state M.t

let state m id = M.find id m
let root_state m = state m Id.zero

let new_state m tr =
  let id = Id.fresh () in
  (M.add id tr m, id)

let str_separate s index =
  (String.sub s 0 index, String.sub s index (String.length s - index))

let rec list_last = function
  | [] -> None
  | [ x ] -> Some x
  | _ :: tl -> list_last tl

let pp ppf m =
  let module S = Set.Make (Id) in
  let fpf = Format.fprintf in
  let pplist pp_a = Format.(pp_print_list ~pp_sep:pp_print_space pp_a) in
  let seen = ref S.empty in
  let rec pp_st ppf sti =
    match M.find_opt sti m with
    | Some _ when S.mem sti !seen -> fpf ppf "(%d seen)" (sti :> int)
    | Some trs ->
        seen := S.add sti !seen;
        fpf ppf "%-4d@[<v>%a@]" (sti :> int) (pplist pp_tr) trs
    | None -> fpf ppf " <removed>"
  and pp_tr ppf tr =
    fpf ppf "%C @[<v>(n=%d)" tr.c tr.number;
    if tr.final then fpf ppf " (final)";
    fpf ppf "@ %a@]" pp_st tr.next
  in
  fpf ppf ".%a" pp_st Id.zero

module State = struct
  type t = state

  let hash = Hashtbl.hash
  let equal = ( = )
end

(** The construction algorithm is from the paper:
    {v
    Incremental Construction of Minimal Acyclic Finite-State Automata
    by Jan Daciuk, Stoyan Mihov, Bruce Watson, Richard Watson
    https://arxiv.org/pdf/cs/0007009
    v}
    With the following differences:

    - Final transitions are used instead of final nodes to represent the end of
      words.
    - Duplicated words in the input list do not crash the program.
    - Additional bookkeeping is added to reduce the size of the register during
      construction.
    - The incremental algorithm is not used.

    The perfect hashing scheme is from the paper:
    {v
    Applications of Finite Automata Representing Large Vocabularies
    by Cláudio L. Lucchesi and Tomasz Kowaltowski
    https://www.cs.mun.ca/~harold/Courses/Old/CS4750.F14/Diary/1992-001.pdf
    v}
    With the following differences:

    - Only the 'number' field from the traversed transitions need to be read.
      The preceding transitions in the traversed nodes are no longer taken into
      account. *)

module R = Hashtbl.Make (State)
(** Register *)

let common_prefix m word =
  let rec loop id i =
    if i >= String.length word then (i, id, M.find id m)
    else
      let st = M.find id m in
      let c = word.[i] in
      match List.find_opt (fun tr -> tr.c = c) st with
      | Some { next; _ } -> loop next (i + 1)
      | None -> (i, id, st)
  in
  loop Id.zero 0

let rec with_last_child st q =
  match st with
  | [] -> assert false
  | [ tr ] -> [ { tr with next = q } ]
  | hd :: tl -> hd :: with_last_child tl q

(** Assumes that no prefix of [suffix] is present in [st]. *)
let add_suffix m sti st suffix =
  let len = String.length suffix in
  let rec loop m i =
    let (m, next), final =
      if i + 1 = len then (new_state m [], true)
      else
        let m, tr' = loop m (i + 1) in
        (new_state m [ tr' ], false)
    in
    (m, { c = suffix.[i]; next; number = ~-1; final })
  in
  if len = 0 then (* Remove a duplicate. *) m
  else
    let m, tr' = loop m 0 in
    M.add sti (st @ [ tr' ]) m

(** Takes both the state id and the state to avoid unecessary lookups. Returns
    the updated version of the state. *)
let rec replace_or_register reg m sti st =
  match list_last st with
  | None -> (* No children *) (m, st)
  | Some { c = _; next = childi; _ } -> (
      let m, child = replace_or_register reg m childi (M.find childi m) in
      match R.find_opt reg child with
      | Some qi when qi = childi -> (m, st)
      | Some qi ->
          let st = with_last_child st qi in
          let m = M.add sti st (M.remove childi m) in
          (m, st)
      | None ->
          R.add reg child childi;
          (m, st))

let add_word_sorted reg m word =
  let prefix_len, last_statei, last_state = common_prefix m word in
  let _, current_suffix = str_separate word prefix_len in
  let m, last_state = replace_or_register reg m last_statei last_state in
  add_suffix m last_statei last_state current_suffix

let numbers_state m =
  let rec map_trs m index acc = function
    | tr :: tl ->
        let acc = { tr with number = index } :: acc in
        let m, size = map_st m 0 tr.next in
        let size = if tr.final then size + 1 else size in
        map_trs m (index + size) acc tl
    | [] -> (m, index, List.rev acc)
  and map_st m index sti =
    let m, size, trs = map_trs m 0 [] (M.find sti m) in
    (M.add sti trs m, index + size)
  in
  fst (map_st m 0 Id.zero)

let of_sorted_iter iter =
  let reg = R.create 4096 in
  let acc = ref (M.singleton Id.zero []) in
  iter (fun word -> acc := add_word_sorted reg !acc word);
  let m = !acc in
  let m, _ = replace_or_register reg m Id.zero (M.find Id.zero m) in
  numbers_state m

let of_sorted_list words = of_sorted_iter (fun f -> List.iter f words)
