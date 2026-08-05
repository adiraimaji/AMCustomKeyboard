package adiraimaji.cdict;

public class Cdict
{
  public static final class Result
  {
    /** Whether the word is recognized. */
    public final boolean found;
    /** Unique index of the word within the dictionary.
      Ranges from 0 to the number of words in the dictionary - 1. Can be used
      to lookup word metadata. */
    public final int index;
    /** Internal pointer used by the suffixes function. */
    public final long prefix_ptr;
    /** Index before alias resolution. Used internally. */
    public final int original_index;

    // Constructed from C code.
    private Result()
    { found = false; index = -1; prefix_ptr = 0; original_index = -1; }
  }

  /** Dictionary name. */
  public final String name;

  /** Load a dictionary file stored in a string. The dictionaries contained in
      the file are returned as an array. They can be distinguished using the
      [name] field, the main dictionary is named "main". The data is copied and
      not modified. Use [cdict-tool] to construct the dictionary. */
  public static Cdict[] of_bytes(byte[] data) throws ConstructionError
  { return new Header(of_bytes_native(data)).get_dicts(); }

  /** Check whether the given word is recognized by the dictionary. Never
      return null. */
  public Result find(String word)
  { return find_native(_ptr, word); }

  /** Lookup the frequency of a word. The frequency ranges from 0 to 15
      included and is used to sort words returned by [suffixes] and [distance].
      A higher value means a more frequent word in usage. [index] is a word
      index, either found in [Result.index] or returned by [suffixes] and
      [distance]. */
  public int freq(int index)
  { return freq_native(_ptr, index); }

  /** Lookup the word at a given index. [index] is a word index, either found
      in [Result.index] or returned by [suffixes] and [distance]. */
  public String word(int index)
  { return word_native(_ptr, index); }

  /** List words that starts with the query passed to [find]. This can be called
      even if [result.found] is false. The returned array cannot contain more than
      [count] elements but might be smaller. */
  public int[] suffixes(Result result, int count)
  { return suffixes_native(_ptr, result, count); }

  /** [distance dict word ~dist ~count] lists words that are a specified number
      of edits from the given word according to Levenshtein distance.
      Adding suffixes of any length is considered to be a single edition. Do
      not return words that have a distance less than [dist]. The [count] most
      frequent words are returned. The returned array cannot contain more than
      [count] elements but might be smaller. */
  public int[] distance(String word, int distance, int count)
  { return distance_native(_ptr, word, distance, count); }

  /** Version of the dictionary's format. Dictionaries built for a different
      version are not compatible. */
  public static native int format_version();

  /** Thrown during construction. */
  public static class ConstructionError extends Exception
  {
    public ConstructionError(String msg) { super(msg); }
  }

  /** Internals */

  // A pointer to C allocated memory.
  private final long _ptr;
  private final Header _header;

  private Cdict(String n, long p, Header h)
  {
    name = n;
    _ptr = p;
    _header = h;
  }

  static
  {
    System.loadLibrary("cdict_java");
    init();
  }

  private static class Header
  {
    private final long _ptr;
    private Header(long p) { _ptr = p; }

    public Cdict[] get_dicts() { return get_dicts_native(_ptr); }
    public native Cdict[] get_dicts_native(long header_ptr);
    @Override
    protected void finalize() throws Throwable { finalize_header(_ptr); }
  }

  private static native void init();
  private static native long of_bytes_native(byte[] data);
  private static native void finalize_header(long header);
  private static native Result find_native(long dict, String word);
  private static native int freq_native(long dict, int index);
  private static native String word_native(long dict, int index);
  private static native int[] suffixes_native(long dict, Result result,
      int count);
  private static native int[] distance_native(long dict, String word,
      int distance, int count);
}
