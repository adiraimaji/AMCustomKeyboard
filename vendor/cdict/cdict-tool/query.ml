let open_dict dict_name fname =
  let data = In_channel.(with_open_bin fname input_all) in
  match Array.find_opt (fun (n, _) -> n = dict_name) (Cdict.of_string data) with
  | Some (_, d) -> d
  | None -> assert false

let queries_from_file = function
  | Some fname -> In_channel.(with_open_text fname input_lines)
  | None -> []

let query_distance dict ~dist q =
  Cdict.distance dict q ~dist ~count:5
  |> Array.iter (fun idx ->
         Printf.printf "close match: %S distance=%d freq=%d index=%d\n"
           (Cdict.word dict idx) dist (Cdict.freq dict idx)
           (idx :> int))

let print_result_and_suggestions dict q r =
  if r.Cdict.found then
    let w = Cdict.word dict r.index in
    let freq = Cdict.freq dict r.index in
    Printf.printf "found: %S freq=%d index=%d\n" w freq (r.index :> int)
  else Printf.printf "not found: %S\n" q;
  let prefixes_idx = Cdict.suffixes dict r 5 in
  Array.iter
    (fun idx ->
      Printf.printf "prefix: %S freq=%d index=%d\n" (Cdict.word dict idx)
        (Cdict.freq dict idx)
        (idx :> int))
    prefixes_idx;
  query_distance dict ~dist:1 q;
  query_distance dict ~dist:2 q

let query ~quiet ~subst dict q =
  let r = Cdict.find dict q in
  if not quiet then (
    print_result_and_suggestions dict q r;
    Substitutions.apply subst q
    |> List.iter (fun q' ->
           print_result_and_suggestions dict q' (Cdict.find dict q')));
  if r.found then 0 else 1

let main quiet from_file dict_name subst dict_fname queries =
  let dict = open_dict dict_name dict_fname in
  let queries = queries @ queries_from_file from_file in
  let subst = Substitutions.of_file_opt subst in
  exit (List.fold_left (fun n q -> query ~quiet ~subst dict q + n) 0 queries)
