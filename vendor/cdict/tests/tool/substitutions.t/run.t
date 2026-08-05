  $ cdict-tool build -o dict --subst-file subst.json main:wordlist
  Parsing "wordlist"
  Built dictionary "main" (2 words)
  Done.

  $ cdict-tool query dict --from-file wordlist
  found: "dictionary" freq=0 index=2
  prefix: "dictionary" freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  found: "Unexpected" freq=0 index=0
  prefix: "Unexpected" freq=0 index=0
  close match: "Unexpected" distance=2 freq=0 index=0

  $ cdict-tool query dict Dictionary
  not found: "Dictionary"
  close match: "dictionary" distance=1 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  [1]

  $ cdict-tool query dict --subst-file subst.json Dictionary dctionary unexpected
  not found: "Dictionary"
  close match: "dictionary" distance=1 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  found: "dictionary" freq=0 index=2
  prefix: "dictionary" freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  not found: "dctionary"
  close match: "dictionary" distance=1 freq=0 index=2
  found: "dictionary" freq=0 index=2
  prefix: "dictionary" freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  close match: "dictionary" distance=2 freq=0 index=2
  not found: "unexpected"
  close match: "Unexpected" distance=1 freq=0 index=0
  close match: "Unexpected" distance=2 freq=0 index=0
  found: "Unexpected" freq=0 index=0
  prefix: "Unexpected" freq=0 index=0
  close match: "Unexpected" distance=2 freq=0 index=0
  [3]
