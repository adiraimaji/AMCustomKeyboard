/** libdict

This file describes the encoding of the dictionary.

Dictionaries are made of BRANCHES and PREFIX nodes that each consume a part of
the query and contain transitions to the next nodes.

Transitions are made of:
- a pointer, a relative signed offset to the next node,
- a number, used to compute the word index and
- a final flag, that signals that we reached the end of a word.

All integers are in big-endian order.
*/

/** Incremented when the format changes. */
#define FORMAT_VERSION 1

#include <stdint.h>

/** Chars are unsigned in the data-structure. */
typedef uint8_t uchar;

typedef enum
{
  BRANCHES = 0b0,
  PREFIX = 0b1,
} kind_t;

/** The first byte of nodes contains the node's kind with this mask. */
#define NODE_KIND_MASK 0b1u
#define NODE_KIND_BIT_LENGTH 1

#define NODE_KIND(NODE) (((uint8_t const*)(NODE))[0] & NODE_KIND_MASK)

/** Pointers. */

/** Whether the pointer is a final transition. */
#define PTR_FLAG_FINAL 0b1
#define PTR_OFFSET_MASK (~PTR_FLAG_FINAL)

#define PTR_IS_FINAL(PTR) (bool)((PTR) & PTR_FLAG_FINAL)
#define PTR_NODE(PTR, PARENT_NODE) \
  (((void const*)(PARENT_NODE)) + (((int)(PTR)) & PTR_OFFSET_MASK))

/** Sized integer arrays. */

/** Format of integer arrays. All formats are big-endians. */
typedef enum
{
  FORMAT_4_BITS = 1,
  FORMAT_8_BITS = 2,
  FORMAT_16_BITS = 4,
  FORMAT_24_BITS = 6,
} format_t;

/** Mask masking every values of [format_t]. */
#define MAX_FORMAT_T 0b111
#define FORMAT_T_BIT_LENGTH 3

/** Size of an array of N elements with format F. */
#define FORMAT_ARRAY_INDEX(F, I) (((F) * (I)) >> 1)
#define FORMAT_ARRAY_SIZE(F, N) (((F) * (N) + 1) >> 1)

/** Access the [i]th unsigned integer in array [ar] of format [fmt]. */
static inline int sized_int_array_unsigned(uint8_t const *ar, format_t fmt, int i)
{
  if (fmt == FORMAT_8_BITS)
    return ar[i];
  ar = ar + FORMAT_ARRAY_INDEX(fmt, i);
  uint8_t ar0 = ar[0];
  if (fmt == FORMAT_4_BITS)
    return (i & 1) ? ar0 >> 4 : ar0 & 0xF;
  if (fmt == FORMAT_16_BITS)
    return ((ar0 << 8) | ar[1]);
  return (int)(unsigned)((ar0 << 16) | (ar[1] << 8) | ar[2]);
}

/** Access the [i]th unsigned integer in array [ar] of format [fmt]. */
static inline int sized_int_array_signed(uint8_t const *ar, format_t fmt, int i)
{
  if (fmt == FORMAT_8_BITS)
    return (int)(int8_t)ar[i];
  ar = ar + FORMAT_ARRAY_INDEX(fmt, i);
  uint8_t ar0 = ar[0];
  if (fmt == FORMAT_4_BITS)
    return (i & 1) ? ((int8_t)ar0) >> 4 :
      (ar0 & 0x8) ? (int)ar0 | ~0xF : ar0 & 0x7;
  if (fmt == FORMAT_16_BITS)
    return ((int)((int8_t)ar0 << 8) | ar[1]);
  return (int)(((int8_t)ar0 << 16) | (ar[1] << 8) | ar[2]);
}

/** BRANCHES nodes (size = 2 bytes + (1 + X + Y) * n_branches)
where X and Y can be 0.5, 1, 2 or 3.

A branching node that consumes 1 byte from the query. The branch labels are
stored in a binary search tree.
First search for the current byte prefix into 'labels' then lookup the
corresponding pointer in 'branches' and number in 'numbers'

Branches are not NULL and there is no padding within the node.
*/

typedef struct
{
  uint8_t header;
  /** Format of the 'branches' and 'numbers' array and node kind. */
  uint8_t length;
  /** Length of the 'labels' and 'branches' arrays. Not the offset to the
      'branches' array. */
  uchar labels[];
  // uint8_t branches[]; /** Use [branch(b, i)] to access. */
  // uint8_t numbers[]; /** Use [branch_number(b, i)] to access. */
} branches_t;

#define BRANCHES_BRANCHES_FORMAT_OFFSET NODE_KIND_BIT_LENGTH
#define BRANCHES_NUMBERS_FORMAT_OFFSET \
  (BRANCHES_BRANCHES_FORMAT_OFFSET + FORMAT_T_BIT_LENGTH)

