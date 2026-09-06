package com.adiraimaji.customkeyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Parsed, validated form of the single stored Tasker Automation JSON:
 {
 "amck_replace": "##",     // optional, default "##"
 "amck_append": "@@",      // optional, default "@@"
 "amck_timeout": "15000",  // optional, default "15000" - ms to wait
 // for a task's result before giving up
 "runtask1": "Task 1",     // any other key -> a Tasker task NAME
 "runtask2": "Task 2",
 "amck_patterns": [ // optional - see [ExpandPattern]
 { "prefix": "\\.\\.", "suffix": " ", "replace_prefix": "true", "fire_on_suffix": "true", "task": "Expand Task 1" },
 { "prefix": "(\\s|^)", "regex": "x.{2,}", "suffix": " ", "replace_prefix": "false", "fire_on_suffix": "false", "task": "TextExpander" }
 ]
 }

 Typing <amck_replace><keyword> (e.g. "##runtask1") sends the entire
 current field's text to the named task and, on completion, replaces
 the ENTIRE field with the task's returned output. Typing
 <amck_append><keyword> (e.g. "@@runtask1") does the same Tasker
 call, but only replaces the typed trigger+keyword itself with the
 output, leaving the rest of the field untouched.

 Each entry in "amck_patterns" describes an open-ended expander built
 around three regexes - "prefix", the optional "regex", and "suffix"
 - all three compiled with [Pattern.MULTILINE], so "^"/"$" also match
 right after/before a newline, not only at the very start/end of the
 whole field. On every keystroke (see
 [TaskerTriggerEngine.check_expand_patterns]), for each entry: find
 the closest "prefix" match before the cursor, then look at whatever
 follows it up to the cursor (the "in-between text"):
 - If that in-between text can be split into a part fully matching
 "regex" (or, if "regex" is omitted, just any non-empty part)
 followed immediately by a part fully matching "suffix" - the split
 landing exactly at the cursor - [task] is fired for this occurrence,
 with %prefix, %keyword (just the "regex" part) AND %suffix all sent.
 This occurrence is then CLOSED: no further keystroke will ever fire
 it again, unless the user backspaces back into (or past) either the
 %keyword or %suffix span, which reopens it - see
 [TaskerTriggerEngine.check_expand_patterns]'s "closed" tracking.
 - Otherwise, if "fire_on_suffix" is "false" and the WHOLE in-between
 text (with no "suffix" yet) fully matches "regex" (or is just
 non-empty, if "regex" is omitted), [task] fires again - %prefix and
 %keyword sent, %suffix left UNSET - and keeps firing again on every
 subsequent qualifying keystroke (typed forward or backspace) for as
 long as that stays true. If "fire_on_suffix" is "true", this
 in-between-only case never fires at all - [task] only ever runs once
 "suffix" itself is matched, same as the very first bullet above.
 - Otherwise (too little typed yet, or content no longer matches),
 nothing happens - the check simply runs again on the next keystroke.

 So "fire_on_suffix": "true" makes an entry behave like a one-shot
 calculation (e.g. "..5+1 " -> a doMath task, nothing runs until the
 whole "..5+1 " is typed) while "fire_on_suffix": "false" makes it a
 continuous live-suggestion trigger (e.g. a text-expander popup that
 updates as you type) that ALSO still fires one final time the moment
 "suffix" completes. Both are otherwise identical in every other
 respect - same regex-based prefix/suffix, same "replace_prefix", same
 field-editing behaviour once a reply lands.

 None of these calls touch the field themselves while they're
 running - seeing [task] fire at all is purely a "Running ..." toast
 (for a "fire_on_suffix": "false" entry, shown once when a fresh
 occurrence starts being live, not on every repeat; always shown for
 the one call a "fire_on_suffix": "true" entry ever makes) - unless
 and until one of them actually replies with non-empty text, at which
 point that reply replaces the matched span: %keyword's span, plus
 %suffix's span if this was a call where "suffix" had matched - PLUS
 %prefix's span too, but ONLY if "replace_prefix" is "true". With
 "replace_prefix": "false", the text "prefix" matched is left
 completely untouched in the field, immediately before wherever the
 replacement lands - this is what stops a leading separator like a
 space from being swallowed along with the replacement. Whatever the
 user typed after the matched span in the meantime (while the call
 that ended up replying was in flight) is carried through untouched
 right after the replacement - so however many overlapping calls end
 up in flight for the same occurrence (only possible for
 "fire_on_suffix": "false"), whichever one's reply lands FIRST wins
 and mutates the field; every other call's own attempt to apply its
 reply then correctly finds the field no longer matches what it
 expected and is silently dropped, however much later it eventually
 arrives.

 "prefix", "suffix", "task", "replace_prefix" and "fire_on_suffix" are
 all required (and, other than the two booleans, non-empty); "regex"
 is the only optional field - omitting it (or leaving it empty) means
 "any non-empty in-between text counts", the same fallback for both
 values of "fire_on_suffix". An invalid regex, or a
 "replace_prefix"/"fire_on_suffix" value that isn't "true" or "false",
 is rejected at [parse] time with a [KeymapJsonUtils.ParseError], same
 as any other malformed field in this JSON.

 If "amck_replace", "amck_append", "amck_timeout" and/or
 "amck_patterns" are missing (or "amck_patterns" is an empty array)
 from the stored JSON, they are auto-filled with their defaults - see
 [DEFAULT_REPLACE_TRIGGER], [DEFAULT_APPEND_TRIGGER],
 [DEFAULT_TIMEOUT_MS], and, for "amck_patterns" specifically, TWO
 example entries are filled in when it's missing entirely - one for
 each value of "fire_on_suffix" - built from
 [DEFAULT_EXPAND_PATTERN_PREFIX] / [DEFAULT_EXPAND_PATTERN_SUFFIX] /
 [DEFAULT_EXPAND_PATTERN_TASK] /
 [DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX] /
 [DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX] and
 [DEFAULT_LIVE_PATTERN_PREFIX] / [DEFAULT_LIVE_PATTERN_REGEX] /
 [DEFAULT_LIVE_PATTERN_SUFFIX] / [DEFAULT_LIVE_PATTERN_REPLACE_PREFIX] /
 [DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX] / [DEFAULT_LIVE_PATTERN_TASK].
 When any default gets auto-filled, [needs_persist] is set to true
 and the caller should write [to_json]'s (beautified) output back to
 storage. */
