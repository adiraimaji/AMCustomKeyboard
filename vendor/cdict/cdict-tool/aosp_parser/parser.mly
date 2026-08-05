%{
open Types
let field_get = List.assoc
%}

%token <string * Types.fields> Dictionary
%token <string * Types.fields> Word
%token <string * Types.fields> Shortcut
%token Eof

%start dictionary
%type <t> dictionary

%%

shortcut:
  | Shortcut { fst ($1) }

word:
  | Word list(shortcut) {
    let w, fields = $1 in
    let w_freq = int_of_string (field_get "f" fields) in
    { w; w_freq; w_shortcuts = $2 }
  }

dictionary:
  | Dictionary list(word) Eof { ignore $1; { words = $2 } }

%%
