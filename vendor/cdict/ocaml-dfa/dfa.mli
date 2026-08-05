(** Implements a minimal deterministic finite-state acyclic automaton that
    recognizes a finite set of words and implements perfect hashing. It works
    with a 8 bits alphabet and treat words as byte strings.

    The construction step runs in O(n^2) but remains a costly operation. The DFA
    cannot be modified once it's constructed. It is ideal for natural language
    dictionaries that are constructed once and compiled into a compact
    representation.

    Perfect hashing is implemented using the {!field-number} field in each
    transitions.

    The construction algorithm is from the paper:
    {v
    Incremental Construction of Minimal Acyclic Finite-State Automata
    by Jan Daciuk, Stoyan Mihov, Bruce Watson, Richard Watson
    https://arxiv.org/pdf/cs/0007009
    v}

    The perfect-hasing scheme is from the paper:
    {v
    Applications of Finite Automata Representing Large Vocabularies
    by Cláudio L. Lucchesi and Tomasz Kowaltowski
    https://www.cs.mun.ca/~harold/Courses/Old/CS4750.F14/Diary/1992-001.pdf
    v} *)

type id

type transition = {
  c : char;
  next : id;
  number : int;
      (** The number of words recognized by the state pointed to by [next].
          During a successful lookup of a word in the DFA, the sum of the
          [number] fields of all the transitions that were traversed is the
          perfect hash of that word. A number uniquely assigned to each words,
          from 1 to the number of words present in the dictionary (incl). *)
  final : bool;
}

type state = transition list
(** Transitions sorted in lexicographic order. *)

type t

val state : t -> id -> state
(** Access a state. From its ID. IDs are found in a state [tr] field. *)

val root_state : t -> state
(** Access the root state. *)

val of_sorted_list : string list -> t
(** Construct a minimal DFA from a lexicographically sorted list. If the list is
    not sorted, the DFA will not be minimal. Duplicated words are removed. *)

val of_sorted_iter : ((string -> unit) -> unit) -> t
(** Like [of_sorted_list] but takes a partially applied [iter] function instead
    of a list. *)

val pp : Format.formatter -> t -> unit
(** Pretty print the internal structure of the DFA. For debugging purposes. *)

(** Allowed operations on values of type [id]. *)
module Id : sig
  type t = id

  val compare : t -> t -> int

  module Map : Map.S with type key = t
end