public final class TaskerAutomationConfig
{
    public static final String KEY_REPLACE_TRIGGER = "amck_replace";
    public static final String KEY_APPEND_TRIGGER = "amck_append";
    public static final String KEY_TIMEOUT_MS = "amck_timeout";
    public static final String KEY_EXPAND_PATTERNS = "amck_patterns";
    /** Optional per-entry key inside "amck_patterns". See [ExpandPattern]. */
    public static final String KEY_EXPAND_PATTERN_REGEX = "regex";
    public static final String DEFAULT_REPLACE_TRIGGER = "##";
    public static final String DEFAULT_APPEND_TRIGGER = "@@";
    public static final long DEFAULT_TIMEOUT_MS = 15000;
    /** Guards against a typo'd huge value stalling the keyboard's
     receiver/timeout bookkeeping indefinitely, and against 0/negative
     values that would fire the timeout immediately or never register. */
    public static final long MIN_TIMEOUT_MS = 1000;
    public static final long MAX_TIMEOUT_MS = 120000;

    /** First of the two example entries auto-filled into
     "amck_patterns" when that key is missing (or present but empty)
     from the stored JSON - a "fire_on_suffix": "true" (one-shot,
     calculation-style) entry. Rename the task in-place to whichever
     Tasker task you want it to run. [DEFAULT_EXPAND_PATTERN_PREFIX]
     is a regex matching the literal 2 characters ".." (the dots are
     escaped since "prefix" is a regex now, and a bare "." would
     instead match any single character). */
    public static final String DEFAULT_EXPAND_PATTERN_PREFIX = "\\.\\.";
    public static final String DEFAULT_EXPAND_PATTERN_SUFFIX = " ";
    public static final String DEFAULT_EXPAND_PATTERN_TASK = "ReplaceYourTaskName";
    public static final String DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX = "true";
    public static final String DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX = "true";

