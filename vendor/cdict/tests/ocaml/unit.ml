[@@@warning "-52"] (* Warning on string patterns matching [Failure] argument. *)

let fail fmt = Format.kasprintf failwith fmt
let fpf = Format.fprintf

let expect ?(msg = "") pp_a got expected =
  if got <> expected then (
    Format.eprintf
      "%sExpected: {@\n@[<v>%a@]@\n} but got: {@\n@[<v>%a@]@\n}@\n%!" msg pp_a
      expected pp_a got;
    failwith "Test failure")

let create' words =
  let w = Cdict_builder.of_list ~name:"main" ~freq:fst ~alias:snd words in
  let data = Cdict_builder.to_string [ w ] in
  (* Cdict_builder.stats Format.err_formatter w; *)
  (* Hxd_string.pp Hxd.default Format.err_formatter data; *)
  (* Format.pp_print_flush Format.err_formatter (); *)
  let cdict_header = Cdict.of_string data in
  expect Format.pp_print_int (Array.length cdict_header) 1;
  expect Format.pp_print_string (fst cdict_header.(0)) "main";
  snd cdict_header.(0)

let create words = create' (List.mapi (fun i w -> (w, (i, None))) words)

let pp_leaf_opt ppf = function
  | Some leaf -> fpf ppf "%d" leaf
  | None -> fpf ppf "<not found>"

let pp_word_leaf_opt ppf (w, l) = fpf ppf "%s %a" w pp_leaf_opt l
let pp_list fmt = Format.(pp_print_list ~pp_sep:pp_print_space) fmt

let find_no_assert d word =
  let r = Cdict.find d word in
  if r.found then Some (Cdict.freq d r.index) else None

let find_assert d word =
  let r = Cdict.find d word in
  if r.found then (
    expect ~msg:"Retrieve word" Format.pp_print_string (Cdict.word d r.index)
      word;
    Some (Cdict.freq d r.index))
  else None

let assert_found d word expected_leaf =
  expect ~msg:"Find returned unexpected value. " pp_leaf_opt
    (find_assert d word) (Some expected_leaf)

let create_and_assert' words =
  let d = create' (List.map fst words) in
  expect ~msg:"create_and_assert. " (pp_list pp_word_leaf_opt)
    (List.map (fun ((w, _), _) -> (w, find_no_assert d w)) words)
    (List.map (fun ((w, _), r) -> (w, Some r)) words);
  d

let create_and_assert words =
  create_and_assert' (List.mapi (fun i w -> ((w, (i, None)), i)) words)

let create_and_assert_with_aliases words =
  create_and_assert'
    (List.mapi (fun i (w, alias, r) -> ((w, (i, alias)), r)) words)

let assert_not_found d word =
  match find_assert d word with
  | Some leaf -> fail "Expected not found but got %d for word %S" leaf word
  | None -> ()

let () = assert (Cdict.format_version () = 1)

(* Fruit test *)
let () =
  let _ = create_and_assert [ "pomme"; "poire"; "coing"; "poireau" ] in
  ()

(* One empty word *)
let () =
  (* The DFA based dictionary doesn't support the empty word. *)
  let d = create [ "" ] in
  assert_not_found d "";
  assert_not_found d "a"

(* One word *)
let () =
  let d = create [ "a" ] in
  assert_found d "a" 0;
  assert_not_found d "";
  assert_not_found d "b"

(* Empty dict *)
let () =
  let d = create [] in
  assert_not_found d "";
  assert_not_found d "a"

(* Btree node *)
let () =
  let _ = create_and_assert [ "y"; "z"; "0"; "1"; "2" ] in
  let rec loop ws =
    let d = create_and_assert ws in
    assert_not_found d "d";
    match ws with [] -> () | _ :: tl -> loop tl
  in
  loop [ "a"; "b"; "c"; "x"; "y"; "z"; "0"; "1"; "2" ]

(* Magic number check. *)
let () =
  match Cdict.of_string "foo" with
  | exception Failure "Not a dictionary" -> ()
  | _ -> assert false

(* Version check. *)
let () =
  match Cdict.of_string "Dic\xFF" with
  | exception Failure "Unsupported format" -> ()
  | _ -> assert false

(* Aliases *)
let () =
  let _ =
    create_and_assert_with_aliases
      [
        ("abc", Some "Abc", 1);
        ("Abc", None, 1);
        ("Def", None, 2);
        ("def", Some "Def", 2);
        ("ghi", None, 4);
      ]
  in
  ()
