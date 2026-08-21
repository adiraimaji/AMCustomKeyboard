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
 { "prefix": "..", "suffix": " ", "task": "Expand Task 1" },
 { "prefix": "==", "regex": "\\d+.+\\d", "suffix": "\n", "task": "doMath" } // "regex" optional
 ]
 }

 Typing <amck_replace><keyword> (e.g. "##runtask1") sends the entire
 current field's text to the named task and, on completion, replaces
 the ENTIRE field with the task's returned output. Typing
 <amck_append><keyword> (e.g. "@@runtask1") does the same Tasker
 call, but only replaces the typed trigger+keyword itself with the
 output, leaving the rest of the field untouched.

 Each entry in "amck_patterns" describes an open-ended
 expander: typing [prefix], then any non-empty text, then [suffix]
 (e.g. "..5+1 " for prefix ".." / suffix " ") runs [task] with that
 in-between text as %keyword, and replaces just that prefix+content+
 suffix span with the result - same field-editing behaviour as an
 "amck_append" trigger, never the whole field.

 An entry may optionally add "regex": when present, the in-between
 content only counts as a match if it matches [regex] *in full*
 (i.e. as if the pattern were wrapped in ^...$ - a partial/"contains"
 match is not enough). If the content doesn't fully match, this
 entry is treated exactly like the suffix never having been typed at
 all: no task runs, nothing is deleted from the field, and the user
 can keep typing (the check simply runs again on the next
 keystroke). This is what lets "==1+6 \n" ("regex": "\\d+.+\\d")
 correctly NOT fire - the trailing space before the newline means
 the content doesn't end in a digit - while "==1+6\n" does. Since
 the match already excludes any newline inside the content (see
 [TaskerTriggerEngine.check_expand_patterns]), an ordinary regex like
 ".+" naturally also can't span one, without the pattern author
 having to think about newlines at all. An entry with no "regex" (or
 an empty one) keeps the original behaviour: any non-empty content
 between [prefix] and [suffix] matches. An invalid regex is rejected
 at [parse] time with a [KeymapJsonUtils.ParseError], same as any
 other malformed field in this JSON.

 If "amck_replace", "amck_append", "amck_timeout" and/or
 "amck_patterns" are missing (or "amck_patterns" is an
 empty array) from the stored JSON, they are auto-filled with their
 defaults - see [DEFAULT_REPLACE_TRIGGER], [DEFAULT_APPEND_TRIGGER],
 [DEFAULT_TIMEOUT_MS] and [DEFAULT_EXPAND_PATTERN_PREFIX] /
 [DEFAULT_EXPAND_PATTERN_SUFFIX] / [DEFAULT_EXPAND_PATTERN_TASK].
 When that happens [needs_persist] is set to true and the caller
 should write [to_json]'s (beautified) output back to storage. */
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

    /** Default single entry auto-filled into "amck_patterns"
     when that key is missing (or present but empty) from the stored
     JSON. Rename the task in-place to whichever Tasker task you want
     it to run. */
    public static final String DEFAULT_EXPAND_PATTERN_PREFIX = "..";
    public static final String DEFAULT_EXPAND_PATTERN_SUFFIX = " ";
    public static final String DEFAULT_EXPAND_PATTERN_TASK = "ReplaceYourTaskName";

    /** One "amck_patterns" entry. [prefix], [suffix] and [task] are
     required and non-empty (see [TaskerAutomationConfig.parse]).
     [regex] is optional - null (or, equivalently, empty) means "any
     non-empty content matches", same as before this field existed.
     When non-null, [compiled_regex] is the pre-compiled form used to
     test candidate content on every keystroke - compiled once here
     rather than in the hot [TaskerTriggerEngine.check_expand_patterns]
     path. */
    public static final class ExpandPattern
    {
        public final String prefix;
        public final String suffix;
        public final String task;
        public final String regex;
        public final Pattern compiled_regex;

        public ExpandPattern(String prefix_, String suffix_, String task_, String regex_, Pattern compiled_regex_)
        {
            prefix = prefix_;
            suffix = suffix_;
            task = task_;
            regex = regex_;
            compiled_regex = compiled_regex_;
        }
    }

    public final String replace_trigger;
    public final String append_trigger;
    public final long timeout_ms;
    /** keyword -> Tasker task name, in declared order. */
    public final LinkedHashMap<String, String> tasks;
    /** In declared order. Never empty - if "amck_patterns" was
     missing or an empty array, contains [DEFAULT_EXPAND_PATTERN_PREFIX]
     / [DEFAULT_EXPAND_PATTERN_SUFFIX] / [DEFAULT_EXPAND_PATTERN_TASK]. */
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
        KeymapJsonUtils.MixedObjectResult mixed =
                KeymapJsonUtils.parse_object_with_array_field(json, KEY_EXPAND_PATTERNS,
                        java.util.Collections.singleton(KEY_EXPAND_PATTERN_REGEX));

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
        for (List<Map.Entry<String, String>> obj : mixed.array_objects)
        {
            String prefix = null, suffix = null, task = null, regex = null;
            for (Map.Entry<String, String> e : obj)
            {
                String key = e.getKey();
                if (key.equals("prefix"))
                    prefix = e.getValue();
                else if (key.equals("suffix"))
                    suffix = e.getValue();
                else if (key.equals("task"))
                    task = e.getValue();
                else if (key.equals(KEY_EXPAND_PATTERN_REGEX))
                    regex = e.getValue();
                else
                    throw new KeymapJsonUtils.ParseError(
                            "Unknown key \"" + key + "\" in \"" + KEY_EXPAND_PATTERNS
                                    + "\" entry (expected \"prefix\", \"" + KEY_EXPAND_PATTERN_REGEX + "\" (optional), \"suffix\", \"task\")");
            }
            if (prefix == null || prefix.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"prefix\"");
            if (suffix == null || suffix.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"suffix\"");
            if (task == null || task.isEmpty())
                throw new KeymapJsonUtils.ParseError("Each \"" + KEY_EXPAND_PATTERNS + "\" entry needs a non-empty \"task\"");
            if (regex != null && regex.isEmpty())
                regex = null; // Empty "regex" is the same as omitting it entirely.

            Pattern compiled_regex = null;
            if (regex != null)
            {
                try
                {
                    compiled_regex = Pattern.compile(regex);
                }
                catch (PatternSyntaxException pse)
                {
                    throw new KeymapJsonUtils.ParseError(
                            "Invalid \"" + KEY_EXPAND_PATTERN_REGEX + "\" for prefix \"" + prefix + "\" / suffix \"" + suffix
                                    + "\": " + pse.getDescription());
                }
            }

            String pattern_key = prefix + "\u0000" + suffix;
            if (!seen_pattern_keys.add(pattern_key))
                throw new KeymapJsonUtils.ParseError(
                        "Duplicate \"" + KEY_EXPAND_PATTERNS + "\" entry: prefix \"" + prefix + "\" with suffix \"" + suffix + "\"");

            expand_patterns.add(new ExpandPattern(prefix, suffix, task, regex, compiled_regex));
        }

        boolean used_default_expand_patterns = false;
        if (expand_patterns.isEmpty())
        {
            expand_patterns.add(new ExpandPattern(
                    DEFAULT_EXPAND_PATTERN_PREFIX, DEFAULT_EXPAND_PATTERN_SUFFIX, DEFAULT_EXPAND_PATTERN_TASK, null, null));
            used_default_expand_patterns = true;
        }

        boolean needs_persist = used_default_replace || used_default_append
                || used_default_timeout || used_default_expand_patterns;

        return new TaskerAutomationConfig(replace_trigger, append_trigger, timeout_ms, tasks,
                expand_patterns, needs_persist);
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