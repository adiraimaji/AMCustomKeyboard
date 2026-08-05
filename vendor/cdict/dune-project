(lang dune 3.13)

(name cdict)

(using menhir 3.0)

(using directory-targets 0.1)

(generate_opam_files true)

(package
 (name cdict)
 (synopsis "Compact dictionary partially written in C for spell checking.")
 (depends dfa))

(package
 (name cdict-tool)
 (synopsis "Compiles dictionaries for the cdict library.")
 (depends cmdliner menhir cdict astring bytesrw jsont))

(package
 (name dfa)
 (synopsis "Construct a minimal DFA that recognizes a list of words.")
 (description
  "Implements a minimal deterministic finite-state acyclic automaton that recognizes a finite set of words and perfect hashing. It works with a 8 bits alphabet and treat words as byte strings. It is ideal for natural language dictionaries that are constructed once and compiled into a compact representation."))

(package
 (name cdict-java)
 (synopsis "Internal package.")
 (allow_empty)
 (depends cdict))
