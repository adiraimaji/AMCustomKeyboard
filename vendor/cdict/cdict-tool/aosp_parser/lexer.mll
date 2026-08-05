{
open Parser

(* Tracking line numbers without allocating [Lexing.position]s. *)
let fname = ref ""
let linen = ref 0

let start fname_ =
  fname := fname_;
  linen := 1

let warn msg =
  Printf.eprintf ("Warning: While reading %S at line %d: " ^^ msg ^^ ".\n%!")
    !fname !linen
}

let sp = [' ' '\t']*
let eol_char = ['\n' '\r']
let field_name = ['a'-'z' 'A'-'Z' '_']+
let field_value = (_ # [','] # eol_char)*
let not_eol = (_ # eol_char)
let eol = '\r'* '\n' '\r'*

rule fields acc = parse
  | ',' sp (field_name as f) '=' (field_value as v) {
      fields ((f, v) :: acc) lexbuf }
  | not_eol as c {
      warn "Invalid field syntax %C" c; discard_line lexbuf; acc }
  | eol | eof { incr linen; acc }

and line_value = parse
  | field_value as first_field { first_field, fields [] lexbuf }

and line = parse
  | sp "dictionary=" { Dictionary (line_value lexbuf) }
  | sp "word=" { Word (line_value lexbuf) }
  | sp "shortcut=" { Shortcut (line_value lexbuf) }
  | sp "bigram=" { discard_line lexbuf; line lexbuf }
  | sp (field_name as p) {
      warn "Unknown line prefix %S" p; discard_line lexbuf; line lexbuf }
  | sp eol { incr linen; line lexbuf }
  | _ as c { warn "Invalid syntax at %C" c; failwith "Parsing error" }
  | eof { Eof }

and discard_line = parse
  | not_eol* eol { incr linen }
