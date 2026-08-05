  $ printf "%s\n" foo bar baz > second.txt

  $ cdict-tool build -o dict main:ocaml_manual.txt second:second.txt
  Parsing "ocaml_manual.txt"
  Built dictionary "main" (7984 words)
  Parsing "second.txt"
  Built dictionary "second" (3 words)
  Done.

  $ ls -sh dict
  60K dict

TODO: Some words are not found back due to some encoding issues.

  $ cdict-tool query -q dict --from-file ocaml_manual.txt

  $ cdict-tool query -d main dict foo
  found: "foo" freq=15 index=4223
  prefix: "foo" freq=15 index=4223
  prefix: "fooBar" freq=7 index=4224
  prefix: "footnote" freq=7 index=4225
  close match: "for" distance=1 freq=15 index=4226
  close match: "Foo" distance=1 freq=13 index=1040
  close match: "too" distance=1 freq=13 index=7409
  close match: "fooBar" distance=1 freq=7 index=4224
  close match: "footnote" distance=1 freq=7 index=4225
  close match: "For" distance=2 freq=15 index=1042
  close match: "To" distance=2 freq=15 index=2090
  close match: "To" distance=2 freq=15 index=2090
  close match: "cmo" distance=2 freq=15 index=3041
  close match: "do" distance=2 freq=15 index=3678
  $ cdict-tool query -d second dict foo
  found: "foo" freq=0 index=2
  prefix: "foo" freq=0 index=2
  close match: "foo" distance=2 freq=0 index=2
