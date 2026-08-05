open Dfa

let words_of_dfa dfa =
  let rec loop acc index prefix st =
    List.fold_left
      (fun acc { c; next; number; final } ->
        let prefix = prefix ^ String.make 1 c in
        let index = index + number in
        let acc = if final then (prefix, index) :: acc else acc in
        let index = if final then index + 1 else index in
        loop acc index prefix (state dfa next))
      acc st
  in
  List.rev (loop [] 0 "" (root_state dfa))

let expect ?(msg = "") pp_a got expected =
  if got <> expected then (
    Format.eprintf "%sExpected:@\n{@\n%a@\n}@\nbut got:@\n{@\n%a@\n}@\n%!" msg
      pp_a expected pp_a got;
    failwith "Test failure")

let pplist pp_a = Format.(pp_print_list ~pp_sep:pp_print_space) pp_a

let test ?expected_words input_words expected_printed =
  let input_words = List.sort String.compare input_words in
  let dfa = of_sorted_list input_words in
  expect Format.pp_print_string
    (String.trim (Format.asprintf "%a" pp dfa))
    (String.trim expected_printed);
  let expected_words =
    match expected_words with
    | Some ws -> ws
    | None -> List.mapi (fun i w -> (w, i)) input_words
  in
  let dfa_words = words_of_dfa dfa in
  expect ~msg:"Missing words. "
    (pplist Format.pp_print_string)
    (List.map fst dfa_words)
    (List.map fst expected_words);
  expect ~msg:"Wrong perfect hash. "
    (pplist (fun ppf (w, i) -> Format.fprintf ppf "%S (%d)" w i))
    dfa_words expected_words

let () =
  test
    [ "pomme"; "pommes"; "poire"; "poires"; "coing" ]
    {|
.0   'c' (n=0)
         5   'o' (n=0)
                 4   'i' (n=0)
                         3   'n' (n=0)
                                 2   'g' (n=0) (final)
                                         1   
     'p' (n=1)
         10  'o' (n=0)
                 9   'i' (n=0)
                         8   'r' (n=0)
                                 7   'e' (n=0) (final)
                                         6   's' (n=0) (final)
                                                 (1 seen)
                     'm' (n=2)
                         14  'm' (n=0)
                                 (7 seen)
|}

(* The data used in the paper *)
let () =
  test
    [
      "aient";
      "ais";
      "ait";
      "ant";
      "assent";
      "asses";
      "assi";
      "ent";
      "eraient";
      "erais";
      "erait";
      "eras";
      "erez";
      "eriez";
      "erions";
      "erons";
      "eront";
      "es";
      "ez";
      "iez";
      "ions";
      "ons";
      "âmes";
      "âtes";
      "èrent";
      "ées";
      "és";
    ]
    {|
.0   'a' (n=0)
         20  'i' (n=0)
                 19  'e' (n=0)
                         18  'n' (n=0)
                                 17  't' (n=0) (final)
                                         16  
                     's' (n=1) (final)
                         (16 seen)
                     't' (n=2) (final)
                         (16 seen)
             'n' (n=3)
                 (17 seen)
             's' (n=4)
                 29  's' (n=0)
                         28  'e' (n=0)
                                 27  'n' (n=0)
                                         (17 seen)
                                     's' (n=1) (final)
                                         (16 seen)
                             'i' (n=2) (final)
                                 (16 seen)
     'e' (n=7)
         34  'n' (n=0)
                 (17 seen)
             'r' (n=1)
                 40  'a' (n=0)
                         39  'i' (n=0)
                                 (19 seen)
                             's' (n=3) (final)
                                 (16 seen)
                     'e' (n=4)
                         45  'z' (n=0) (final)
                                 (16 seen)
                     'i' (n=5)
                         48  'e' (n=0)
                                 (45 seen)
                             'o' (n=1)
                                 51  'n' (n=0)
                                         50  's' (n=0) (final)
                                                 (16 seen)
                     'o' (n=7)
                         54  'n' (n=0)
                                 53  's' (n=0) (final)
                                         (16 seen)
                                     't' (n=1) (final)
                                         (16 seen)
             's' (n=10) (final)
                 (16 seen)
             'z' (n=11) (final)
                 (16 seen)
     'i' (n=19)
         (48 seen)
     'o' (n=21)
         (51 seen)
     '\195' (n=22)
            71  '\162' (n=0)
                       70  'm' (n=0)
                               69  'e' (n=0)
                                       (50 seen)
                           't' (n=1)
                               (69 seen)
                '\168' (n=2)
                       79  'r' (n=0)
                               78  'e' (n=0)
                                       (18 seen)
                '\169' (n=3)
                       82  'e' (n=0)
                               (50 seen)
                           's' (n=1) (final)
                               (16 seen)
|}

(** Duplicate leaves. *)
let () =
  test
    ~expected_words:[ ("être", 0) ]
    [ "être"; "être" ]
    {|
.0   '\195' (n=0)
            88  '\170' (n=0)
                       87  't' (n=0)
                               86  'r' (n=0)
                                       85  'e' (n=0) (final)
                                               84
|}
