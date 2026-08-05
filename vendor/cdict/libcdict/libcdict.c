#include "libcdict.h"
#include "libcdict_format.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static inline int decode_int24(uint8_t const *ar)
{
  return (((int)(int8_t)ar[0]) << 16) | (ar[1] << 8) | ar[2];
}

static inline int decode_int32(uint8_t const *ar)
{
  return (decode_int24(ar) << 8) | ar[3];
}

static inline int min(int a, int b) { return (a < b) ? a : b; }

/** ************************************************************************
    cdict_of_string
    ************************************************************************ */

cdict_cnstr_result_t cdict_of_string(char const *data, int size,
    cdict_header_t *dst)
{
  header_t const *src_h = (void const*)data;
  if (memcmp(src_h->magic, HEADER_MAGIC, sizeof(src_h->magic)) != 0)
    return CDICT_NOT_A_DICTIONARY;
  if (src_h->version != FORMAT_VERSION)
    return CDICT_UNSUPPORTED_FORMAT;
  *dst = (cdict_header_t){
    .data = data,
    .n_dicts = src_h->dict_count,
    .total_size = size
  };
  return CDICT_OK;
}

void cdict_get_dict(cdict_header_t const *header, int i, cdict_t *dst)
{
  void const *data = header->data;
  dict_header_t const *dh = &((header_t const*)data)->dicts[i];
  *dst = (cdict_t){
    .name = data + decode_int32(dh->name_off),
    .root_node = data + decode_int32(dh->root_ptr),
    .freq = data + decode_int32(dh->freq_off),
    .aliases_header = dh->aliases_header,
    .aliases_length = decode_int24(dh->aliases_length),
    .aliases_keys = data + decode_int32(dh->aliases_keys),
    .aliases_values = data + decode_int32(dh->aliases_values),
  };
}

char const* cdict_cnstr_result_to_string(cdict_cnstr_result_t r)
{
  switch (r)
  {
    case CDICT_OK: return "OK";
    case CDICT_NOT_A_DICTIONARY: return "Not a dictionary";
    case CDICT_UNSUPPORTED_FORMAT: return "Unsupported format";
  }
  return "";
}

int cdict_format_version() { return FORMAT_VERSION; }

/** ************************************************************************
    Aliases
    ************************************************************************ */

/** Search for [key] in the complete tree encoded in array [ar] with format
    [fmt] and length [length]. Return [-1] if the key is not found. */
static int complete_tree_search_unsigned(uint8_t const *ar, format_t fmt,
    unsigned int length, unsigned int key)
{
  unsigned int i = 0;
  while (i < length)
  {
    unsigned int ar_i = sized_int_array_unsigned(ar, fmt, i);
    if (ar_i == key)
      return i;
    i = i * 2 + ((key < ar_i) ? 1 : 2);
  }
  return -1;
}

/** Return the new index if an alias exists or [-1]. */
static int resolve_alias(cdict_t const *dict, int index)
{
  if (dict->aliases_length == 0)
    return -1;
  int i =
    complete_tree_search_unsigned(dict->aliases_keys,
        ALIASES_KEY_FORMAT(dict), dict->aliases_length, index);
  if (i < 0)
    return -1;
  return sized_int_array_unsigned(dict->aliases_values,
        ALIASES_VALUES_FORMAT(dict), i);
}

/** ************************************************************************
    cdict_find
    ************************************************************************ */

static void cdict_find_node(void const *parent_node, int ptr,
    char const *word, char const *end, int index, cdict_result_t *result);

static void cdict_find_branches(branches_t const *b,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  uchar c = *word;
  int len = b->length;
  for (int i = 0; i < len;)
  {
    // [l] is NUL when stepping outside of the tree but [k] cannot be NUL so we
    // don't have to check for this case.
    uchar l = b->labels[i];
    if (c == l)
    {
      index += branch_number(b, i);
      cdict_find_node(b, branch(b, i), word + 1, end, index, result);
      return;
    }
    else if (c < l)
      i = i * 2 + 1;
    else
      i = i * 2 + 2;
  }
}

static void find_ends(intptr_t prefix_ptr, int index, bool found,
    cdict_result_t *result)
{
  result->found = found;
  result->index = index;
  result->prefix_ptr = prefix_ptr;
  result->original_index = index;
}

