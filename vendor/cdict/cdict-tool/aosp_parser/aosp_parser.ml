include Types

let parse ~fname in_chan : t =
  Lexer.start fname;
  let lexbuf = Lexing.from_channel ~with_positions:false in_chan in
  try Parser.dictionary Lexer.line lexbuf
  with Parser.Error ->
    Printf.ksprintf failwith "Failed parsing %S at line %d" fname !Lexer.linen
