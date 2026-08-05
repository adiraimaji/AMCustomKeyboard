type 'a t = 'a array

(** Return the size of the biggest full level and the number of element in the
    next incomplete level (or 0 if the tree is perfect). *)
let width_of_last_full_level len =
  let rec loop s =
    let s' = (s * 2) + 1 in
    if s' > len then ((s / 2) + 1, len - s) else loop s'
  in
  loop 1

(* The pivot must carefuly be choosen to ensure that the tree is complete
   (without holes in the last level). *)
let pivot len =
  let width, last_level = width_of_last_full_level len in
  if last_level <= width then
    (* If [last_level <= width], there are [last_level] more elements in the
       left subtree than in the right subtree (which is perfect). If [last_level
       = 0], the tree is perfect and the pivot is [len / 2]. *)
    (len + last_level) / 2
  else
    (* If [last_level > width], The left subtree is perfect and the right
       subtree has [width - last_level] less elements than the left one. At this
       point, [len = width * 3 - 1 + last_level], so the formula [((len -
       last_level) / 2) + width] can be simplified. *)
    (width * 2) - 1

let of_sorted_array src =
  let len = Array.length src in
  if len = 0 then [||]
  else
    let dst = Array.make len src.(0) in
    let rec loop lo hi dsti =
      if lo > hi then ()
      else
        let mid = lo + pivot (hi - lo + 1) in
        dst.(dsti) <- src.(mid);
        loop lo (mid - 1) ((dsti * 2) + 1);
        loop (mid + 1) hi ((dsti * 2) + 2)
    in
    loop 0 (len - 1) 0;
    dst

let of_sorted_list src = of_sorted_array (Array.of_list src)
let to_array t = t
