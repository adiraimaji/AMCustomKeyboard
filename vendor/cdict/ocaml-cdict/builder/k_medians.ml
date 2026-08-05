(** [k_medians data k ~compare ~renumber] clusters the data into [k] clusters.
    The data is internally sorted using [compare] and it is then assigned a
    cluster with [renumber elt cluster], where [cluster] is a integer from 0 to
    [k] (excl). The array [data] is not modified. [renumber] is called once for
    each element in [data]. *)
let k_medians :
    'a array ->
    int ->
    compare:('a -> 'a -> int) ->
    renumber:('a -> int -> 'b) ->
    'b array =
 fun data k ~compare ~renumber ->
  let len = Array.length data in
  if len = 0 then [||]
  else
    let indexes = Array.init len Fun.id in
    Array.sort (fun i j -> compare data.(i) data.(j)) indexes;
    let first_val = data.(indexes.(0)) in
    let res = Array.make len (renumber first_val 0) in
    let cluster_size = max 1 (len / k) in
    let rec loop i prev_cluster prev_val =
      if i >= len then ()
      else
        let idx = indexes.(i) in
        let cluster = i / cluster_size and v = data.(idx) in
        let cluster =
          (* Put equal values in the same cluster. This results in clusters of
             different size. *)
          if compare prev_val v = 0 then prev_cluster else cluster
        in
        res.(idx) <- renumber v cluster;
        loop (i + 1) cluster v
    in
    loop 1 0 first_val;
    res
