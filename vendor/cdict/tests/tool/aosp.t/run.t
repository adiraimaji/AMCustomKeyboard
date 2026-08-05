  $ cdict-tool build -o dict main:words.combined
  Parsing "words.combined"
  Built dictionary "main" (7984 words)
  Done.

  $ ls -sh dict
  60K dict

  $ cdict-tool query dict type module function value match
  found: "type" freq=15 index=7526
  prefix: "type" freq=15 index=7526
  prefix: "types" freq=15 index=7537
  prefix: "typexpr" freq=15 index=7540
  prefix: "typeconstr" freq=14 index=7530
  prefix: "typed" freq=11 index=7531
  close match: "types" distance=1 freq=15 index=7537
  close match: "typexpr" distance=1 freq=15 index=7540
  close match: "Type" distance=1 freq=14 index=2115
  close match: "typeconstr" distance=1 freq=14 index=7530
  close match: "typed" distance=1 freq=11 index=7531
  close match: "byte" distance=2 freq=15 index=2831
  close match: "the" distance=2 freq=15 index=7347
  close match: "the" distance=2 freq=15 index=7347
  close match: "time" distance=2 freq=15 index=7387
  close match: "true" distance=2 freq=15 index=7493
  found: "module" freq=15 index=5399
  prefix: "module" freq=15 index=5399
  prefix: "modules" freq=15 index=5404
  prefix: "moduleexamples" freq=10 index=5401
  prefix: "modulealias" freq=5 index=5400
  prefix: "modulename" freq=5 index=5402
  close match: "modules" distance=1 freq=15 index=5404
  close match: "Module" distance=1 freq=14 index=1466
  close match: "modulo" distance=1 freq=13 index=5406
  close match: "mdule" distance=1 freq=11 index=5286
  close match: "moduleexamples" distance=1 freq=10 index=5401
  close match: "mode" distance=2 freq=15 index=5384
  close match: "module" distance=2 freq=15 index=5399
  close match: "module" distance=2 freq=15 index=5399
  close match: "module" distance=2 freq=15 index=5399
  close match: "module" distance=2 freq=15 index=5399
  found: "function" freq=15 index=4302
  prefix: "function" freq=15 index=4302
  prefix: "functions" freq=15 index=4306
  prefix: "functional" freq=14 index=4303
  prefix: "functionals" freq=7 index=4305
  prefix: "functionality" freq=5 index=4304
  close match: "functions" distance=1 freq=15 index=4306
  close match: "functional" distance=1 freq=14 index=4303
  close match: "Function" distance=1 freq=10 index=1063
  close match: "functionals" distance=1 freq=7 index=4305
  close match: "functionality" distance=1 freq=5 index=4304
  close match: "function" distance=2 freq=15 index=4302
  close match: "function" distance=2 freq=15 index=4302
  close match: "function" distance=2 freq=15 index=4302
  close match: "function" distance=2 freq=15 index=4302
  close match: "function" distance=2 freq=15 index=4302
  found: "value" freq=15 index=7722
  prefix: "value" freq=15 index=7722
  prefix: "values" freq=15 index=7724
  prefix: "valuerestriction" freq=0 index=7723
  close match: "values" distance=1 freq=15 index=7724
  close match: "Value" distance=1 freq=8 index=2185
  close match: "valuerestriction" distance=1 freq=0 index=7723
  close match: "false" distance=2 freq=15 index=4110
  close match: "value" distance=2 freq=15 index=7722
  close match: "value" distance=2 freq=15 index=7722
  close match: "value" distance=2 freq=15 index=7722
  close match: "value" distance=2 freq=15 index=7722
  found: "match" freq=15 index=5267
  prefix: "match" freq=15 index=5267
  prefix: "matching" freq=15 index=5270
  prefix: "matched" freq=14 index=5268
  prefix: "matches" freq=14 index=5269
  prefix: "matchings" freq=11 index=5271
  close match: "matching" distance=1 freq=15 index=5270
  close match: "Match" distance=1 freq=14 index=1428
  close match: "matched" distance=1 freq=14 index=5268
  close match: "matches" distance=1 freq=14 index=5269
  close match: "batch" distance=1 freq=11 index=2679
  close match: "each" distance=2 freq=15 index=3752
  close match: "machine" distance=2 freq=15 index=5198
  close match: "match" distance=2 freq=15 index=5267
  close match: "match" distance=2 freq=15 index=5267
  close match: "match" distance=2 freq=15 index=5267

  $ cdict-tool query dict overload enum defensive coding
  not found: "overload"
  prefix: "overloading" freq=0 index=5830
  close match: "overloading" distance=1 freq=0 index=5830
  close match: "overhead" distance=2 freq=9 index=5826
  close match: "overlap" distance=2 freq=7 index=5827
  close match: "overlay" distance=2 freq=0 index=5829
  close match: "overlook" distance=2 freq=0 index=5831
  not found: "enum"
  prefix: "enumerate" freq=13 index=3885
  prefix: "enumerated" freq=10 index=3886
  prefix: "enumeration" freq=0 index=3887
  close match: "num" distance=1 freq=14 index=5623
  close match: "enumerate" distance=1 freq=13 index=3885
  close match: "vnum" distance=1 freq=12 index=7774
  close match: "enumerated" distance=1 freq=10 index=3886
  close match: "enumeration" distance=1 freq=0 index=3887
  close match: "em" distance=2 freq=15 index=3800
  close match: "end" distance=2 freq=15 index=3844
  close match: "number" distance=2 freq=15 index=5626
  close match: "num" distance=2 freq=14 index=5623
  close match: "numbers" distance=2 freq=14 index=5629
  not found: "defensive"
  not found: "coding"
  close match: "coming" distance=1 freq=5 index=3093
  close match: "Coding" distance=1 freq=0 index=712
  close match: "adding" distance=2 freq=13 index=2346
  close match: "encoding" distance=2 freq=13 index=3838
  close match: "ending" distance=2 freq=13 index=3849
  close match: "copying" distance=2 freq=12 index=3312
  close match: "copying" distance=2 freq=12 index=3312
  [4]