    /** Second of the two example entries auto-filled into
     "amck_patterns" when that key is missing entirely - a
     "fire_on_suffix": "false" (continuous, live-suggestion-style)
     entry. Rename the task in-place to whichever Tasker task you want
     it to run. */
    public static final String DEFAULT_LIVE_PATTERN_PREFIX = "(\\s|^)";
    public static final String DEFAULT_LIVE_PATTERN_REGEX = "x.{2,}";
    public static final String DEFAULT_LIVE_PATTERN_SUFFIX = " ";
    public static final String DEFAULT_LIVE_PATTERN_TASK = "ReplaceYourTaskName";
    public static final String DEFAULT_LIVE_PATTERN_REPLACE_PREFIX = "false";
    public static final String DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX = "false";

    /** One "amck_patterns" entry. [prefix], [suffix], [task],
     [replace_prefix_str] and [fire_on_suffix_str] are required
     (non-empty, except the two booleans just need to be present and
     valid); [regex] is optional - null (or, equivalently, empty)
     means "any non-empty in-between text matches". [prefix], [regex]
     (when present) and [suffix] are all regex source strings, each
     with its own pre-compiled (MULTILINE) [Pattern] - compiled once
     here rather than in the hot
     [TaskerTriggerEngine.check_expand_patterns] path. [replace_prefix]/
     [fire_on_suffix] are the parsed boolean form of
     [replace_prefix_str]/[fire_on_suffix_str] (both kept around too,
     verbatim, purely so [to_json] can round-trip whatever casing the
     user typed rather than normalizing it to "true"/"false"). */
    public static final class ExpandPattern
    {
        public final String prefix;
        public final Pattern compiled_prefix;
        public final String regex;
        public final Pattern compiled_regex;
        public final String suffix;
        public final Pattern compiled_suffix;
        public final String task;
        public final String replace_prefix_str;
        public final boolean replace_prefix;
        public final String fire_on_suffix_str;
        public final boolean fire_on_suffix;

        public ExpandPattern(String prefix_, Pattern compiled_prefix_, String regex_, Pattern compiled_regex_,
                             String suffix_, Pattern compiled_suffix_, String task_,
                             String replace_prefix_str_, boolean replace_prefix_,
                             String fire_on_suffix_str_, boolean fire_on_suffix_)
        {
            prefix = prefix_;
            compiled_prefix = compiled_prefix_;
            regex = regex_;
            compiled_regex = compiled_regex_;
            suffix = suffix_;
            compiled_suffix = compiled_suffix_;
            task = task_;
            replace_prefix_str = replace_prefix_str_;
            replace_prefix = replace_prefix_;
            fire_on_suffix_str = fire_on_suffix_str_;
            fire_on_suffix = fire_on_suffix_;
        }
    }

    public final String replace_trigger;
    public final String append_trigger;
    public final long timeout_ms;
    /** keyword -> Tasker task name, in declared order. */
    public final LinkedHashMap<String, String> tasks;
    /** In declared order. Never empty - if "amck_patterns" was missing
     or an empty array, contains the two example entries built from
     [DEFAULT_EXPAND_PATTERN_PREFIX]/etc. and
     [DEFAULT_LIVE_PATTERN_PREFIX]/etc. */
    public final List<ExpandPattern> expand_patterns;
    /** True if any of "amck_replace", "amck_append", "amck_timeout" or
     "amck_patterns" were missing/empty in the parsed JSON and a
     default was auto-filled in this result. When true, the caller
     should persist [to_json]'s output back to storage so the stored
     JSON stays in sync with what's actually in effect. */
    public final boolean needs_persist;

    private TaskerAutomationConfig(String replace_trigger_, String append_trigger_,
                                   long timeout_ms_, LinkedHashMap<String, String> tasks_,
                                   List<ExpandPattern> expand_patterns_, boolean needs_persist_)
    {
        replace_trigger = replace_trigger_;
        append_trigger = append_trigger_;
        timeout_ms = timeout_ms_;
        tasks = tasks_;
        expand_patterns = expand_patterns_;
        needs_persist = needs_persist_;
    }

