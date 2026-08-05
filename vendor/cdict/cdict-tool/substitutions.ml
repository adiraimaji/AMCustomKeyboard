open Bytesrw
module M = Map.Make (String)

module Map = struct
  type t = string M.t

  let jsont = Jsont.Object.as_string_map Jsont.string
end

type t = Map.t * Re.Str.regexp

let of_map m =
  let regexp =
    M.fold (fun k _ acc -> Re.str k :: acc) m [] |> Re.alt |> Re.compile
  in
  (m, regexp)

let empty = of_map M.empty

let of_chan in_chan =
  match
    Bytes.Reader.of_in_channel in_chan
    |> Jsont_bytesrw.decode ~locs:true Map.jsont
  with
  | Ok m -> of_map m
  | Error msg -> failwith msg

let of_file_opt = function
  | Some f -> In_channel.with_open_bin f of_chan
  | None -> empty

let apply (map, re) word =
  if Re.execp re word then
    let f grp = M.find (Re.Group.get grp 0) map in
    [ Re.replace re ~f word ]
  else []