Prefix search:

  $ cdict-tool query dict typ
  found: "typ" freq=15 index=7524
  prefix: "typ" freq=15 index=7524
  prefix: "type" freq=15 index=7526
  prefix: "types" freq=15 index=7537
  prefix: "typexpr" freq=15 index=7540
  prefix: "typeconstr" freq=14 index=7530
  close match: "top" distance=1 freq=15 index=7416
  close match: "type" distance=1 freq=15 index=7526
  close match: "types" distance=1 freq=15 index=7537
  close match: "typexpr" distance=1 freq=15 index=7540
  close match: "typeconstr" distance=1 freq=14 index=7530
  close match: "map" distance=2 freq=15 index=5250
  close match: "p" distance=2 freq=15 index=5847
  close match: "t1" distance=2 freq=15 index=7253
  close match: "tag" distance=2 freq=15 index=7272
  close match: "tex" distance=2 freq=15 index=7331

Stats:

  $ cdict-tool stats main:words.combined
  Parsing "words.combined"
  Built dictionary "main" (7984 words)
  Nodes: 4785
  Branch nodes: 2262
    With size:
    |  0: 1    |  2: 1287 |  3: 446  |  4: 209  |  5: 80   |  6: 49  
    |  7: 51   |  8: 24   |  9: 20   | 10: 19   | 11: 13   | 12: 13  
    | 13: 7    | 14: 6    | 15: 5    | 16: 7    | 17: 2    | 18: 3   
    | 19: 4    | 20: 1    | 21: 4    | 22: 3    | 23: 1    | 24: 3   
    | 25: 1    | 26: 1    | 27: 1    | 68: 1    |
    Transitions: 7597
      | Final: 2081 | Non-final: 5516 |
    With numbers format: | U16: 13   | U4: 2044 | U8: 205  |
  Prefix nodes: 2523
    Followed by:
    | Branches: 2116 | Prefix: 407  |
    With size:
    |  1: 550  |  2: 523  |  3: 505  |  4: 358  |  5: 246  |  6: 145 
    |  7: 88   |  8: 54   |  9: 17   | 10: 22   | 11: 5    | 12: 1   
    | 13: 4    | 15: 1    | 17: 1    | 19: 1    | 27: 1    | 61: 1    |
    Transitions: 2523
      | Final: 2061 | Non-final: 462  |
  Freq: 7984
    With value:
    |  0: 2611 |  5: 1156 |  7: 666  |  8: 453  |  9: 342  | 10: 281 
    | 11: 531  | 12: 490  | 13: 468  | 14: 492  | 15: 494  |
  Aliases: 0
