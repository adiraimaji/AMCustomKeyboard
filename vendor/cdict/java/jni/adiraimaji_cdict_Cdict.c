#include <jni.h>
#include <libcdict.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "adiraimaji_cdict.h"

#pragma GCC diagnostic ignored "-Wunused-parameter"

/** Structure pointed to by the [header_ptr] field in [Cdict.Header]. */
typedef struct
{
  cdict_header_t header;
  cdict_t const *dicts;
  char const data[];
} header_value;

// JNI IDs for the Result class.
static struct
{
  jclass class;
  jfieldID found;
  jfieldID index;
  jfieldID prefix_ptr;
  jfieldID original_index;
} Result;

static jobject result_to_java(JNIEnv *env, cdict_result_t const *result)
{
  jobject jresult = (*env)->AllocObject(env, Result.class);
  (*env)->SetBooleanField(env, jresult, Result.found, result->found);
  (*env)->SetIntField(env, jresult, Result.index, result->index);
  (*env)->SetLongField(env, jresult, Result.prefix_ptr,
      (jlong)result->prefix_ptr);
  (*env)->SetIntField(env, jresult, Result.original_index,
      result->original_index);
  return jresult;
}

static void result_of_java(JNIEnv *env, jobject jresult, cdict_result_t *dst)
{
  dst->found = (*env)->GetBooleanField(env, jresult, Result.found);
  dst->index = (*env)->GetIntField(env, jresult, Result.index);
  dst->prefix_ptr = (*env)->GetLongField(env, jresult, Result.prefix_ptr);
  dst->original_index = (*env)->GetIntField(env, jresult, Result.original_index);
}

static jarray jarray_of_int_array(JNIEnv *env, int const *src, int len)
{
  jarray ar = (*env)->NewIntArray(env, len);
  (*env)->SetIntArrayRegion(env, ar, 0, len, src);
  return ar;
}

/** Define a local var array for storing a Java string converted to UTF-8.
    The biggest jchar is 0xFFFF, which requires 3 bytes in UTF-8.
    Surrogate pairs (4 jchars) require 4 bytes in UTF-8. Allocate 3 bytes for
    every jchar and leave room for the final NUL char. */
#define STACK_ALLOCATED_GETSTRINGUTF(VARNAME, JWORD) \
  int jword_len = (*env)->GetStringLength(env, JWORD); \
  char VARNAME[jword_len * 3 + 1]; \
  memset(VARNAME, 0, sizeof(VARNAME)); \
  (*env)->GetStringUTFRegion(env, JWORD, 0, jword_len, VARNAME);

/** Methods */

JNIEXPORT jint JNICALL Java_adiraimaji_cdict_Cdict_format_1version(JNIEnv *env,
    jclass jcls)
{
  return cdict_format_version();
}

JNIEXPORT void JNICALL Java_adiraimaji_cdict_Cdict_init(JNIEnv *env, jclass jcls)
{
  Result.class =
    (*env)->NewGlobalRef(env,
        (*env)->FindClass(env, "adiraimaji/cdict/Cdict$Result"));
  Result.found = (*env)->GetFieldID(env, Result.class, "found", "Z");
  Result.index = (*env)->GetFieldID(env, Result.class, "index", "I");
  Result.prefix_ptr = (*env)->GetFieldID(env, Result.class, "prefix_ptr", "J");
  Result.original_index = (*env)->GetFieldID(env, Result.class, "original_index", "I");
}

JNIEXPORT jlong JNICALL Java_adiraimaji_cdict_Cdict_of_1bytes_1native
  (JNIEnv *env, jclass _cls, jbyteArray data)
{
  int const len = (*env)->GetArrayLength(env, data);
  // Allocate and copy the dictionary data on the C heap.
  header_value *hv = malloc(sizeof(header_value) + len);
  void *dict_data = ((void*)hv) + sizeof(header_value);
  (*env)->GetByteArrayRegion(env, data, 0, len, dict_data);
  cdict_cnstr_result_t r = cdict_of_string(dict_data, len, &hv->header);
  if (r != CDICT_OK)
  {
    free(hv);
    (*env)->ThrowNew(env,
        (*env)->FindClass(env, "adiraimaji/cdict/Cdict$ConstructionError"),
        cdict_cnstr_result_to_string(r));
    return 0;
  }
  int n_dicts = hv->header.n_dicts;
  cdict_t *dicts = malloc(sizeof(cdict_t) * n_dicts);
  for (int i = 0; i < n_dicts; i++)
    cdict_get_dict(&hv->header, i, &dicts[i]);
  hv->dicts = dicts;
  return (jlong)hv;
}

