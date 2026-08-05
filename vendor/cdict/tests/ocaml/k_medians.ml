open Cdict_builder.K_medians

let km k d = k_medians d k ~compare:Int.compare ~renumber:(fun d i -> (d, i))
let () = Random.self_init ()
let rand = Random.int

let expect pp expected got =
  if expected <> got then (
    Format.eprintf
      "@[<v>Test failure. Expected:@;\
       <1 2>@[<v>%a@]@ but got:@;\
       <1 2>@[<v>%a@]@]@\n"
      pp expected pp got;
    assert false)

let pp_result =
  let pp ppf (v, i) = Format.fprintf ppf "v=%d, cluster=%d" v i in
  Format.(pp_print_array ~pp_sep:pp_print_space pp)

let expect' exp got = expect pp_result exp got

let () =
  let data =
    [|
      1;
      2;
      3;
      5;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      6;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      8;
      10;
      12;
      12;
      13;
      14;
      20;
      40;
      80;
      160;
    |]
  in
  let r0 = km 3 data in
  for _ = 0 to 10 do
    Array.shuffle ~rand data;
    let ri = km 3 data in
    Array.sort (fun (a, _) (b, _) -> a - b) ri;
    expect' r0 ri
  done

let () = expect' (km 1 [| 1 |]) [| (1, 0) |]
let () = expect' (km 1 [||]) [||]
let () = expect' (km 5 [| 1; 2; 3 |]) [| (1, 0); (2, 1); (3, 2) |]
