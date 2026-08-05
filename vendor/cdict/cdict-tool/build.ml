type word = Aosp_parser.word = {
  w : string;
  w_freq : int;
  w_shortcuts : string list;
}

let word_of_string ?(shortcuts = []) w =
  (w, { w; w_freq = 1; w_shortcuts = shortcuts })

let words_of_string_list ?shortcuts l =
  List.rev_map (word_of_string ?shortcuts) l

let build ~dict_name ~subst words =
  Printf.printf "Built dictionary %S (%d words)\n%!" dict_name
    (List.length words);
  (* Add all the shortcuts to the dictionary. *)
  let words =
    let ( @: ) = List.rev_append in
    List.fold_left
      (fun acc (_, w) ->
        words_of_string_list ~shortcuts:[ w.w ] (Substitutions.apply subst w.w)
        @: words_of_string_list w.w_shortcuts
        @: acc)
      words words
    |> List.rev
    (* Ensure that the original words appear first in the list or they would get
       shadowed by shortcuts. *)
  in
  Cdict_builder.of_list ~name:dict_name
    ~freq:(fun w -> w.w_freq)
    ~alias:(fun w -> List.nth_opt w.w_shortcuts 0)
    words

let parse_aosp_combined ~fname ~dict_name ~subst =
  let open Aosp_parser in
  let wordlist = In_channel.with_open_text fname (parse ~fname) in
  let words = List.rev_map (fun w -> (w.w, w)) wordlist.words in
  build ~dict_name ~subst words

let parse_newline_separated ~fname ~dict_name ~subst =
  let wordlist = In_channel.(with_open_text fname input_lines) in
  let counts = Hashtbl.create (List.length wordlist) in
  List.iter
    (fun w ->
      let c = try Hashtbl.find counts w with Not_found -> 0 in
      Hashtbl.replace counts w (c + 1))
    wordlist;
  let words =
    Hashtbl.fold
      (fun w w_freq acc -> (w, { w; w_freq; w_shortcuts = [] }) :: acc)
      counts []
  in
  build ~dict_name ~subst words

let parse_file fname ~dict_name ~subst =
  Printf.printf "Parsing %S\n%!" fname;
  match Filename.extension fname with
  | ".combined" -> parse_aosp_combined ~fname ~dict_name ~subst
  | _ -> parse_newline_separated ~fname ~dict_name ~subst

let parse_files_into_cdict_builders ~subst inputs =
  if not (List.exists (fun (n, _) -> n = "main") inputs) then
    Format.eprintf "Warning: No dictionary named \"main\" specified@\n";
  List.map (fun (dict_name, path) -> parse_file path ~dict_name ~subst) inputs

let main subst_file output inputs =
  try
    let subst = Substitutions.of_file_opt subst_file in
    let ds = parse_files_into_cdict_builders ~subst inputs in
    Out_channel.with_open_bin output (fun out_chan ->
        Cdict_builder.output ds out_chan);
    Printf.printf "Done.\n%!"
  with Failure msg ->
    Printf.eprintf "Error: %s\n%!" msg;
    exit 1