static void cdict_find_prefix(prefix_t const *node,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  uchar const *prefix = node->prefix;
  uchar const *prefix_end = prefix + PREFIX_LENGTH(node);
  int next_ptr = decode_int24(node->next_ptr);
  while (true)
  {
    uchar c = *word++;
    if (c != *(prefix++)) return; // Prefix doesn't match
    if (prefix == prefix_end) // Prefix matches
      return cdict_find_node(node, next_ptr, word, end, index, result);
    if (word == end) // Query ends
      return find_ends(PREFIX_PTR(next_ptr, node), index, false, result);
  }
}

static void cdict_find_node(void const *parent_node, int ptr,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  bool is_final = PTR_IS_FINAL(ptr);
  void const *node = PTR_NODE(ptr, parent_node);
  if (word == end)
    return find_ends(PREFIX_PTR(ptr, parent_node), index, is_final, result);
  if (is_final)
    index++;
  switch (NODE_KIND(node))
  {
    case BRANCHES:
      return cdict_find_branches(node, word, end, index, result);
    case PREFIX: return cdict_find_prefix(node, word, end, index, result);
    default: return;
  }
}

#define RESULT_T_INIT ((cdict_result_t){ \
    .found = false, .index = 0, .prefix_ptr = 0, .original_index = 0 })

void cdict_find(cdict_t const *dict, char const *word, int word_size,
    cdict_result_t *result)
{
  *result = RESULT_T_INIT;
  cdict_find_node(dict->root_node, 0, word, word + word_size, 0,
      result);
  if (result->found)
  {
    int alias = resolve_alias(dict, result->index);
    if (alias >= 0)
      result->index = alias;
  }
}

/** ************************************************************************
    cdict_freq
    ************************************************************************ */

int cdict_freq(cdict_t const *dict, int index)
{
  uint8_t f = dict->freq[index / 2];
  if (index & 1) f = f >> 4;
  return f & 0xF;
}

/** ************************************************************************
    cdict_word
    ************************************************************************ */

static int cdict_word_node(void const *parent_node, int ptr, int index,
    char *dst, int dsti, int max_len);

static int cdict_word_branches(branches_t const *b, int index,
    char *dst, int dsti, int max_len)
{
  // The 'number' field of each transition is in the same order as the labels.
  int len = b->length;
  int next = 0;
  int next_number = 0;
  for (int i = 0; i < len;)
  {
    int ni = branch_number(b, i);
    int bi = branch(b, i);
    if (ni > index)
      i = i * 2 + 1;
    else
    {
      next = bi;
      next_number = ni;
      dst[dsti] = b->labels[i];
      i = i * 2 + 2;
    }
  }
  if (next == 0)
    return dsti;
  return cdict_word_node(b, next, index - next_number, dst, dsti + 1,
      max_len);
}

static int cdict_word_prefix(prefix_t const *p, int index,
    char *dst, int dsti, int max_len)
{
  int end = dsti + PREFIX_LENGTH(p);
  uchar const *prefix = p->prefix;
  if (end > max_len)
    end = max_len;
  while (dsti < end)
    dst[dsti++] = *(prefix++);
  return cdict_word_node(p, decode_int24(p->next_ptr), index, dst, end, max_len);
}

static int cdict_word_node(void const *parent_node, int ptr, int index,
    char *dst, int dsti, int max_len)
{
  if (dsti >= max_len)
    return dsti;
  void const *node = PTR_NODE(ptr, parent_node);
  if (PTR_IS_FINAL(ptr))
  {
    if (index == 0)
      return dsti;
    index--;
  }
  switch (NODE_KIND(node))
  {
    case BRANCHES: return cdict_word_branches(node, index, dst, dsti, max_len);
    case PREFIX: return cdict_word_prefix(node, index, dst, dsti, max_len);
  }
  return dsti;
}

int cdict_word(cdict_t const *dict, int index, char *dst, int max_length)
{
  return cdict_word_node(dict->root_node, 0, index, dst, 0, max_length);
}

/** ************************************************************************
    Priority max-queue storing the N most frequent words.
    ************************************************************************ */

typedef struct
{
  unsigned int freq:4;
  unsigned int index:28;
} word_freq_t;

/** Orders by decreasing frequencies. Elements with the same frequency are
    ordered by increasing [index] value, which corresponds to the alphabetical
    order. */
