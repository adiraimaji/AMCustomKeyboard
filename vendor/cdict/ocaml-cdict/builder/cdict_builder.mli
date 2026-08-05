(** This module allows writing dictionaries for the cdict C library. *)

type 'a t
(** A collection of words. Words can be arbitrary strings with arbitrary
    metadata attached. This stores the entire dictionary in memory, in a
    space-inefficient way. *)

val of_list :
  name:string ->
  freq:('a -> int) ->
  ?alias:('a -> string option) ->
  (string * 'a) list ->
  'a t
(** Construct a dictionary from a list.

    If [alias w1] returns [Some w2], [w1] is not a proper word in the dictionary
    but instead an alias to [w2]. The alias dictionary is an extra dictionary,
    named [name ^ ".alias"], that specifies whether a word is an alias to an
    other word. It is not created if [alias] returns [None] for every words in
    the list (the default). *)

val output : 'a t list -> Out_channel.t -> unit
(** Write a dictionary file containing a list of dictionaries into the given
    channel. *)

val to_string : 'a t list -> string
(** Like [output] but write into a string in memory. *)

val stats : Format.formatter -> 'a t -> unit
(** Print various stats for debugging and testing purposes. *)

val pp : Format.formatter -> 'a t -> unit

(**/**)

(** Exposed for testing purposes *)

module Complete_tree : sig
  type 'a t

  val of_sorted_list : 'a list -> 'a t
  val to_array : 'a t -> 'a array
end

module K_medians : sig
  val k_medians :
    'a array ->
    int ->
    compare:('a -> 'a -> int) ->
    renumber:('a -> int -> 'b) ->
    'b array
end

module Sized_int_array : module type of Sized_int_array
