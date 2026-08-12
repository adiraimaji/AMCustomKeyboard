package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

import com.adiraimaji.customkeyboard.prefs.TaskerAutomationManager;

import java.util.HashSet;

/** Watches raw typed characters for a configured trigger+keyword
 sequence (e.g. "##runtask1" or "@@runtask1") and, when one
 completes, sends the entire current field's text to the named
 Tasker task and applies its returned value - either replacing the
 whole field ("replace" trigger) or just the typed trigger+keyword
 itself ("append" trigger).

 Runs with priority OVER KeymapEngine in KeyEventHandler.send_text():
 for as long as the characters typed could still be part of some
 configured trigger+keyword combination, they are committed as
 literal raw text (bypassing transliteration) and buffered here;
 KeymapEngine only ever sees a character once this engine has
 determined it can no longer be part of any valid command. Text
 that never matches a command (the overwhelmingly common case) is
 unaffected - the check for "does this character start any
 configured trigger" is cheap and, for ordinary prose, almost always
 fails immediately. The one deliberate trade-off: text typed right
 after a configured trigger symbol (e.g. immediately after "##") is
 provisionally treated as literal until it either completes a full
 command or diverges from every candidate - so on a keymap-active
 layout, a few characters right after a trigger symbol can
 temporarily skip transliteration even if they don't end up forming
 a real command. This is narrow (scoped to right after the trigger
 symbols only) and necessary, since correctly detecting a literal
 ASCII command requires seeing the raw characters before Keymap
 engine would otherwise transform them.

 One-shot undo: right after a trigger's replacement text lands (see
 [try_undo_replacement]), a single backspace with nothing typed in
 between swaps it back for the original trigger+keyword text
 ("@@one", "##one", ...) instead of deleting a character. Pressing
 backspace again after that behaves like an ordinary backspace -
 which, via the same field-recomputation [handle_backspace] already
 does for typo-correction, naturally lets deleting the last letter
 and retyping it re-fire the command. Typing anything else (a space,
 another letter, moving the cursor) instead of that one immediate
 backspace forfeits the undo - the restored/expanded text is then
 left alone, as plain text. */
public class TaskerTriggerEngine
{
    private static final String LOG_TAG = "TaskerTriggerEngine";

    private static final TaskerTriggerEngine INSTANCE = new TaskerTriggerEngine();

    public static TaskerTriggerEngine get()
    {
        return INSTANCE;
    }

    public interface InputConnectionProvider
    {
        InputConnection get();
    }

    private TaskerAutomationConfig _config = null;

    private final HashSet<String> _prefix_set = new HashSet<>();
    private final HashSet<String> _strict_prefix_set = new HashSet<>();
    private final HashSet<String> _full_commands = new HashSet<>();
    /** Longest full command (trigger+keyword) currently configured -
     bounds how much text [handle_backspace] needs to look at. */
    private int _max_command_len = 0;

    private String _pending = "";

    private int _self_edit_count = 0;
    private int _last_consumed_self_edit_count = 0;

    /** One-shot undo state for the replacement that was just
     committed - null/0 whenever there is nothing to undo. See
     [try_undo_replacement] and [arm_undo]. */
    private String _undo_before = null;
    private String _undo_after = null;
    private int _undo_replacement_len = 0;

    private TaskerTriggerEngine() {}

    /** Reloads the single stored Tasker Automation config from storage.
     Cheap to call often - picks up edits made in Settings
     immediately, same pattern as KeymapEngine.load(). */
    public void reload(Context ctx)
    {
        _pending = "";
        _prefix_set.clear();
        _strict_prefix_set.clear();
        _full_commands.clear();
        _max_command_len = 0;
        _config = null;
        clear_undo();

        String json = TaskerAutomationManager.load(ctx);
        if (json == null)
            return;

        try
        {
            _config = TaskerAutomationConfig.parse(json);
        }
        catch (Exception e)
        {
            return; // Invalid config saved somehow - behave as if unset.
        }

        for (String keyword : _config.tasks.keySet())
        {
            add_full_command(_config.replace_trigger + keyword);
            add_full_command(_config.append_trigger + keyword);
        }
    }

    private void add_full_command(String full)
    {
        _full_commands.add(full);
        _max_command_len = Math.max(_max_command_len, full.length());
        for (int len = 1; len <= full.length(); len++)
        {
            String sub = full.substring(0, len);
            _prefix_set.add(sub);
            if (len < full.length())
                _strict_prefix_set.add(sub);
        }
    }

