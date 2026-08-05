open Cdict_builder.Complete_tree

let rec check_tree t i =
  let is_leaf i = Array.length t <= i in
  let l = (i * 2) + 1 and r = (i * 2) + 2 in
  if not (is_leaf l) then (
    assert (t.(i) > t.(l));
    check_tree t l);
  if not (is_leaf r) then (
    assert (t.(i) < t.(r));
    check_tree t r)

(** Build a complete tree from a sorted list and test whether all the elements
    are reachable. *)
let test_of_sorted_list lst =
  let t = to_array (of_sorted_list lst) in
  assert (List.sort compare (Array.to_list t) = lst);
  if Array.length t > 0 then check_tree t 0

let () =
  (* Test all the trees with up to 4 levels *)
  let rec loop lst =
    test_of_sorted_list lst;
    match lst with _ :: tl -> loop tl | [] -> ()
  in
  loop (List.init 31 (fun i -> Char.chr (Char.code '0' + i)))
