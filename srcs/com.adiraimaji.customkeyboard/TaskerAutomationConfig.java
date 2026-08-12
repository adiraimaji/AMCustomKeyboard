package com.adiraimaji.customkeyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed, validated form of the single stored Tasker Automation JSON:
 {
 "amck_replace": "##",     // optional, default "##"
 "amck_append": "@@",      // optional, default "@@"
 "amck_timeout": "15000",  // optional, default "15000" - ms to wait
                            // for a task's result before giving up
 "runtask1": "Task 1",     // any other key -> a Tasker task NAME
 "runtask2": "Task 2"
 }
 Typing <amck_replace><keyword> (e.g. "##runtask1") sends the entire
 current field's text to the named task and, on completion, replaces
 the ENTIRE field with the task's returned output. Typing
 <amck_append><keyword> (e.g. "@@runtask1") does the same Tasker
 call, but only replaces the typed trigger+keyword itself with the
 output, leaving the rest of the field untouched. */
public final class TaskerAutomationConfig
{
    public static final String KEY_REPLACE_TRIGGER = "amck_replace";
    public static final String KEY_APPEND_TRIGGER = "amck_append";
    public static final String KEY_TIMEOUT_MS = "amck_timeout";
    public static final String DEFAULT_REPLACE_TRIGGER = "##";
    public static final String DEFAULT_APPEND_TRIGGER = "@@";
    public static final long DEFAULT_TIMEOUT_MS = 15000;
    /** Guards against a typo'd huge value stalling the keyboard's
     receiver/timeout bookkeeping indefinitely, and against 0/negative
     values that would fire the timeout immediately or never register. */
    public static final long MIN_TIMEOUT_MS = 1000;
    public static final long MAX_TIMEOUT_MS = 120000;

    public final String replace_trigger;
    public final String append_trigger;
    public final long timeout_ms;
    /** keyword -> Tasker task name, in declared order. */
    public final LinkedHashMap<String, String> tasks;

    private TaskerAutomationConfig(String replace_trigger_, String append_trigger_,
                                   long timeout_ms_, LinkedHashMap<String, String> tasks_)
    {
        replace_trigger = replace_trigger_;
        append_trigger = append_trigger_;
        timeout_ms = timeout_ms_;
        tasks = tasks_;
    }

    public static TaskerAutomationConfig parse(String json) throws KeymapJsonUtils.ParseError
    {
        List<Map.Entry<String, String>> raw = KeymapJsonUtils.parse_flat_object(json);

        String replace_trigger = DEFAULT_REPLACE_TRIGGER;
        String append_trigger = DEFAULT_APPEND_TRIGGER;
        long timeout_ms = DEFAULT_TIMEOUT_MS;
        LinkedHashMap<String, String> tasks = new LinkedHashMap<>();
        List<String> dup_keywords = new ArrayList<>();

        for (Map.Entry<String, String> e : raw)
        {
            String key = e.getKey();
            String value = e.getValue();
            if (key.equals(KEY_REPLACE_TRIGGER))
            {
                if (!value.isEmpty())
                    replace_trigger = value;
            }
            else if (key.equals(KEY_APPEND_TRIGGER))
            {
                if (!value.isEmpty())
                    append_trigger = value;
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

        return new TaskerAutomationConfig(replace_trigger, append_trigger, timeout_ms, tasks);
    }
}
