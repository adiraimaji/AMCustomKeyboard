open Astring
open Cmdliner

let arg_input_dictionaries =
  let t =
    let parser s =
      match String.cut ~sep:":" s with
      | Some (name, path) ->
          Arg.Conv.parser Arg.file path |> Result.map (fun path -> (name, path))
      | None -> Error "Missing dictionary name, use NAME:PATH."
    and pp fmt (name, path) = Format.fprintf fmt "%s:%s" name path in
    Arg.Conv.make ~docv:"NAME:PATH" ~parser ~pp ()
  in
  let doc = "Input files." in
  Arg.(non_empty & pos_all t [] & info ~doc [])

let cmd_build =
  let arg_output =
    let doc = "Output file." in
    Arg.(required & opt (some string) None & info ~doc [ "o" ])
  in
  let opt_substitutions =
    let doc =
      {|
Path to a file describing substitutions in JSON format. An associative array
with string values is expected.
For every words in the input word lists, a word alias is created with all the
substitutions described in this file applied. Alias words are not created if
the result of the substitution is already present in the word list or was
already created for an earlier word.

For example, with the following substitutions:

{ "D": "d", "a": "", "i": "", "o": "", "y": "", "n": "m", "t": "d" }

The alias "dcdmr" is created for the word "Dictionary".
This can be used to detect spelling mistake like missing diacritics, wrong case
or to implement a very simple soundex-like system.
|}
    in
    Arg.(
      value
      & opt (some file) None
      & info ~doc ~docv:"SUBST.json" [ "s"; "subst-file" ])
  in
  let term =
    Term.(
      const Build.main $ opt_substitutions $ arg_output $ arg_input_dictionaries)
  in
  let doc =
    "Compile dictionaries for libcdict from text files. Supported formats are:\n\n\
    \  - AOSP word lists, if the file extension is .combined.\n\
    \  - Plain text files with one word per line, for any other file \
     extension.\n\n\
     The leaves of contain the frequency of the word as specified in the AOSP \
     word list or counted in the plain text word list."
  in
  Cmd.(v (info "build" ~doc)) term

let cmd_query =
  let opt_quiet =
    let doc = "Suppress output." in
    Arg.(value & flag & info ~doc [ "q" ])
  in
  let opt_from_file =
    let doc = "Read the words to query from a file." in
    Arg.(value & opt (some file) None & info ~doc [ "from-file" ])
  in
  let opt_dict_name =
    let doc = "Name of the dictionary to use." in
    Arg.(value & opt string "main" & info ~doc [ "d" ])
  in
  let opt_substitutions =
    let doc =
      "Path to the same file passed to the 'build' command with the same \
       option."
    in
    Arg.(value & opt (some file) None & info ~doc [ "s"; "subst-file" ])
  in
  let arg_dict =
    let doc = "Dictionary to query." in
    Arg.(required & pos 0 (some file) None & info ~doc ~docv:"DICT" [])
  in
  let arg_queries =
    let doc = "Words to look for in the dictionary." in
    Arg.(value & pos_right 0 string [] & info ~doc ~docv:"WORDS" [])
  in
  let term =
    Term.(
      const Query.main $ opt_quiet $ opt_from_file $ opt_dict_name
      $ opt_substitutions $ arg_dict $ arg_queries)
  in
  let doc =
    "Query the specified words in the given dictionary. Exits with status \
     equal to the number of words not found in the dictionary."
  in
  Cmd.(v (info "query" ~doc)) term

let cmd_stats =
  let term = Term.(const Stats.main $ arg_input_dictionaries) in
  let doc = "Build a dictionary and print stats about its structure." in
  Cmd.(v (info "stats" ~doc)) term

let cmd_format_version =
  let run () = Printf.printf "%d\n%!" (Cdict.format_version ()) in
  let term = Term.(const run $ const ()) in
  let doc = "Print the version of the dictionary format." in
  Cmd.(v (info "format-version" ~doc)) term

let cmd =
  let doc = "Build dictionaries from libcdict." in
  let info = Cmd.info "cdict-tool" ~version:"%%VERSION%%" ~doc in
  Cmd.group info [ cmd_build; cmd_query; cmd_stats; cmd_format_version ]

let () = exit (Cmd.eval cmd)