    public static TaskerAutomationConfig parse(String json) throws KeymapJsonUtils.ParseError
    {
        java.util.Set<String> array_field_keys = java.util.Collections.singleton(KEY_EXPAND_PATTERNS);
        // "prefix"/"regex"/"suffix" are all regex source strings now,
        // so all three get lenient parsing - see [parse_json_string] -
        // letting a user write "\s" instead of having to double-escape
        // it to "\\s".
        java.util.Set<String> lenient_value_keys = new java.util.HashSet<>();
        lenient_value_keys.add(KEY_EXPAND_PATTERN_REGEX);
        lenient_value_keys.add("prefix");
        lenient_value_keys.add("suffix");
        KeymapJsonUtils.MultiArrayObjectResult mixed =
                KeymapJsonUtils.parse_object_with_array_fields(json, array_field_keys, lenient_value_keys);

        String replace_trigger = DEFAULT_REPLACE_TRIGGER;
        String append_trigger = DEFAULT_APPEND_TRIGGER;
        long timeout_ms = DEFAULT_TIMEOUT_MS;
        LinkedHashMap<String, String> tasks = new LinkedHashMap<>();
        List<String> dup_keywords = new ArrayList<>();

        boolean used_default_replace = true;
        boolean used_default_append = true;
        boolean used_default_timeout = true;

        for (Map.Entry<String, String> e : mixed.string_entries)
        {
            String key = e.getKey();
            String value = e.getValue();
            if (key.equals(KEY_REPLACE_TRIGGER))
            {
                if (!value.isEmpty())
                {
                    replace_trigger = value;
                    used_default_replace = false;
                }
            }
            else if (key.equals(KEY_APPEND_TRIGGER))
            {
                if (!value.isEmpty())
                {
                    append_trigger = value;
                    used_default_append = false;
                }
            }
            else if (key.equals(KEY_TIMEOUT_MS))
            {
                if (!value.isEmpty())
                {
                    long parsed;
                    try
                    {
                        parsed = Long.parseLong(value.trim());
                    }
                    catch (NumberFormatException nfe)
                    {
                        throw new KeymapJsonUtils.ParseError(
                                "\"" + KEY_TIMEOUT_MS + "\" must be a whole number of milliseconds, got \"" + value + "\"");
                    }
                    if (parsed < MIN_TIMEOUT_MS || parsed > MAX_TIMEOUT_MS)
                        throw new KeymapJsonUtils.ParseError(
                                "\"" + KEY_TIMEOUT_MS + "\" must be between " + MIN_TIMEOUT_MS
                                        + " and " + MAX_TIMEOUT_MS + " (ms), got " + parsed);
                    timeout_ms = parsed;
                    used_default_timeout = false;
                }
            }
            else
            {
                if (tasks.containsKey(key))
                    dup_keywords.add(key);
                tasks.put(key, value);
            }
        }

        if (!dup_keywords.isEmpty())
            throw new KeymapJsonUtils.ParseError("Duplicate keyword: " + dup_keywords.get(0));

        if (replace_trigger.equals(append_trigger))
            throw new KeymapJsonUtils.ParseError("\"" + KEY_REPLACE_TRIGGER + "\" and \"" + KEY_APPEND_TRIGGER + "\" must be different");

        if (tasks.isEmpty())
            throw new KeymapJsonUtils.ParseError("Add at least one task, e.g. \"runtask1\": \"Task 1\"");

        List<ExpandPattern> expand_patterns = new ArrayList<>();
        LinkedHashSet<String> seen_pattern_keys = new LinkedHashSet<>();
        for (List<Map.Entry<String, String>> obj : mixed.array_objects(KEY_EXPAND_PATTERNS))
        {
            String prefix = null, regex = null, suffix = null, task = null;
            String replace_prefix_str = null, fire_on_suffix_str = null;
            for (Map.Entry<String, String> e : obj)
            {
                String key = e.getKey();
                if (key.equals("prefix"))
                    prefix = e.getValue();
                else if (key.equals(KEY_EXPAND_PATTERN_REGEX))
                    regex = e.getValue();
                else if (key.equals("suffix"))
                    suffix = e.getValue();
                else if (key.equals("task"))
                    task = e.getValue();
                else if (key.equals("replace_prefix"))
                    replace_prefix_str = e.getValue();
                else if (key.equals("fire_on_suffix"))
                    fire_on_suffix_str = e.getValue();
                else
                    throw new KeymapJsonUtils.ParseError(
                            "Unknown key \"" + key + "\" in \"" + KEY_EXPAND_PATTERNS
                                    + "\" entry (expected \"prefix\", \"" + KEY_EXPAND_PATTERN_REGEX
                                    + "\" (optional), \"suffix\", \"task\", \"replace_prefix\", \"fire_on_suffix\")");
            }
            if (prefix == null || prefix.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"prefix\"");
            if (suffix == null || suffix.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"suffix\"");
            if (task == null || task.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"task\"");
            if (replace_prefix_str == null || replace_prefix_str.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a \"replace_prefix\" (\"true\" or \"false\")");
            if (fire_on_suffix_str == null || fire_on_suffix_str.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a \"fire_on_suffix\" (\"true\" or \"false\")");
            if (regex != null && regex.isEmpty())
                regex = null; // Empty "regex" is the same as omitting it entirely.

            boolean replace_prefix = parse_bool_field(prefix, "replace_prefix", replace_prefix_str);
            boolean fire_on_suffix = parse_bool_field(prefix, "fire_on_suffix", fire_on_suffix_str);

            Pattern compiled_prefix = compile_pattern_field_regex("prefix", prefix);
            Pattern compiled_regex = regex != null ? compile_pattern_field_regex(KEY_EXPAND_PATTERN_REGEX, regex) : null;
            Pattern compiled_suffix = compile_pattern_field_regex("suffix", suffix);

            String pattern_key = prefix + "\u0000" + suffix;
            if (!seen_pattern_keys.add(pattern_key))
                throw new KeymapJsonUtils.ParseError(
                        "Duplicate \"" + KEY_EXPAND_PATTERNS + "\" entry: prefix \"" + prefix + "\" with suffix \"" + suffix + "\"");

            expand_patterns.add(new ExpandPattern(prefix, compiled_prefix, regex, compiled_regex, suffix, compiled_suffix,
                    task, replace_prefix_str, replace_prefix, fire_on_suffix_str, fire_on_suffix));
        }

        boolean used_default_expand_patterns = false;
        if (expand_patterns.isEmpty())
        {
            expand_patterns.add(new ExpandPattern(
                    DEFAULT_EXPAND_PATTERN_PREFIX, compile_pattern_field_regex("prefix", DEFAULT_EXPAND_PATTERN_PREFIX),
                    null, null,
                    DEFAULT_EXPAND_PATTERN_SUFFIX, compile_pattern_field_regex("suffix", DEFAULT_EXPAND_PATTERN_SUFFIX),
                    DEFAULT_EXPAND_PATTERN_TASK,
                    DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX, Boolean.parseBoolean(DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX),
                    DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX, Boolean.parseBoolean(DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX)));
            expand_patterns.add(new ExpandPattern(
                    DEFAULT_LIVE_PATTERN_PREFIX, compile_pattern_field_regex("prefix", DEFAULT_LIVE_PATTERN_PREFIX),
                    DEFAULT_LIVE_PATTERN_REGEX, compile_pattern_field_regex(KEY_EXPAND_PATTERN_REGEX, DEFAULT_LIVE_PATTERN_REGEX),
                    DEFAULT_LIVE_PATTERN_SUFFIX, compile_pattern_field_regex("suffix", DEFAULT_LIVE_PATTERN_SUFFIX),
                    DEFAULT_LIVE_PATTERN_TASK,
                    DEFAULT_LIVE_PATTERN_REPLACE_PREFIX, Boolean.parseBoolean(DEFAULT_LIVE_PATTERN_REPLACE_PREFIX),
                    DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX, Boolean.parseBoolean(DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX)));
            used_default_expand_patterns = true;
        }

        boolean needs_persist = used_default_replace || used_default_append
                || used_default_timeout || used_default_expand_patterns;

        return new TaskerAutomationConfig(replace_trigger, append_trigger, timeout_ms, tasks,
                expand_patterns, needs_persist);
    }

