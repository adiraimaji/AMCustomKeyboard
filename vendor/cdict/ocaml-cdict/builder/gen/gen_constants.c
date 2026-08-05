#include <libcdict_format.h>
#include <stddef.h>
#include <stdio.h>

static int indent = 0;
#define P_LINE(FMT, ...) printf("%*s" FMT "\n", indent * 2, "", ##__VA_ARGS__)

static void module(char const *name)
{
  P_LINE("module %s = struct", name);
  indent++;
}

static void end()
{
  indent--;
  P_LINE("end");
}

#define FIELD(TYPE, FIELD) P_LINE("let %s = %d", #TYPE "_" #FIELD, (int)offsetof(TYPE, FIELD))
#define SIZE(TYPE) P_LINE("let %s = %d", #TYPE, (int)sizeof(TYPE))
#define VAL(PREFIX, VAL) P_LINE("let %s = %d", #PREFIX #VAL, (int)VAL)
#define VAL_INT32(PREFIX, VAL) P_LINE("let %s = %dl", #PREFIX #VAL, (int32_t)VAL)
#define VAL_STR(PREFIX, VAL) P_LINE("let %s = \"%s\"", #PREFIX #VAL, VAL)

int main()
{
  module("C");

  VAL(c_, FORMAT_VERSION);
  VAL(c_, FORMAT_4_BITS);
  VAL(c_, FORMAT_8_BITS);
  VAL(c_, FORMAT_16_BITS);
  VAL(c_, FORMAT_24_BITS);
  VAL(c_, MAX_FORMAT_T);
  VAL(c_, PREFIX_MAX_LENGTH);
  VAL(c_, BRANCHES_BRANCHES_FORMAT_OFFSET);
  VAL(c_, BRANCHES_NUMBERS_FORMAT_OFFSET);
  VAL(c_, PREFIX_LENGTH_OFFSET);
  VAL(c_, ALIASES_KEYS_FORMAT_OFFSET);
  VAL(c_, ALIASES_VALUES_FORMAT_OFFSET);
  VAL_STR(c_, HEADER_MAGIC);
  VAL(mask_, PTR_OFFSET_MASK);
  VAL(flag_, PTR_FLAG_FINAL);
  VAL(tag_, BRANCHES);
  VAL(tag_, PREFIX);

  end();
  module("O");

  FIELD(branches_t, header);
  FIELD(branches_t, length);
  FIELD(branches_t, labels);
  FIELD(prefix_t, next_ptr);
  FIELD(prefix_t, header);
  FIELD(prefix_t, prefix);
  FIELD(header_t, magic);
  FIELD(header_t, version);
  FIELD(header_t, dict_count);
  FIELD(header_t, dicts);
  FIELD(dict_header_t, name_off);
  FIELD(dict_header_t, root_ptr);
  FIELD(dict_header_t, freq_off);
  FIELD(dict_header_t, aliases_header);
  FIELD(dict_header_t, aliases_length);
  FIELD(dict_header_t, aliases_keys);
  FIELD(dict_header_t, aliases_values);

  end();
  module("S");

  SIZE(branches_t);
  SIZE(prefix_t);
  SIZE(header_t);
  SIZE(dict_header_t);

  end();
  return 0;
}
