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
 left alone, as plain text.

 Expand patterns ("amck_patterns"): a second, independent kind
 of trigger for open-ended text, e.g. prefix ".." + suffix " " turns
 "..5+1 " into whatever "Expand Task 1" returns, with "5+1" as
 %keyword. Unlike the fixed dictionary above (a handful of known
 keywords, matched character-by-character as they're typed), the
 content here is arbitrary and unbounded, so it can't be tracked the
 same way. Instead [check_expand_patterns] is called after every
 committed character (forward-typed or backspace) and simply looks at
 the field's actual current text: does it now end with some
 configured suffix, and if so, is there a matching prefix somewhere
 before that (with at least one character of content in between)?
 This needs no dedicated state at all - it's the same
 read-the-actual-field-text philosophy [handle_backspace] already
 uses for typo-correction, just applied per-keystroke rather than
 only on backspace. Firing one always behaves like an "amck_append"
 trigger: only the matched prefix+content+suffix span is replaced,
 the rest of the field is untouched - and reuses the exact same
 one-shot undo as dictionary triggers. */
public class TaskerTriggerEngine
{
    private static final String LOG_TAG = "TaskerTriggerEngine";

    /** Bounds how much text [check_expand_patterns] looks at before the
     cursor on every keystroke - large enough for realistic expand
     content, small enough to keep the per-character InputConnection
     peek cheap. */
    private static final int MAX_EXPAND_SCAN_CHARS = 4000;

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

    /** True while the currently focused field belongs to this app's own
     UI (see [set_paused]) - e.g. the Tasker Automation JSON editor
     itself, or the keymap JSON editor that reuses the same dialog.
     While paused, this engine does nothing at all: no character
     tracking, no expand-pattern scanning, no undo - so typing the
     configured trigger/prefix/suffix characters while editing the
     config JSON (a near-certainty, since JSON itself uses quotes,
     colons, and the very prefix/suffix strings being configured)
     can't misfire a task or eat the user's own typed text. Editing
     that JSON in some other app entirely (not this one) is
     unaffected - triggers stay active there, same as any other
     field. */
    private boolean _paused = false;

    private int _self_edit_count = 0;
    private int _last_consumed_self_edit_count = 0;

    /** Bumped only on a genuine field/app focus change (see
     [new_field_started], called once per [Keyboard2.onStartInputView]
     - NOT on every ordinary keystroke, unlike [reset] below). An
     in-flight [fire]/[fire_expand] captures the value at the moment it
     starts; if it no longer matches by the time the async Tasker
     result comes back, the user has since clicked into another field
     or switched app entirely, and the result must be dropped rather
     than landing in whatever field now happens to be focused -
     [InputConnectionProvider.get] would happily hand back a live,
     non-null InputConnection for that unrelated field, so a null
     check alone can't catch this. */
    private int _session_id = 0;

    /** Boundary used by [check_expand_patterns], in the same
     "characters before the cursor" coordinate system as its own
     [text_before] read: text at or after this offset was typed by the
     user since the last successful expand-pattern fire (or since this
     field was focused); text before it is either pre-existing content
     or the result of that last fire. A candidate prefix match is only
     accepted at or after this offset - see [check_expand_patterns] -
     so a result like "8*3=24" (from firing on prefix "=" suffix "\n")
     can't have its own "=24" reinterpreted as a fresh trigger the next
     time the user presses Enter: only a "=" the user actually types
     *after* the result counts as a new prefix. This is what makes
     "just after the replacement lands, retyping the bare suffix
     should be ignored" work: right after a fire, this offset sits
     past the whole matched span, so re-adding just the suffix (with
     nothing else touched) can never find a prefix at or after it.

     Reset to 0 on a genuine field change (see [_session_id]) -
     deliberately NOT on every [reset], since that fires on
     essentially every ordinary keystroke and clearing it there would
     defeat the whole point. Also shrunk (never grown) down to the
     field's current length at the top of every
     [check_expand_patterns] call, so backspacing all the way through
     a previously-fired result and retyping it from scratch is never
     blocked forever. And restored to its pre-fire value the moment
     that fire's one-shot undo is used (see [try_undo_replacement] /
     [_undo_expand_prev_boundary]) - undoing a fire undoes this side
     effect of it too, so as soon as the user backspaces the restored
     suffix back off again ("resuming" the edit - e.g. fixing the last
     digit before re-typing the suffix) the original prefix is
     immediately eligible again, rather than needing the whole span
     deleted down to nothing first. */
    private int _expand_no_match_before_len = 0;

    /** One-shot undo state for the replacement that was just
     committed - null/0 whenever there is nothing to undo. See
     [try_undo_replacement] and [arm_undo]. */
    private String _undo_before = null;
    private String _undo_after = null;
    private int _undo_replacement_len = 0;

    /** Companion to the one-shot undo state above, set only by
     [fire_expand]'s callback (left at -1 - "not applicable" - by
     [arm_undo] for every other caller, i.e. ordinary dictionary
     triggers). Holds whatever [_expand_no_match_before_len] was
     *before* this particular fire overwrote it. [try_undo_replacement]
     restores it there when this undo is used, so undoing an
     expand-pattern fire reverts its effect on that boundary along
     with the field text itself - see [_expand_no_match_before_len]. */
    private int _undo_expand_prev_boundary = -1;

    private TaskerTriggerEngine() {}

    /** Called from [Keyboard2.onStartInputView] on every field focus
     change, based on whether the newly focused field's package is
     this app's own. See [_paused]. Resets any in-flight tracking
     state either way, since a field focus change always means
     whatever was being tracked in the old field no longer applies. */
    public void set_paused(boolean paused)
    {
        _paused = paused;
        _pending = "";
        clear_undo();
    }

    /** Call once per genuine field/app focus change (from
     [KeyEventHandler.started]) - distinct from [reset], which also
     fires on essentially every ordinary keystroke (see its own doc)
     and must NOT touch [_session_id]/[_expand_no_match_before_len],
     or those would never survive long enough to do their job. */
    public void new_field_started()
    {
        _session_id++;
        _expand_no_match_before_len = 0;
        reset();
    }

    /** Reloads the single stored Tasker Automation config from storage.
     Cheap to call often - picks up edits made in Settings
     immediately, same pattern as KeymapEngine.load(). */
    public void reload(Context ctx)
    {
        _pending = "";
        _session_id++; // Any in-flight call was launched under the old config.
        _expand_no_match_before_len = 0;
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
        if (_paused || _config == null || _full_commands.isEmpty())
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

        if (_paused || _config == null || _prefix_set.isEmpty() || conn == null)
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
        if (_paused || _undo_before == null || conn == null)
            return false;

        final String before = _undo_before;
        final String after = _undo_after;
        final int replacement_len = _undo_replacement_len;
        final int expand_prev_boundary = _undo_expand_prev_boundary;
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

        // This undo just put back the exact text an expand-pattern
        // fire consumed - undo its effect on the match boundary too,
        // so backspacing off the just-restored suffix (to fix a typo
        // and re-trigger) isn't left permanently blocked by a boundary
        // that fire set. See [_expand_no_match_before_len] and
        // [_undo_expand_prev_boundary]. Left untouched (-1) for a
        // dictionary-trigger undo, which never touches that boundary.
        if (expand_prev_boundary >= 0)
            _expand_no_match_before_len = expand_prev_boundary;

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
        // Captured now, before the async call - compared against
        // [_session_id] when the result comes back so a field/app
        // switch in the meantime can be detected. See [_session_id].
        final int session = _session_id;

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
        // What gets sent to the task: text1/text2 are the field's
        // content around the matched trigger+keyword (with the trigger
        // itself removed from text1), never the whole field as one
        // blob - keyword is just "one", never "##one"/"@@one". This is
        // purely about what's sent to Tasker; how the result gets
        // applied back (whole-field replace vs. just the matched span)
        // is unchanged, still driven by [final_is_replace] below.
        final String text1 = text_before.substring(0, text_before.length() - matched.length());
        final String text2 = text_after;

        final int remaining_before_len = text1.length();
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

        TaskerBridge.run_task(ctx, task_name, text1, text2, keyword, _config.timeout_ms, new TaskerBridge.ResultCallback()
        {
            public void result(String output, String error_message)
            {
                if (session != _session_id)
                    return; // Field/app changed while the task was running - see [_session_id].

                InputConnection late_conn = late_conn_provider.get();
                if (late_conn == null)
                    return; // No field focused at all right now - nothing safe to do.

                if (output == null)
                {
                    // Task never sent back a matching reply (it stopped
                    // before reaching Send Intent / the plugin action,
                    // timed out, or Tasker was unreachable). [matched]
                    // - the typed trigger+keyword - was already deleted
                    // from the field before the task ran (see above),
                    // and nothing else has been touched yet regardless
                    // of "append" vs "replace", so putting [matched]
                    // back at the cursor is enough to restore the field
                    // exactly as it was before this trigger fired -
                    // rather than leaving the keyword gone and the
                    // field just sitting empty at that spot.
                    if (error_message != null)
                        android.widget.Toast.makeText(ctx, error_message, android.widget.Toast.LENGTH_SHORT).show();
                    try
                    {
                        _self_edit_count++;
                        late_conn.beginBatchEdit();
                        try
                        {
                            late_conn.commitText(matched, 1);
                        }
                        finally
                        {
                            late_conn.endBatchEdit();
                        }
                    }
                    catch (Exception e)
                    {
                        Log.w(LOG_TAG, "restoring original trigger text failed", e);
                    }
                    return; // Nothing was actually replaced - no undo to arm.
                }

                String text_to_insert = output;

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

    /** Call after EVERY committed character - whether typed forward
     (through this engine's own dictionary-trigger path, KeymapEngine,
     or a plain commit) or removed via backspace (but NOT right after
     [try_undo_replacement] restores text - see the call sites in
     KeyEventHandler). Looks at the field's actual current text and,
     if it now completes some configured "amck_patterns" entry,
     fires it. Safe to call with [conn] null, with no expand patterns
     configured, or if talking to [conn] fails for any reason - always
     just does nothing rather than throwing. */
    public void check_expand_patterns(Context ctx, InputConnection conn,
                                      KeymapEngine.WordTrackerCallback wt,
                                      InputConnectionProvider late_conn_provider)
    {
        if (_paused || _config == null || _config.expand_patterns.isEmpty() || conn == null)
            return;

        String text_before;
        try
        {
            CharSequence before = conn.getTextBeforeCursor(MAX_EXPAND_SCAN_CHARS, 0);
            text_before = (before != null) ? before.toString() : "";
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "getTextBeforeCursor failed in check_expand_patterns", e);
            return;
        }

        // [_expand_no_match_before_len] is an absolute offset that was
        // only ever meant to protect a *previous fire's own result*
        // from being rescanned - it was never meant to permanently
        // wall off that position once the result is edited away. If
        // the field is now shorter than that offset, the user has
        // backspaced into (or through) the protected span, so shrink
        // the boundary down to what's actually left. Without this,
        // deleting a fired result (in full or in part) and retyping
        // the exact same prefix/suffix at that same position would
        // stay blocked forever, since [prefix_idx] there would always
        // read as "before" a boundary that never moved - until the
        // keyboard is closed and reopened (the only other place this
        // offset resets). This mirrors [handle_backspace] re-deriving
        // its own state from the live field text rather than trusting
        // stale bookkeeping.
        if (text_before.length() < _expand_no_match_before_len)
            _expand_no_match_before_len = text_before.length();

        if (text_before.isEmpty())
            return;

        for (TaskerAutomationConfig.ExpandPattern p : _config.expand_patterns)
        {
            if (!text_before.endsWith(p.suffix))
                continue;
            int content_end = text_before.length() - p.suffix.length();
            if (content_end < p.prefix.length())
                continue; // Not even room for prefix + suffix, let alone content.
            int search_from = content_end - p.prefix.length();
            int prefix_idx = text_before.lastIndexOf(p.prefix, search_from);
            if (prefix_idx < 0)
                continue;
            // Don't let the result of a previous fire in this same
            // field be reinterpreted as a fresh prefix - e.g. firing
            // on prefix "=" suffix "\n" against "8*3=24" must not let
            // a later, unrelated Enter press treat the "=24" already
            // sitting there as a new match. Only a prefix the user
            // typed *after* that point counts. See
            // [_expand_no_match_before_len]. Clamped to the current
            // text's length so a boundary from before some
            // intervening backspace can't block matching forever.
            if (prefix_idx < Math.min(_expand_no_match_before_len, text_before.length()))
                continue;
            // The keyword (the content between prefix and suffix) must
            // never contain a newline. If a newline was typed anywhere
            // in between, the prefix found above is stale - the user
            // has moved to a new line, so matching must not reach back
            // across it. Rather than fail the whole pattern, this
            // should behave as if that stale prefix had never been
            // typed: only a prefix typed *after* the newline can still
            // complete this pattern, and since [lastIndexOf] above
            // already returned the closest possible occurrence at or
            // before [search_from], no closer occurrence exists after
            // the newline - so there is nothing left to try here. Note
            // this only concerns a newline *inside* the keyword span;
            // a newline that is itself the configured suffix (already
            // matched above via [endsWith]) is the terminator, not
            // part of the keyword, and content_end already excludes it.
            int prefix_end = prefix_idx + p.prefix.length();
            int newline_in_content = text_before.indexOf('\n', prefix_end);
            if (newline_in_content >= 0 && newline_in_content < content_end)
                continue;
            String content = text_before.substring(prefix_end, content_end);
            if (content.isEmpty())
                continue; // Require non-empty content - otherwise ordinary
            // punctuation like ".. " (an ellipsis before a
            // space) would misfire as an empty-content match.
            String matched_span = text_before.substring(prefix_idx);
            fire_expand(ctx, conn, wt, late_conn_provider, matched_span, content, p.task);
            return; // Only one pattern fires per keystroke.
        }
    }

    private void fire_expand(final Context ctx, InputConnection conn,
                             KeymapEngine.WordTrackerCallback wt,
                             final InputConnectionProvider late_conn_provider,
                             final String matched_span, String content, String task_name)
    {
        // Any character that got us here already ran through
        // [handle_char] first (which clears undo for every character
        // when dictionary triggers are configured) - but expand
        // patterns work even with none configured, so clear it here
        // too rather than assuming that already happened.
        clear_undo();

        // Captured now, before the async call - compared against
        // [_session_id] when the result comes back so a field/app
        // switch (or the keyboard closing) in the meantime can be
        // detected and the result dropped instead of landing wherever
        // is now focused. See [_session_id] and [fire], which this
        // mirrors.
        final int session = _session_id;

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
            Log.w(LOG_TAG, "reading field text failed, aborting expand pattern", e);
            return;
        }

        // Defensive re-check: the field could in principle have
        // changed between [check_expand_patterns]'s scan and here
        // (both run synchronously back-to-back on the same thread, so
        // in practice it can't, but never assume it silently still
        // holds).
        if (!text_before.endsWith(matched_span))
            return;

        final String text1 = text_before.substring(0, text_before.length() - matched_span.length());
        final String text2 = text_after;
        final String keyword = content;

        try
        {
            _self_edit_count++;
            conn.beginBatchEdit();
            try
            {
                conn.deleteSurroundingText(matched_span.length(), 0);
            }
            finally
            {
                conn.endBatchEdit();
            }
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "deleting matched expand pattern failed, aborting", e);
            return;
        }
        if (wt != null)
            wt.remove_surrounding_text(matched_span.length(), 0);

        // Expand patterns always behave like an "amck_append" trigger:
        // only [matched_span] itself is ever touched - see the class
        // doc.
        final String undo_before = matched_span;
        final String undo_after = "";

        TaskerBridge.run_task(ctx, task_name, text1, text2, keyword, _config.timeout_ms,
                new TaskerBridge.ResultCallback()
                {
                    public void result(String output, String error_message)
                    {
                        if (session != _session_id)
                            return; // Field/app changed (or keyboard closed) while the task was running - see [_session_id].

                        InputConnection late_conn = late_conn_provider.get();
                        if (late_conn == null)
                            return; // No field focused at all right now - nothing safe to do.

                        if (output == null)
                        {
                            // Task never sent back a matching reply (timeout,
                            // Tasker unreachable, or it just didn't include the
                            // "text" extra) - [matched_span] was already
                            // removed from the field before the task ran (see
                            // above), so put it right back rather than leaving
                            // the field with the keyword gone and nothing in
                            // its place.
                            if (error_message != null)
                                android.widget.Toast.makeText(ctx, error_message, android.widget.Toast.LENGTH_SHORT).show();
                            try
                            {
                                _self_edit_count++;
                                late_conn.beginBatchEdit();
                                try
                                {
                                    late_conn.commitText(matched_span, 1);
                                }
                                finally
                                {
                                    late_conn.endBatchEdit();
                                }
                            }
                            catch (Exception e)
                            {
                                Log.w(LOG_TAG, "restoring original expand pattern text failed", e);
                            }
                            return; // Nothing was actually replaced - no undo to arm.
                        }

                        String text_to_insert = output;

                        android.widget.Toast.makeText(ctx,
                                "Tasker returned: \"" + text_to_insert + "\"",
                                android.widget.Toast.LENGTH_SHORT).show();

                        try
                        {
                            _self_edit_count++;
                            late_conn.beginBatchEdit();
                            try
                            {
                                late_conn.commitText(text_to_insert, 1);
                            }
                            finally
                            {
                                late_conn.endBatchEdit();
                            }
                        }
                        catch (Exception e)
                        {
                            Log.w(LOG_TAG, "applying Tasker result failed", e);
                            return;
                        }

                        // The field's text before the cursor is now
                        // text1+text_to_insert. Nothing at or before
                        // that point may be treated as a fresh prefix
                        // by a later [check_expand_patterns] scan - in
                        // particular the just-inserted [text_to_insert]
                        // itself, which may well contain characters
                        // that look like a prefix/suffix (e.g. a
                        // doMath task returning "8*3=24" for a "="/"\n"
                        // pattern) but were never typed by the user.
                        // See [_expand_no_match_before_len]. The value
                        // being overwritten here is saved so undoing
                        // this fire can put it back - see
                        // [_undo_expand_prev_boundary].
                        final int prev_boundary = _expand_no_match_before_len;
                        _expand_no_match_before_len = text1.length() + text_to_insert.length();

                        arm_undo(undo_before, undo_after, text_to_insert.length());
                        _undo_expand_prev_boundary = prev_boundary;
                    }
                });
    }

    /** Arms the one-shot undo consumed by [try_undo_replacement]: an
     immediate, untouched next backspace will delete
     [replacement_len] characters before the cursor and put [before]
     (then, for "replace" triggers, [after]) back in its place. Leaves
     [_undo_expand_prev_boundary] at -1 ("not applicable") - only
     [fire_expand]'s callback sets it, right after calling this. */
    private void arm_undo(String before, String after, int replacement_len)
    {
        _undo_before = before;
        _undo_after = after;
        _undo_replacement_len = replacement_len;
        _undo_expand_prev_boundary = -1;
    }

    private void clear_undo()
    {
        _undo_before = null;
        _undo_after = null;
        _undo_replacement_len = 0;
        _undo_expand_prev_boundary = -1;
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