    /** Parses a "true"/"false" (case-insensitive) per-entry boolean
     field - [field_name] is "replace_prefix" or "fire_on_suffix",
     [prefix] is that entry's own "prefix" value, purely so the error
     message can point at which entry is wrong. */
    private static boolean parse_bool_field(String prefix, String field_name, String value) throws KeymapJsonUtils.ParseError
    {
        if (value.equalsIgnoreCase("true"))
            return true;
        if (value.equalsIgnoreCase("false"))
            return false;
        throw new KeymapJsonUtils.ParseError(
                "\"" + field_name + "\" for prefix \"" + prefix + "\" must be \"true\" or \"false\", got \"" + value + "\"");
    }

    /** Compiles one of an "amck_patterns" entry's regex fields
     ([field_name] is "prefix", "regex" or "suffix", purely for the
     error message) with [Pattern.MULTILINE] - so "^"/"$" in, say, a
     "prefix" of "(\s|^)" also match right after/before a newline, not
     only at the very start/end of the whole field - and turns an
     invalid pattern into a [KeymapJsonUtils.ParseError] instead of
     letting [PatternSyntaxException] escape. */
    private static Pattern compile_pattern_field_regex(String field_name, String pattern_source) throws KeymapJsonUtils.ParseError
    {
        try
        {
            return Pattern.compile(pattern_source, Pattern.MULTILINE);
        }
        catch (PatternSyntaxException pse)
        {
            throw new KeymapJsonUtils.ParseError(
                    "Invalid \"" + field_name + "\" in \"" + KEY_EXPAND_PATTERNS + "\" entry (\"" + pattern_source
                            + "\"): " + pse.getDescription());
        }
    }

