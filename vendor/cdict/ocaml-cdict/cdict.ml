type t
type index = private int
type node
type ptr

type result = {
  found : bool;  (** Whether the word is recognized. *)
  index : index;
      (** Unique index of the word, can be used to lookup word metadata. *)
  prefix_node : node;
      (** Internal pointer used to list words starting with a prefix. *)
  original_index : index;  (** Index before alias resolution. *)
}

external of_string : string -> (string * t) array = "cdict_of_string_ocaml"
(** Load dictionaries stored in a string. The main dictionary is called
    ["main"]. Use the library [cdict.builder] to construct the dictionaries.
    Raises [Failure] if the dictionary seems corrupted. *)

external find : t -> string -> result = "cdict_find_ocaml"
(** Lookup a word in the dictionary. *)

external freq : t -> index -> int = "cdict_freq_ocaml"
(** Query the frequency associated to a word. *)

external word : t -> index -> string = "cdict_word_ocaml"
(** Retrieve the word at the given index. *)

external suffixes : t -> result -> int -> index array = "cdict_suffixes_ocaml"
(** List words that starts with the query passed to {!find}. This can be called
    even if [result.found] is false. The returned array cannot contain more than
    [len] elements but might be smaller. *)

external distance : t -> string -> dist:int -> count:int -> index array
  = "cdict_distance_ocaml"
(** [distance dict word ~dist ~count] lists words that are a [dist] editions
    away from the [word] according to Levenshtein distance. Adding suffixes of
    any length is considered to be a single edition. Do not return words that
    have a distance less than [dist]. The [count] most frequent words are
    returned. The returned array cannot contain more than [count] elements but
    might be smaller. *)

external format_version : unit -> int = "cdict_format_version_ocaml"
(** Version of the dictionary's format. Dictionaries built for a different
    version are not compatible. *)
