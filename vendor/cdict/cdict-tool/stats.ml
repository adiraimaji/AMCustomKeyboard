let main inputs =
  let subst = Substitutions.empty in
  let ds = Build.parse_files_into_cdict_builders ~subst inputs in
  List.iter (Cdict_builder.stats Format.std_formatter) ds