    /** Re-serializes this config as beautified (2-space indented) JSON,
     with every field - including any keys that were auto-filled with
     defaults during [parse] - written out explicitly. Callers should
     write this back to storage whenever [needs_persist] is true, so
     the saved file always reflects what's actually in effect. */
    public String to_json()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"").append(KEY_REPLACE_TRIGGER).append("\": \"").append(escape_json(replace_trigger)).append("\",\n");
        sb.append("  \"").append(KEY_APPEND_TRIGGER).append("\": \"").append(escape_json(append_trigger)).append("\",\n");
        sb.append("  \"").append(KEY_TIMEOUT_MS).append("\": \"").append(timeout_ms).append("\",\n");
        for (Map.Entry<String, String> e : tasks.entrySet())
        {
            sb.append("  \"").append(escape_json(e.getKey())).append("\": \"")
                    .append(escape_json(e.getValue())).append("\",\n");
        }
        sb.append("  \"").append(KEY_EXPAND_PATTERNS).append("\": [\n");
        for (int i = 0; i < expand_patterns.size(); i++)
        {
            ExpandPattern p = expand_patterns.get(i);
            sb.append("    {\n");
            sb.append("      \"prefix\": \"").append(escape_json(p.prefix)).append("\",\n");
            if (p.regex != null)
                sb.append("      \"").append(KEY_EXPAND_PATTERN_REGEX).append("\": \"").append(escape_json(p.regex)).append("\",\n");
            sb.append("      \"suffix\": \"").append(escape_json(p.suffix)).append("\",\n");
            sb.append("      \"replace_prefix\": \"").append(escape_json(p.replace_prefix_str)).append("\",\n");
            sb.append("      \"fire_on_suffix\": \"").append(escape_json(p.fire_on_suffix_str)).append("\",\n");
            sb.append("      \"task\": \"").append(escape_json(p.task)).append("\"\n");
            sb.append("    }").append(i < expand_patterns.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escape_json(String s)
    {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20)
                        out.append(String.format("\\u%04x", (int) c));
                    else
                        out.append(c);
            }
        }
        return out.toString();
    }
}
