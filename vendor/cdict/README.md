# cdict

Dictionary implementation for spell checking and word suggestion.

This project contains several things:

- `libcdict` A portable and small C library for querying dictionaries.
  It supports a compact representation, fast lookups, word correction and
  search by a prefix.

- `ocaml-cdict` An OCaml library for constructing dictionaries and bindings for
  `libcdict`.

- `ocaml-dfa` An OCaml library for building a minimal acyclic automaton from a
  list of words and perfect hashing.

- `java` Java bindings for `libcdict`.

- `cdict-tool` An OCaml program for constructing dictionaries with support for
  AOSP word lists.