    /** Returns true if this engine claims [c] - the caller should skip
     its own normal handling (KeymapEngine / plain commit) when
     true. */
    public boolean handle_char(Context ctx, InputConnection conn, char c,
                               KeymapEngine.WordTrackerCallback wt,
                               InputConnectionProvider late_conn_provider)
    {
        if (_config == null || _full_commands.isEmpty())
            return false;

        // Any character typed - whether or not this engine ends up
        // claiming it - means the user has moved on from a
        // just-completed replacement, so the one-shot undo is no
        // longer offered on some later, unrelated backspace.
        clear_undo();

        String candidate = _pending + c;

        if (_prefix_set.contains(candidate))
        {
            if (!commit_literal(conn, wt, c))
                return false;
            _pending = candidate;

            if (_full_commands.contains(_pending) && !_strict_prefix_set.contains(_pending))
                fire(ctx, conn, wt, late_conn_provider);

            return true;
        }

        // Doesn't extend the current buffer. Already-committed characters
        // stay as literal text (correctly - they never completed a
        // command). Try a fresh start with just this character.
        _pending = "";
        String single = String.valueOf(c);
        if (_prefix_set.contains(single))
        {
            if (!commit_literal(conn, wt, c))
                return false;
            _pending = single;
            if (_full_commands.contains(_pending) && !_strict_prefix_set.contains(_pending))
                fire(ctx, conn, wt, late_conn_provider);
            return true;
        }

        return false;
    }

    /** Call BEFORE sending a single-character backspace (KEYCODE_DEL) to
     the field. Rather than just popping one character off [_pending]
     (which only helps while [_pending] itself is still tracking
     something - it's already empty by the time a full mismatch, e.g.
     typing "##ond" for keyword "one", has happened, since
     [handle_char] gives up on the whole buffer the moment a character
     fails to extend it, even though the still-good prefix "##on"
     remains sitting right there in the field), this re-derives
     [_pending] from the field's actual current text: it looks at
     what the text immediately before the cursor will read once this
     one character is deleted, and resumes tracking the longest
     trailing prefix of that text which still matches some configured
     trigger+keyword - so correcting a typo (e.g. "##ond", backspace,
     "e" -> "##one") completes the command instead of leaving the
     engine unable to recognise text that's plainly sitting right
     there. Also marks the resulting selection change as
     self-inflicted so [selection_updated] doesn't undo this by
     calling [reset] right after. Safe to call with [conn] null or
     with no trigger configured - becomes a no-op reset. */
    public void handle_backspace(InputConnection conn)
    {
        _self_edit_count++;
        clear_undo();

        if (_config == null || _prefix_set.isEmpty() || conn == null)
        {
            _pending = "";
            return;
        }

        String text_before;
        try
        {
            CharSequence before = conn.getTextBeforeCursor(_max_command_len, 0);
            text_before = (before != null) ? before.toString() : "";
        }
        catch (Exception e)
        {
            // Some editors' InputConnection implementations can throw
            // here (seen with certain apps/WebViews) instead of just
            // returning null/clamping like the contract says. Don't
            // let that take the whole keyboard down - fall back to
            // "nothing to track" and let the plain backspace proceed.
            Log.w(LOG_TAG, "getTextBeforeCursor failed", e);
            _pending = "";
            return;
        }

        if (text_before.length() == 0)
        {
            _pending = "";
            return;
        }

        // Simulate deleting exactly the one character backspace is
        // about to remove, then find the longest trailing prefix of
        // what's left that's still a candidate for some command.
        String after_delete = text_before.substring(0, text_before.length() - 1);
        String new_pending = "";
        int max_len = Math.min(after_delete.length(), _max_command_len);
        for (int len = max_len; len >= 1; len--)
        {
            String suffix = after_delete.substring(after_delete.length() - len);
            if (_prefix_set.contains(suffix))
            {
                new_pending = suffix;
                break;
            }
        }
        _pending = new_pending;
    }

