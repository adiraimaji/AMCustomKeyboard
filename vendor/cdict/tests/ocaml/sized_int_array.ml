module S = Cdict_builder.Sized_int_array

let expect pp a b =
  if a <> b then Format.kasprintf failwith "Expected %a but got %a" pp a pp b

let expect_int = expect Format.pp_print_int

(** Format detection *)

let create_detect_and_assert ?signed ?expect ar =
  let expect = Option.value expect ~default:ar in
  let t = S.mk_detect ?signed ar in
  Array.iteri (fun i v -> expect_int v (S.get t i)) expect;
  (match S.format t with
  | I4 | U4 -> expect_int ((Array.length ar + 1) land lnot 1) (S.length t)
  | _ -> expect_int (Array.length ar) (S.length t));
  t

let create_and_assert format ?expect ar =
  let expect = Option.value expect ~default:ar in
  let t = S.mk format ar in
  Array.iteri (fun i v -> expect_int v (S.get t i)) expect;
  t

let () =
  let t = create_detect_and_assert [| 0; 1; 2; 4; 5; 6 |] in
  assert (S.size_bytes t = 3);
  assert (fst t = U4)

let () =
  let t = create_detect_and_assert [| 0; 1; 2; 4; 5; -1; 6 |] in
  assert (S.size_bytes t = 4);
  assert (fst t = I4)

let () =
  let t = create_detect_and_assert [| 0; 1; 2; 4; 255 |] in
  assert (S.size_bytes t = 5);
  assert (fst t = U8)

let () =
  let t = create_detect_and_assert [| 0; 1; -1; 2; 4; 127 |] in
  assert (S.size_bytes t = 6);
  assert (fst t = I8)

let () =
  let t = create_detect_and_assert [| 0; 1; -1; 2; 4; -128 |] in
  assert (S.size_bytes t = 6);
  assert (fst t = I8)

let () =
  let t = create_detect_and_assert [| 0; 1; -1; 2; 4; 128 |] in
  assert (S.size_bytes t = 6 * 2);
  assert (fst t = I16)

let () =
  let t = create_detect_and_assert [| 0; 1; -1; 2; 4; -129 |] in
  assert (S.size_bytes t = 6 * 2);
  assert (fst t = I16)

let () =
  let t = create_detect_and_assert ~signed:true [| 0; 1; 2; 4; 128 |] in
  assert (S.size_bytes t = 5 * 2);
  assert (fst t = I16)

let () =
  let t =
    create_detect_and_assert ~signed:false ~expect:[| 0; 1; 2; 4; 128 |]
      [| 0; 1; 2; 4; -128 |]
  in
  assert (fst t = U8)

(** 24-bits integers *)

let () =
  let t =
    create_and_assert S.U24
      ~expect:[| 1; 2; 0xFF; 0xFFFF; 0xFFFFFF; 0xFFFFFF - 2 |]
      [| 1; 2; 0xFF; 0xFFFF; 0xFFFFFF; -3 |]
  in
  assert (S.size_bytes t = 6 * 3);
  ()

let () =
  let t =
    create_and_assert S.I24
      ~expect:[| 1; 2; 0xFF; 0xFFFF; -1; -3 |]
      [| 1; 2; 0xFF; 0xFFFF; 0xFFFFFF; -3 |]
  in
  assert (S.size_bytes t = 6 * 3);
  ()