#define BRANCHES_BRANCHES_FORMAT(B) \
  ((format_t)(((B)->header >> BRANCHES_BRANCHES_FORMAT_OFFSET) & MAX_FORMAT_T))

#define BRANCHES_NUMBERS_FORMAT(B) \
  ((format_t)(((B)->header >> BRANCHES_NUMBERS_FORMAT_OFFSET) & MAX_FORMAT_T))

/** Access a branch. */
// TODO: Using 'inline' make the program crash
[[maybe_unused]] static int branch(branches_t const *b, int i)
{
  format_t fmt = BRANCHES_BRANCHES_FORMAT(b);
  uint8_t const *ar = ((void const*)b) + (sizeof(branches_t) + b->length);
  return sized_int_array_signed(ar, fmt, i);
}

/** Access a number. */
[[maybe_unused]] static unsigned int branch_number(branches_t const *b, int i)
{
  format_t branches_fmt = BRANCHES_BRANCHES_FORMAT(b);
  uint8_t const *ar = ((void const*)b) + (sizeof(branches_t) + b->length +
     FORMAT_ARRAY_SIZE(branches_fmt, b->length));
  return sized_int_array_unsigned(ar, BRANCHES_NUMBERS_FORMAT(b), i);
}

/** PREFIX nodes (size = 4 bytes + prefix length)

A node consuming a prefix of the query, which can have a size from 1 to
PREFIX_MAX_LENGTH.
The node contains a single transition that have the 'number' field equal to 0.
The length cannot be 0.
*/

typedef struct
{
  uint8_t header; /** Length and node kind. */
  uint8_t next_ptr[3]; /** 24-bits big-endian signed integer. */
  uchar prefix[];
} prefix_t;

#define PREFIX_LENGTH_OFFSET NODE_KIND_BIT_LENGTH
#define PREFIX_MAX_LENGTH ((int)(uint8_t)(0xFFu << PREFIX_LENGTH_OFFSET))

/** Length of the [prefix] array. */
#define PREFIX_LENGTH(P) ((P)->header >> PREFIX_LENGTH_OFFSET)

/** Prefix pointer

This is a pointer to a node, with the final flag embedded.
It is exposed in 'cdict_result_t' but not used outside of the library.
'prefix_ptr' can be NULL.
*/

#define PREFIX_PTR_PARENT(P) ((void const*)((P) & PTR_OFFSET_MASK))
#define PREFIX_PTR_PTR(P) ((int)((P) & ~PTR_OFFSET_MASK))
#define PREFIX_PTR_IS_FINAL(P) PTR_IS_FINAL(P)
#define PREFIX_PTR(PTR, PARENT_NODE) \
  (((intptr_t)(PARENT_NODE)) + (int)(PTR))

/** Dictionary header (size = 8 bytes)

Located at the beginning of the dictionary.
*/

/** 32-bits big-endian signed integer. */
typedef uint8_t int32be[4];
/** 24-bits big-endian signed integer. */
typedef uint8_t int24be[3];

typedef struct
{
  int32be name_off;
  /** Absolute offset to the NUL-terminated name of the dictionary. */
  int32be root_ptr;
  /** Pointer to the root node. */
  int32be freq_off;
  /** Absolute offset to the 4-bits integer array storing the frequency of each
      words. */
  uint8_t aliases_header;
  /** Specifies the format of [aliases_keys] and [aliases_values]. */
  int24be aliases_length;
  /** Number of aliases. */
  int32be aliases_keys;
  /** Absolute offset to a complete binary tree encoded as an array. It stores
      the indexes of words that are aliases to other words. The array format is
      specified by [aliases_header].  */
  int32be aliases_values;
  /** Absolute offset to an array whose format is specified by
      [aliases_header]. Stores indexes to words pointed by aliases in
      [aliases_keys]. */
} dict_header_t;

/** Offset in [aliases_header] to [aliases_keys]'s format (format_t). */
#define ALIASES_KEYS_FORMAT_OFFSET 0
/** Offset in [aliases_header] to [aliases_values]'s format (format_t). */
#define ALIASES_VALUES_FORMAT_OFFSET \
  (ALIASES_KEYS_FORMAT_OFFSET + FORMAT_T_BIT_LENGTH)

#define ALIASES_KEY_FORMAT(H) \
  (((H)->aliases_header >> ALIASES_KEYS_FORMAT_OFFSET) & MAX_FORMAT_T)
#define ALIASES_VALUES_FORMAT(H) \
  (((H)->aliases_header >> ALIASES_VALUES_FORMAT_OFFSET) & MAX_FORMAT_T)

typedef struct
{
  uint8_t magic[3]; /** Magic number. */
  uint8_t version;
  /** Format version. Supported version is [FORMAT_VERSION]. */
  uint8_t dict_count; /** Number of dicts in [dicts]. */
  dict_header_t dicts[]; /** Dictionaries. */
} header_t;

#define HEADER_MAGIC "Dic"