JNIEXPORT jobjectArray JNICALL Java_adiraimaji_cdict_Cdict_00024Header_get_1dicts_1native
  (JNIEnv *env, jobject this, jlong header_ptr)
{
  jclass cdict_cls = (*env)->FindClass(env, "adiraimaji/cdict/Cdict");
  jmethodID cdict_cnstr = (*env)->GetMethodID(env, cdict_cls, "<init>",
      "(Ljava/lang/String;JLadiraimaji/cdict/Cdict$Header;)V");
  header_value const *hv = (void*)header_ptr;
  int n_dicts = hv->header.n_dicts;
  jobjectArray jdicts = (*env)->NewObjectArray(env, n_dicts, cdict_cls, NULL);
  for (int i = 0; i < n_dicts; i++)
  {
    jstring jname = (*env)->NewStringUTF(env, hv->dicts[i].name);
    (*env)->SetObjectArrayElement(env, jdicts, i,
        (*env)->NewObject(env, cdict_cls, cdict_cnstr, jname,
          (jlong)&hv->dicts[i], this));
  }
  return jdicts;
}

JNIEXPORT void JNICALL Java_adiraimaji_cdict_Cdict_finalize_1header
  (JNIEnv *env, jclass _cls, jlong header_ptr)
{
  free((void*)header_ptr);
}

JNIEXPORT jobject JNICALL Java_adiraimaji_cdict_Cdict_find_1native
  (JNIEnv *env, jclass _cls, jlong dictl, jstring jword)
{
  cdict_t const *dict = (cdict_t const*)dictl;
  STACK_ALLOCATED_GETSTRINGUTF(cword, jword);
  cdict_result_t result;
  cdict_find(dict, cword, strlen(cword), &result);
  return result_to_java(env, &result);
}

JNIEXPORT jint JNICALL Java_adiraimaji_cdict_Cdict_freq_1native
  (JNIEnv *_env, jclass _cls, jlong dictl, jint index)
{
  cdict_t const *dict = (cdict_t const*)dictl;
  return cdict_freq(dict, index);
}

JNIEXPORT jstring JNICALL Java_adiraimaji_cdict_Cdict_word_1native
  (JNIEnv *env, jclass _cls, jlong dictl, jint index)
{
  cdict_t const *dict = (cdict_t const*)dictl;
  int const max_len = 256;
  char dst[max_len + 1];
  int len = cdict_word(dict, index, dst, max_len);
  dst[len] = '\0';
  return (*env)->NewStringUTF(env, dst);
}

JNIEXPORT jintArray JNICALL Java_adiraimaji_cdict_Cdict_suffixes_1native
  (JNIEnv *env, jclass _cls, jlong dictl, jobject jresult, jint count)
{
  cdict_t const *dict = (cdict_t const*)dictl;
  cdict_result_t result;
  result_of_java(env, jresult, &result);
  int indexes[count];
  int final_len = cdict_suffixes(dict, &result, indexes, count);
  return jarray_of_int_array(env, indexes, final_len);
}

JNIEXPORT jintArray JNICALL Java_adiraimaji_cdict_Cdict_distance_1native
  (JNIEnv *env, jclass _cls, jlong dictl, jstring jword, jint distance,
   jint count)
{
  cdict_t const *dict = (cdict_t const*)dictl;
  STACK_ALLOCATED_GETSTRINGUTF(cword, jword);
  int indexes[count];
  int final_len = cdict_distance(dict, cword, strlen(cword), distance,
      indexes, count);
  return jarray_of_int_array(env, indexes, final_len);
}
