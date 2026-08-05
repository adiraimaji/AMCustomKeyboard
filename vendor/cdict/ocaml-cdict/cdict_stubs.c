#include <caml/alloc.h>
#include <caml/custom.h>
#include <caml/fail.h>
#include <caml/memory.h>
#include <caml/mlvalues.h>
#include <libcdict.h>
#include <string.h>

/** Get a [cdict_t const*] from a type [t].
    {[ type t = nativeint * header ]} */
#define CDICT_VAL(v) ((cdict_t const*)Data_custom_val(Field(v, 0)))

static struct custom_operations const cdict_t_ops = {
  "Cdict.t",
  custom_finalize_default,
  custom_compare_default,
  custom_hash_default,
  custom_serialize_default,
  custom_deserialize_default,
  custom_compare_ext_default,
  custom_fixed_length_default
};

// C to OCaml
static value alloc_result(cdict_result_t const *r)
{
  CAMLparam0();
  CAMLlocal1(v);
  v = caml_alloc_tuple(4);
  Store_field(v, 0, Val_bool(r->found));
  Store_field(v, 1, Val_int(r->index));
  Store_field(v, 2, caml_copy_nativeint(r->prefix_ptr));
  Store_field(v, 3, Val_int(r->original_index));
  CAMLreturn(v);
}

// OCaml to C
static void result_of_value(value v, cdict_result_t *dst)
{
  dst->found = Bool_val(Field(v, 0));
  dst->index = Int_val(Field(v, 1));
  dst->prefix_ptr = Nativeint_val(Field(v, 2));
  dst->original_index = Int_val(Field(v, 3));
}

value cdict_of_string_ocaml(value str)
{
  CAMLparam1(str);
  CAMLlocal5(vdicts, vdict, vdict_custom, elt, vheader);
  // We must copy the string to ensure that it doesn't move during GC.
  // The string is copied into the same custom block, after the cdict_t struct.
  int s_len = caml_string_length(str);
  vheader = caml_alloc_custom_mem(&cdict_t_ops, sizeof(cdict_header_t) + s_len, 0);
  cdict_header_t *header = Data_custom_val(vheader);
  void *data = ((void*)header) + sizeof(cdict_header_t);
  memcpy(data, String_val(str), s_len);
  cdict_cnstr_result_t r = cdict_of_string(data, s_len, header);
  if (r != CDICT_OK)
    caml_failwith(cdict_cnstr_result_to_string(r));
  int n_dicts = header->n_dicts;
  vdicts = caml_alloc_tuple(n_dicts);
  for (int i = 0; i < n_dicts; i++)
  {
    vdict_custom = caml_alloc_custom_mem(&cdict_t_ops, sizeof(cdict_t), 0);
    cdict_t *dict = Data_custom_val(vdict_custom);
    cdict_get_dict(header, i, dict);
    vdict = caml_alloc_tuple(2);
    Store_field(vdict, 0, vdict_custom);
    Store_field(vdict, 1, vheader);
    elt = caml_alloc_tuple(2);
    Store_field(elt, 0, caml_copy_string(dict->name));
    Store_field(elt, 1, vdict);
    Store_field(vdicts, i, elt);
  }
  CAMLreturn(vdicts);
}

value cdict_format_version_ocaml(value _unit)
{
  (void)_unit;
  return Val_int(cdict_format_version());
}

value cdict_find_ocaml(value dict, value str)
{
  CAMLparam2(dict, str);
  cdict_result_t result;
  int s_len = caml_string_length(str);
  cdict_find(CDICT_VAL(dict), String_val(str), s_len, &result);
  CAMLreturn(alloc_result(&result));
}

value cdict_freq_ocaml(value dict, value index)
{
  CAMLparam2(dict, index);
  CAMLreturn(Val_int(cdict_freq(CDICT_VAL(dict), Int_val(index))));
}

value cdict_word_ocaml(value dict, value index)
{
  CAMLparam2(dict, index);
  int const max_len = 256;
  char dst[max_len + 1];
  int len = cdict_word(CDICT_VAL(dict), Int_val(index), dst, max_len);
  dst[len] = '\0';
  CAMLreturn(caml_copy_string(dst));
}

value cdict_suffixes_ocaml(value dict, value result, value length)
{
  CAMLparam3(dict, result, length);
  CAMLlocal1(array);
  int dst_len = Int_val(length);
  int dst[dst_len];
  cdict_result_t r;
  result_of_value(result, &r);
  int final_len = cdict_suffixes(CDICT_VAL(dict), &r, dst, dst_len);
  array = caml_alloc_tuple(final_len);
  for (int i = 0; i < final_len; i++)
    Store_field(array, i, Val_int(dst[i]));
  CAMLreturn(array);
}

value cdict_distance_ocaml(value dict, value word, value dist, value count)
{
  CAMLparam4(dict, word, dist, count);
  CAMLlocal1(array);
  int dst_len = Int_val(count);
  int dst[dst_len];
  int final_len =
    cdict_distance(CDICT_VAL(dict), String_val(word), caml_string_length(word),
        Int_val(dist), dst, dst_len);
  array = caml_alloc_tuple(final_len);
  for (int i = 0; i < final_len; i++)
    Store_field(array, i, Val_int(dst[i]));
  CAMLreturn(array);
}