    /** If a trigger's replacement text was committed immediately
     before this call (and nothing else has happened since - see
     [clear_undo]), swaps it back for the original typed
     trigger+keyword ("@@one", "##one", ...) and returns true: the
     caller should treat this backspace as fully handled and NOT also
     send a literal KEYCODE_DEL. Returns false with no effect
     otherwise, in which case the caller should fall back to its
     normal backspace handling. Safe to call with [conn] null or with
     nothing armed. */
    public boolean try_undo_replacement(InputConnection conn, KeymapEngine.WordTrackerCallback wt)
    {
        if (_undo_before == null || conn == null)
            return false;

        final String before = _undo_before;
        final String after = _undo_after;
        final int replacement_len = _undo_replacement_len;
        clear_undo();

        _self_edit_count++;
        try
        {
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(replacement_len, 0);
                // Commit the restored text in up to two calls so the
                // cursor lands back exactly between [before] and
                // [after] (a single commitText can only place the
                // cursor at the very start or end of what it inserts).
                conn.commitText(before, 1);
                if (after != null && after.length() > 0)
                    conn.commitText(after, 0);
            }
            finally
            {
                conn.endBatchEdit();
            }
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "try_undo_replacement failed", e);
            return true; // Already consumed this backspace either way.
        }

        if (wt != null)
        {
            wt.remove_surrounding_text(replacement_len, 0);
            wt.typed(after != null && after.length() > 0 ? before + after : before);
        }
        return true;
    }

    private boolean commit_literal(InputConnection conn, KeymapEngine.WordTrackerCallback wt, char c)
    {
        _self_edit_count++;
        try
        {
            conn.commitText(String.valueOf(c), 1);
        }
        catch (Exception e)
        {
            // Never let a misbehaving target editor crash the whole
            // keyboard over a single character. Drop this engine's own
            // tracking of it and let the caller fall back to its usual
            // (separately-guarded) commit path.
            Log.w(LOG_TAG, "commitText failed", e);
            reset();
            return false;
        }
        if (wt != null)
            wt.typed(String.valueOf(c));
        return true;
    }

    private void fire(final Context ctx, InputConnection conn,
                      KeymapEngine.WordTrackerCallback wt,
                      final InputConnectionProvider late_conn_provider)
    {
        final String matched = _pending;
        _pending = "";

        boolean is_replace = matched.startsWith(_config.replace_trigger);
        String trigger = is_replace ? _config.replace_trigger : _config.append_trigger;
        String keyword = matched.substring(trigger.length());
        final String task_name = _config.tasks.get(keyword);
        final boolean final_is_replace = is_replace;

        if (task_name == null) // Defensive only - shouldn't happen.
            return;

        final int MAX_FIELD_CHARS = 20000;
        final String text_before;
        final String text_after;
        try
        {
            CharSequence before = conn.getTextBeforeCursor(MAX_FIELD_CHARS, 0);
            CharSequence after = conn.getTextAfterCursor(MAX_FIELD_CHARS, 0);
            text_before = (before != null) ? before.toString() : "";
            text_after = (after != null) ? after.toString() : "";
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "reading field text failed, aborting trigger", e);
            return;
        }
        final String full_field_text = text_before + text_after;

        final int remaining_before_len = Math.max(0, text_before.length() - matched.length());
        final int remaining_after_len = text_after.length();
        // What "undo" should restore the field to if the async result
        // is later reversed with a single backspace - see [arm_undo].
        // "append" only ever removed [matched] itself (everything else
        // around it, including [text_after], was never touched), so
        // undoing it only needs to put [matched] back. "replace" wipes
        // the whole field, so undoing it needs the full original
        // [text_before]/[text_after] (captured above, before anything
        // was deleted) - [text_before] already ends with [matched].
        final String undo_before = final_is_replace ? text_before : matched;
        final String undo_after = final_is_replace ? text_after : "";

        try
        {
            _self_edit_count++;
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(matched.length(), 0);
            }
            finally
            {
                conn.endBatchEdit();
            }
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "deleting matched command failed, aborting trigger", e);
            return;
        }
        if (wt != null)
            wt.remove_surrounding_text(matched.length(), 0);

        TaskerBridge.run_task(ctx, task_name, full_field_text, _config.timeout_ms, new TaskerBridge.ResultCallback()
        {
            public void result(String output, String error_message)
            {
                InputConnection late_conn = late_conn_provider.get();
                if (late_conn == null)
                    return; // Session moved on (field/app changed) - nothing safe to do.

                if (error_message != null)
                {
                    android.widget.Toast.makeText(ctx, error_message, android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                String text_to_insert = (output != null) ? output : "";

                android.widget.Toast.makeText(ctx,
                    "Tasker returned: \"" + text_to_insert + "\"",
                    android.widget.Toast.LENGTH_SHORT).show();

                try
                {
                    _self_edit_count++;
                    late_conn.beginBatchEdit();
                    try
                    {
                        if (final_is_replace)
                            late_conn.deleteSurroundingText(remaining_before_len, remaining_after_len);
                        late_conn.commitText(text_to_insert, 1);
                    }
                    finally
                    {
                        late_conn.endBatchEdit();
                    }
                }
                catch (Exception e)
                {
                    // The field may have changed shape (or app) while
                    // Tasker was running. Nothing safe left to do -
                    // just drop it rather than crash.
                    Log.w(LOG_TAG, "applying Tasker result failed", e);
                    return;
                }

                arm_undo(undo_before, undo_after, text_to_insert.length());
            }
        });
    }

    /** Arms the one-shot undo consumed by [try_undo_replacement]: an
     immediate, untouched next backspace will delete
     [replacement_len] characters before the cursor and put [before]
     (then, for "replace" triggers, [after]) back in its place. */
    private void arm_undo(String before, String after, int replacement_len)
    {
        _undo_before = before;
        _undo_after = after;
        _undo_replacement_len = replacement_len;
    }

    private void clear_undo()
    {
        _undo_before = null;
        _undo_after = null;
        _undo_replacement_len = 0;
    }

    public boolean consume_self_edit()
    {
        if (_self_edit_count != _last_consumed_self_edit_count)
        {
            _last_consumed_self_edit_count = _self_edit_count;
            return true;
        }
        return false;
    }

    public void reset()
    {
        _pending = "";
        _last_consumed_self_edit_count = _self_edit_count;
        clear_undo();
    }
}
