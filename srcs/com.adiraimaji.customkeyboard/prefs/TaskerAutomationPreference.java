package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.adiraimaji.customkeyboard.CustomLayoutEditDialog;
import com.adiraimaji.customkeyboard.KeymapJsonUtils;
import com.adiraimaji.customkeyboard.R;
import com.adiraimaji.customkeyboard.TaskerAutomationConfig;
import com.adiraimaji.customkeyboard.TaskerTriggerEngine;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Settings row for the single, app-wide Tasker Automation JSON - not a
 list like keymaps, there is only ever one. Reuses the same
 line-numbered JSON editor dialog used elsewhere, with inline
 validation, and reloads TaskerTriggerEngine immediately on save so
 the change takes effect without needing to reopen the keyboard. */
public class TaskerAutomationPreference extends Preference
{
    public TaskerAutomationPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setPersistent(false);
        refresh_summary();
    }

    public void refresh_summary()
    {
        String json = TaskerAutomationManager.load(getContext());
        int count = 0;
        if (json != null)
        {
            try { count = TaskerAutomationConfig.parse(json).tasks.size(); }
            catch (Exception e) { /* leave count at 0 */ }
        }
        setSummary(count > 0
                ? getContext().getString(R.string.pref_tasker_automation_summary_configured, count)
                : getContext().getString(R.string.pref_tasker_automation_summary_empty));
    }

    /** Text shown when the dialog opens: the stored JSON with any of
     the 3 "amck_" keys that are missing filled in with their default
     value (so the user always sees, and can directly edit, all 3 -
     rather than having to know the defaults exist and type the key
     name themselves to override one), and with "amck_patterns"
     itself auto-filled with one default entry (prefix "..", suffix
     " ", task [TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_TASK])
     when that key is missing entirely - same reasoning: the user
     should see it and edit the task name in place rather than have
     to know the feature exists and type the whole key/array by hand.
     If the key IS present (even as an empty array - that's treated
     as an intentional "no expand patterns configured" and left
     alone), it's kept exactly as saved. If nothing is stored yet, all
     3 plus one placeholder task and one example expand pattern are
     shown. If the stored JSON is currently invalid, it's returned
     as-is (unmodified) so [validate] can show the real parse error
     rather than this method silently rewriting text the user hasn't
     fixed yet. */
    private String initial_json()
    {
        String stored = TaskerAutomationManager.load(getContext());
        if (stored != null)
            return ensure_amck_defaults(stored);
        return "{\n" +
                "  \"amck_append\": \"@@\",\n" +
                "  \"amck_replace\": \"##\",\n" +
                "  \"amck_timeout\": \"15000\",\n" +
                "  \"runtask1\": \"Task 1\",\n" +
                "  \"amck_patterns\": [\n" +
                "    {\n" +
                "      \"prefix\": \"..\",\n" +
                "      \"suffix\": \" \",\n" +
                "      \"task\": \"Expand Task 1\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private static String ensure_amck_defaults(String stored)
    {
        KeymapJsonUtils.MixedObjectResult mixed;
        try
        {
            mixed = KeymapJsonUtils.parse_object_with_array_field(stored, TaskerAutomationConfig.KEY_EXPAND_PATTERNS,
                    java.util.Collections.singleton(TaskerAutomationConfig.KEY_EXPAND_PATTERN_REGEX));
        }
        catch (Exception e)
        {
            return stored; // Invalid JSON - leave untouched, let validate() explain why.
        }

        boolean has_replace = false, has_append = false, has_timeout = false;
        for (Map.Entry<String, String> e : mixed.string_entries)
        {
            if (e.getKey().equals(TaskerAutomationConfig.KEY_REPLACE_TRIGGER))
                has_replace = true;
            else if (e.getKey().equals(TaskerAutomationConfig.KEY_APPEND_TRIGGER))
                has_append = true;
            else if (e.getKey().equals(TaskerAutomationConfig.KEY_TIMEOUT_MS))
                has_timeout = true;
        }

        // Only an entirely-missing key gets a default injected - an
        // explicit empty array ("amck_patterns": []) is treated
        // as the user intentionally clearing it, and left as-is.
        boolean needs_default_expand_patterns = !mixed.array_field_present;

        if (has_replace && has_append && has_timeout && !needs_default_expand_patterns)
            return stored; // Nothing missing - keep exactly as saved.

        List<List<Map.Entry<String, String>>> array_objects = mixed.array_objects;
        if (needs_default_expand_patterns)
        {
            List<Map.Entry<String, String>> default_entry = new ArrayList<>();
            default_entry.add(new AbstractMap.SimpleEntry<>("prefix", TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_PREFIX));
            default_entry.add(new AbstractMap.SimpleEntry<>("suffix", TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_SUFFIX));
            default_entry.add(new AbstractMap.SimpleEntry<>("task", TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_TASK));
            array_objects = new ArrayList<>();
            array_objects.add(default_entry);
        }

        List<String> lines = new ArrayList<>();
        if (!has_replace)
            lines.add(json_line(TaskerAutomationConfig.KEY_REPLACE_TRIGGER, TaskerAutomationConfig.DEFAULT_REPLACE_TRIGGER));
        if (!has_append)
            lines.add(json_line(TaskerAutomationConfig.KEY_APPEND_TRIGGER, TaskerAutomationConfig.DEFAULT_APPEND_TRIGGER));
        if (!has_timeout)
            lines.add(json_line(TaskerAutomationConfig.KEY_TIMEOUT_MS, String.valueOf(TaskerAutomationConfig.DEFAULT_TIMEOUT_MS)));
        for (Map.Entry<String, String> e : mixed.string_entries)
            lines.add(json_line(e.getKey(), e.getValue()));
        if (mixed.array_field_present || needs_default_expand_patterns)
            lines.add(json_array_block(TaskerAutomationConfig.KEY_EXPAND_PATTERNS, array_objects));

        StringBuilder b = new StringBuilder("{\n");
        for (int i = 0; i < lines.size(); i++)
        {
            b.append("  ").append(lines.get(i));
            if (i < lines.size() - 1)
                b.append(",");
            b.append("\n");
        }
        b.append("}");
        return b.toString();
    }

    private static String json_line(String key, String value)
    {
        return "\"" + escape_json_string(key) + "\": \"" + escape_json_string(value) + "\"";
    }

    /** Re-serializes an "amck_patterns"-shaped array field (list
     of flat {"prefix":..,"suffix":..,"task":..} objects) exactly as
     [ensure_amck_defaults] needs to preserve it when it has to
     reconstruct the surrounding JSON to inject missing "amck_"
     defaults elsewhere in the object - or, when
     [ensure_amck_defaults] built a fresh single-entry default list
     because the key was missing entirely, to render that same way.
     The multi-line result is meant to be placed, as-is, as one entry
     in the same [lines] list [ensure_amck_defaults] builds for the
     plain scalar keys - its first line lines up with those at the
     "  " (2-space) indent the caller already adds uniformly. */
    private static String json_array_block(String key, List<List<Map.Entry<String, String>>> array_objects)
    {
        StringBuilder b = new StringBuilder();
        b.append("\"").append(escape_json_string(key)).append("\": [");
        if (array_objects.isEmpty())
        {
            b.append("]");
            return b.toString();
        }
        b.append("\n");
        for (int i = 0; i < array_objects.size(); i++)
        {
            List<Map.Entry<String, String>> obj = array_objects.get(i);
            b.append("    {\n");
            for (int j = 0; j < obj.size(); j++)
            {
                Map.Entry<String, String> e = obj.get(j);
                b.append("      ").append(json_line(e.getKey(), e.getValue()));
                if (j < obj.size() - 1)
                    b.append(",");
                b.append("\n");
            }
            b.append("    }");
            if (i < array_objects.size() - 1)
                b.append(",");
            b.append("\n");
        }
        b.append("  ]");
        return b.toString();
    }

    /** Mirrors KeymapBuilderActivity.escape_json_string() - kept as a
     small local copy rather than shared, since this is the only other
     place in the app that writes (rather than only reads) this flat
     JSON format. */
    private static String escape_json_string(String s)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20)
                        b.append(String.format("\\u%04x", (int)c));
                    else
                        b.append(c);
            }
        }
        return b.toString();
    }

    @Override
    protected void onClick()
    {
        CustomLayoutEditDialog.show(getContext(), initial_json(), false,
                R.string.tasker_dialog_title, 0,
                new CustomLayoutEditDialog.Callback()
                {
                    @Override
                    public void select(String text)
                    {
                        if (text == null)
                            return;
                        TaskerAutomationManager.save(getContext(), text);
                        TaskerTriggerEngine.get().reload(getContext());
                        refresh_summary();
                    }

                    @Override
                    public String validate(String text)
                    {
                        try
                        {
                            TaskerAutomationConfig.parse(text);
                            return null;
                        }
                        catch (KeymapJsonUtils.ParseError e)
                        {
                            return e.getMessage();
                        }
                        catch (Exception e)
                        {
                            // Defensive: any other parsing edge case
                            // shows an inline error instead of
                            // crashing (CustomLayoutEditDialog also
                            // guards this independently).
                            return "Invalid JSON: " + e.getClass().getSimpleName()
                                    + (e.getMessage() != null ? " - " + e.getMessage() : "");
                        }
                    }
                });
    }
}