static int word_freq_compare(void const *a_, void const *b_)
{
  word_freq_t const *a = a_;
  word_freq_t const *b = b_;
  int d = b->freq - a->freq;
  if (d != 0) return d;
  return a->index - b->index;
}

typedef struct
{
  word_freq_t *q;
  int ends; // Cannot be bigger than [max_length].
  int max_length;
} priority_t;

static void priority_init(priority_t *p, word_freq_t *q, int q_len)
{
  *p = (priority_t){ .q = q, .ends = 0, .max_length = q_len };
}

/** This modifies the structure [p] inplace and then empties it. Returns the
    number of elements written to [dst]. */
static int priority_to_sorted_array(priority_t *p, int *dst, int count)
{
  qsort(p->q, p->ends, sizeof(word_freq_t), &word_freq_compare);
  count = min(count, p->ends);
  for (int i = 0; i < count; i++)
    dst[i] = p->q[i].index;
  p->ends = 0;
  return count;
}

/** Push an element and remove the lowest ranking element at the same time.
    The removed element can be the same as the element being added. This
    function works even when the queue is full. */
static void priority_pushpop(priority_t *p, word_freq_t new)
{
  word_freq_t *q = p->q;
  if (p->ends == 0 || word_freq_compare(&q[0], &new) <= 0)
    return;
  int i = 0, biggest = 0, ends = p->ends;
  while (true)
  {
    int left = i * 2 + 1, right = i * 2 + 2;
    if (left >= ends)
      break;
    biggest = (right < ends && word_freq_compare(&q[right], &q[left]) > 0) ?
      right : left;
    if (word_freq_compare(&q[biggest], &new) <= 0)
      break;
    q[i] = q[biggest];
    i = biggest;
  }
  q[i] = new;
}

/** Add a word to the priority queue, possibly removing an other word from it.
    Do nothing if the queue is full and the new word ranks higher than any
    other word already stored. Words are ordered with [word_freq_compare()]. */
static void priority_add(priority_t *p, int freq, int index)
{
  word_freq_t new = (word_freq_t){ .freq = freq, .index = index };
  if (p->ends == p->max_length)
  {
    priority_pushpop(p, new);
    return;
  }
  word_freq_t *q = p->q;
  int i = p->ends;
  p->ends++;
  while (i > 0)
  {
    int parent = (i - 1) / 2;
    if (word_freq_compare(&q[parent], &new) >= 0)
      break;
    q[i] = q[parent];
    i = parent;
  }
  q[i] = new;
}

/** Resole aliases and call [priority_add]. */
static void priority_add_resolved(cdict_t const *dict, priority_t *dst,
    int index)
{
  int alias = resolve_alias(dict, index);
  if (alias >= 0)
    index = alias;
  priority_add(dst, cdict_freq(dict, index), index);
}

/** ************************************************************************
    cdict_suffixes
    ************************************************************************ */

static void suffixes(cdict_t const *dict, void const *parent_node, int ptr,
    int index, priority_t *dst);

static void suffixes_branches(cdict_t const *dict, branches_t const *b,
    int index, priority_t *dst)
{
  int len = b->length;
  for (int i = 0; i < len; i++)
    suffixes(dict, b, branch(b, i), index + branch_number(b, i), dst);
}

static void suffixes(cdict_t const *dict, void const *parent_node, int ptr,
    int index, priority_t *dst)
{
  void const *node = PTR_NODE(ptr, parent_node);
  if (PTR_IS_FINAL(ptr))
  {
    priority_add_resolved(dict, dst, index);
    index++;
  }
  switch (NODE_KIND(node))
  {
    case BRANCHES:
      suffixes_branches(dict, node, index, dst);
      break;
    case PREFIX:
      suffixes(dict, node, decode_int24(((prefix_t const*)node)->next_ptr),
          index, dst);
      break;
  }
}

int cdict_suffixes(cdict_t const *dict, cdict_result_t const *r, int *dst,
    int count)
{
  if (r->prefix_ptr == 0)
    return 0;
  word_freq_t words[count];
  priority_t queue;
  priority_init(&queue, words, count);
  suffixes(dict, PREFIX_PTR_PARENT(r->prefix_ptr),
      PREFIX_PTR_PTR(r->prefix_ptr), r->original_index, &queue);
  return priority_to_sorted_array(&queue, dst, count);
}

