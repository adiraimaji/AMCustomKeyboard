type format = I4 | I8 | I16 | I24 | U4 | U8 | U16 | U24
type t = format * bytes

let set_int24_be b i v =
  Bytes.set_int16_be b i (v asr 8);
  Bytes.set_uint8 b (i + 2) (v land 0xFF)

let set_uint24_be b i v =
  Bytes.set_uint16_be b i (v lsr 8);
  Bytes.set_uint8 b (i + 2) (v land 0xFF)

let get_int24_be b i =
  (Bytes.get_int16_be b i lsl 8) lor Bytes.get_uint8 b (i + 2)

let get_uint24_be b i =
  (Bytes.get_uint16_be b i lsl 8) lor Bytes.get_uint8 b (i + 2)

(** Access and write functions working with indices instead of byte offsets. *)

let read_uint4 b index =
  let v = Bytes.get_uint8 b (index lsr 1) in
  if index land 1 = 1 then v lsr 4 else v land 0xF

let write_uint4 b index v =
  let index' = index lsr 1 in
  let v = v land 0xF in
  let v' = Bytes.get_uint8 b index' in
  let v =
    if index land 1 = 1 then (v lsl 4) lor (v' land 0xF)
    else v lor (v' land 0xF0)
  in
  Bytes.set_uint8 b index' v

let read_int4 b index =
  let v = read_uint4 b index in
  if v land 0x8 = 0 then v else ~-1 land lnot 0xF lor v

let write_int4 = write_uint4
let read_int16 b index = Bytes.get_int16_be b (index * 2)
let write_int16 b index v = Bytes.set_int16_be b (index * 2) v
let read_int24 b index = get_int24_be b (index * 3)
let write_int24 b index v = set_int24_be b (index * 3) v
let read_uint16 b index = Bytes.get_uint16_be b (index * 2)
let write_uint16 b index v = Bytes.set_uint16_be b (index * 2) v
let read_uint24 b index = get_uint24_be b (index * 3)
let write_uint24 b index v = set_uint24_be b (index * 3) v
let int4_array_size len = (len + 1) / 2

(** Returns the [get], [set] and [array_size] functions. *)
let format_specs = function
  | I4 -> (read_int4, write_int4, int4_array_size)
  | I8 -> (Bytes.get_int8, Bytes.set_int8, Fun.id)
  | I16 -> (read_int16, write_int16, ( * ) 2)
  | I24 -> (read_int24, write_int24, ( * ) 3)
  | U4 -> (read_uint4, write_uint4, int4_array_size)
  | U8 -> (Bytes.get_uint8, Bytes.set_uint8, Fun.id)
  | U16 -> (read_uint16, write_uint16, ( * ) 2)
  | U24 -> (read_uint24, write_uint24, ( * ) 3)

let mk format ar =
  let _get, setf, array_size = format_specs format in
  let ar_len = Array.length ar in
  let b = Bytes.create (array_size ar_len) in
  for i = 0 to ar_len - 1 do
    setf b i (Array.unsafe_get ar i)
  done;
  (format, b)

let format_of_integer n_abs signed =
  if signed then
    if n_abs <= 0x7F then if n_abs <= 0x7 then I4 else I8
    else if n_abs <= 0x7FFF then I16
    else I24
  else if n_abs <= 0xFF then if n_abs <= 0xF then U4 else U8
  else if n_abs <= 0xFFFF then U16
  else U24

let rec min_and_max min_ max_ ar i =
  if i >= Array.length ar then (min_, max_)
  else
    let n = ar.(i) in
    min_and_max (Int.min n min_) (Int.max n max_) ar (i + 1)

let detect_format ?signed ar =
  if Array.length ar = 0 then match signed with Some true -> I8 | _ -> U8
  else
    let first = ar.(0) in
    let min, max_ = min_and_max first first ar 1 in
    let signed = match signed with Some s -> s | None -> min < 0 in
    (* [+ 1] so that [-128] because [127] as they both fit on one byte. *)
    let min_abs = if min < 0 then ~-min - 1 else min in
    let max_abs = Int.max min_abs max_ in
    format_of_integer max_abs signed

let mk_detect ?signed ar = mk (detect_format ?signed ar) ar

let get (format, b) i =
  let get, _set, _size = format_specs format in
  get b i

let set (format, b) i v =
  let _get, set, _size = format_specs format in
  set b i v

let format = fst
let size_bytes (_, b) = Bytes.length b

let length (format, b) =
  let l = Bytes.length b in
  match format with
  | I4 | U4 -> l lsl 1
  | I8 | U8 -> l
  | I16 | U16 -> l lsr 1
  | I24 | U24 -> l / 3

let format_to_string = function
  | I4 -> "I4"
  | I8 -> "I8"
  | I16 -> "I16"
  | I24 -> "I24"
  | U4 -> "U4"
  | U8 -> "U8"
  | U16 -> "U16"
  | U24 -> "U24"
