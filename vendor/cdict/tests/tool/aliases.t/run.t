  $ cdict-tool build -o dict main:words.combined
  Parsing "words.combined"
  Built dictionary "main" (8 words)
  Done.
  $ ls -sh dict
  4.0K dict

  $ cdict-tool query dict sourire heureux smiley joie haha 😀
  found: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\133" freq=0 index=13
  close match: "\240\159\152\133" distance=1 freq=0 index=13
  close match: "\240\159\144\173" distance=2 freq=0 index=8
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  found: "\240\159\152\132" freq=0 index=12
  prefix: "\240\159\152\132" freq=0 index=12
  close match: "\240\159\152\132" distance=2 freq=0 index=12
  close match: "\240\159\152\132" distance=2 freq=0 index=12
  found: "\240\159\152\131" freq=0 index=11
  prefix: "\240\159\152\131" freq=0 index=11
  close match: "\240\159\152\131" distance=2 freq=0 index=11
  close match: "\240\159\152\131" distance=2 freq=0 index=11
  found: "\240\159\152\132" freq=0 index=12
  prefix: "\240\159\152\132" freq=0 index=12
  close match: "\240\159\152\132" distance=2 freq=0 index=12
  found: "\240\159\152\134" freq=0 index=14
  prefix: "\240\159\152\134" freq=0 index=14
  close match: "\240\159\152\134" distance=2 freq=0 index=14
  close match: "\240\159\152\134" distance=2 freq=0 index=14
  found: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\128" freq=0 index=9
  close match: "\240\159\152\130" distance=1 freq=0 index=10
  close match: "\240\159\152\131" distance=1 freq=0 index=11
  close match: "\240\159\152\132" distance=1 freq=0 index=12
  close match: "\240\159\152\133" distance=1 freq=0 index=13
  close match: "\240\159\152\134" distance=1 freq=0 index=14
  close match: "\240\159\144\129" distance=2 freq=0 index=7
  close match: "\240\159\144\173" distance=2 freq=0 index=8
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9

"sourire" is found (alias -> 😀) and has a suffix "sourire_en_sueur" (alias -> 😅).
suffixes() must use the original trie index, not the alias-resolved index.

  $ cdict-tool query dict sourire
  found: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\133" freq=0 index=13
  close match: "\240\159\152\133" distance=1 freq=0 index=13
  close match: "\240\159\144\173" distance=2 freq=0 index=8
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\128" distance=2 freq=0 index=9

  $ cdict-tool query dict sour heuxeux
  not found: "sour"
  prefix: "\240\159\144\173" freq=0 index=8
  prefix: "\240\159\152\128" freq=0 index=9
  prefix: "\240\159\152\133" freq=0 index=13
  close match: "\240\159\144\173" distance=1 freq=0 index=8
  close match: "\240\159\152\128" distance=1 freq=0 index=9
  close match: "\240\159\152\133" distance=1 freq=0 index=13
  close match: "\240\159\144\173" distance=2 freq=0 index=8
  close match: "\240\159\152\128" distance=2 freq=0 index=9
  close match: "\240\159\152\133" distance=2 freq=0 index=13
  not found: "heuxeux"
  close match: "\240\159\152\132" distance=1 freq=0 index=12
  [2]

The combination of substitutions and shortcuts creates alias chains:
(eg. sorire -> sourire -> 😀)

  $ cdict-tool build -s subst.json -o subst.dict main:words.combined
  Parsing "words.combined"
  Built dictionary "main" (8 words)
  Done.

  $ cdict-tool query subst.dict so
  not found: "so"
  prefix: "\240\159\144\173" freq=0 index=12
  prefix: "\240\159\144\173" freq=0 index=12
  prefix: "\240\159\152\128" freq=0 index=13
  prefix: "\240\159\152\128" freq=0 index=13
  prefix: "\240\159\152\133" freq=0 index=17
  close match: "\240\159\144\173" distance=1 freq=0 index=12
  close match: "\240\159\144\173" distance=1 freq=0 index=12
  close match: "\240\159\152\128" distance=1 freq=0 index=13
  close match: "\240\159\152\128" distance=1 freq=0 index=13
  close match: "\240\159\152\133" distance=1 freq=0 index=17
  close match: "\240\159\144\173" distance=2 freq=0 index=12
  close match: "\240\159\144\173" distance=2 freq=0 index=12
  close match: "\240\159\152\128" distance=2 freq=0 index=13
  close match: "\240\159\152\128" distance=2 freq=0 index=13
  close match: "\240\159\152\131" distance=2 freq=0 index=15
  [1]