/** ************************************************************************
    cdict_distance
    ************************************************************************ */

static void distance(cdict_t const *dict, void const *parent_node, int ptr,
    char const *word, char const *end, int index, int dist, priority_t *q);

static void distance_branches(cdict_t const *dict, branches_t const *b,
    char const *word, char const *end, int index, int dist, priority_t *q)
{
  int len = b->length;
  uchar c = *word;
  for (int i = 0; i < len; i++)
  {
    int next_index = index + branch_number(b, i);
    int bi = branch(b, i);
    if (c == b->labels[i])
    {
      distance(dict, b, bi, word + 1, end, next_index, dist, q);
    }
    else
    {
      // Change a letter
      distance(dict, b, bi, word + 1, end, next_index, dist - 1, q);
      // Add a letter
      distance(dict, b, bi, word, end, next_index, dist - 1, q);
    }
  }
}

/** Levenshtein distance between the strings 'a' and 'b'. */
static int levenshtein_distance(char const *a, int a_length,
    char const *b, int b_length)
{
  // From https://en.wikipedia.org/wiki/Levenshtein_distance
  int array[(b_length + 1) * 2];
  int *v0 = array;
  int *v1 = v0 + b_length + 1;
  for (int i = 0; i <= b_length; i++)
    v0[i] = i;
  for (int i = 0; i < a_length; i++)
  {
    v1[0] = i + 1;
    for (int j = 0; j < b_length; j++)
    {
      int del = v0[j + 1] + 1;
      int ins = v1[j] + 1;
      int rpl = v0[j] + ((a[i] == b[j]) ? 0 : 1);
      v1[j + 1] = min(del, min(ins, rpl));
    }
    int *swap = v0;
    v0 = v1; v1 = swap;
  }
  return v0[b_length];
}

static void distance_prefix(cdict_t const *dict, prefix_t const *p,
    char const *word, char const *end, int index, int dist, priority_t *q)
{
  int len = PREFIX_LENGTH(p);
  int word_len = ((intptr_t)end) - ((intptr_t)word);
  int common_len = min(word_len, len);
  int pdist =
    levenshtein_distance(word, common_len, (char const*)p->prefix, common_len);
  int next_ptr = decode_int24(p->next_ptr);
  if (word_len < len)
  {
    if (pdist + 1 == dist)
      return suffixes(dict, p, next_ptr, index, q);
    pdist += len - word_len;
  }
  int next_dist = dist - pdist;
  for (int i = common_len; i >= 0 && next_dist >= 0; i--)
  {
    // Add suffixes of 'p', including the empty suffix.
    distance(dict, p, next_ptr, word + i, end, index, next_dist, q);
    next_dist--;
  }
}

static void distance(cdict_t const *dict, void const *parent_node, int ptr,
    char const *word, char const *end, int index, int dist, priority_t *q)
{
  if (dist == 0)
  {
    cdict_result_t r = RESULT_T_INIT;
    cdict_find_node(parent_node, ptr, word, end, index, &r);
    if (r.found)
      priority_add_resolved(dict, q, r.index);
    return;
  }
  void const *node = PTR_NODE(ptr, parent_node);
  if (PTR_IS_FINAL(ptr))
    index++;
  if (word == end)
  {
    // Adding a suffixe of any length is a single edit
    // Remove the final flag before calling [suffixes] to avoid returning the
    // queried word and to avoid incrementing [index] twice.
    if (dist == 1)
      suffixes(dict, node, 0, index, q);
    return;
  }
  switch (NODE_KIND(node))
  {
    case BRANCHES:
      // Remove a letter
      distance(dict, node, 0, word + 1, end, index, dist - 1, q);
      return distance_branches(dict, node, word, end, index, dist, q);
    case PREFIX:
      return distance_prefix(dict, node, word, end, index, dist, q);
  }
}

int cdict_distance(cdict_t const *dict, char const *word, int wlen, int dist,
    int *dst, int count)
{
  word_freq_t words[count];
  priority_t queue;
  priority_init(&queue, words, count);
  distance(dict, dict->root_node, 0, word, word + wlen, 0, dist, &queue);
  return priority_to_sorted_array(&queue, dst, count);
}
