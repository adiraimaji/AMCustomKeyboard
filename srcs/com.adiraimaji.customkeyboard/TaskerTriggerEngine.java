package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

import com.adiraimaji.customkeyboard.prefs.TaskerAutomationManager;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

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

 Expand patterns ("amck_patterns"): a second, independent kind of
 trigger for open-ended text, built around three regexes ("prefix",
 the optional "regex", and "suffix" - see [TaskerAutomationConfig]'s
 "amck_patterns" doc for the full matching rules and what
 "replace_prefix"/"fire_on_suffix" each control). [check_expand_patterns]
 is called after every committed character (forward-typed or
 backspace) and looks at the field's actual current text: find the
 closest "prefix" match before the cursor, then see whether what
 follows it satisfies a final ("regex" then "suffix", ending exactly
 at the cursor) or - for a "fire_on_suffix": "false" entry only - a
 live (just "regex", no "suffix" yet) match. This needs no dedicated
 state for MATCHING itself - it's the same read-the-actual-field-text
 philosophy [handle_backspace] already uses for typo-correction, just
 applied per-keystroke - but firing (see [fire_expand]) IS tracked,
 two different ways, for two different reasons:

 - [_expand_no_match_before_len]: protects a fire's own OUTPUT from
 being reinterpreted as a fresh prefix - e.g. a doMath-style task
 replying "8*3=24" for prefix "="/suffix "\n" must not let the "="
 inside that reply itself be treated as a new trigger the next time
 Enter is pressed. Advanced (in [fire_expand]'s callback, once a
 reply actually lands and gets applied) to just past the inserted
 output; anything before that position is never eligible as a fresh
 "prefix" match, for ANY entry (shared across all of them, since one
 entry's output could easily contain characters another entry's
 "prefix"/"suffix" would match). See its own doc for the shrink-on-
 backspace and undo-restores-it details - unchanged from before this
 feature grew "regex" prefixes/suffixes and a live-firing mode.

 - Per-entry "closed" tracking ([_expand_closed_prefix_end] /
 [_expand_closed_content]): stops the SAME occurrence from re-firing
 over and over as the user keeps typing forward past an already-
 completed "regex"+"suffix" match (which - unlike the classic
 one-shot case, where the matched text is about to be replaced
 anyway - matters a lot for a "fire_on_suffix": "false" entry, since
 the matched span is left sitting untouched in the field for as long
 as it takes a reply to arrive, so it would otherwise still be
 sitting there, still matching, on every subsequent keystroke).
 Recorded the instant a final match is detected (both fire_on_suffix
 values - it's what makes a classic entry fire only once, too), and
 checked by re-verifying the exact closed text is STILL there,
 unchanged, with something typed after it: if so, that occurrence is
 skipped entirely; if the check fails - because the user backspaced
 into the closed span (or past it) rather than typing forward - it's
 treated as stale and the occurrence is evaluated completely fresh,
 which is what makes backspacing right after "suffix" completes
 immediately resume live firing (or allow a fresh final fire) rather
 than staying permanently closed. This is is entirely separate from,
 and in addition to, [_expand_no_match_before_len] above - they
 protect against two different things and neither can substitute for
 the other.

 Firing itself (see [fire_expand]) never touches the field up front,
 for either fire_on_suffix value: the whole matched span - "prefix"'s
 match, "regex"'s match, and "suffix"'s match once it exists - is left
 exactly as typed while [task] runs, and is only ever replaced once
 (and if) that specific call's reply arrives with non-empty text,
 verified against the field's current text before being applied - so
 for a "fire_on_suffix": "false" entry, with several overlapping calls
 potentially in flight for the same occurrence, whichever reply lands
 FIRST wins and mutates the field; every other call's own attempt to
 apply its reply then correctly finds the field no longer matches what
 it expected and is silently dropped, however much later it eventually
 arrives (even one from before "suffix" matched, replying after the
 occurrence is otherwise already finished). A timeout, an unreachable
 Tasker, or an empty reply all simply leave the field untouched -
 nothing to "restore" because nothing was removed. Reuses the same
 one-shot undo as dictionary triggers. */
public class TaskerTriggerEngine
{
    private static final String LOG_TAG = "TaskerTriggerEngine";

    /** Bounds how much text [check_expand_patterns] looks at before the
     cursor on every keystroke - large enough for realistic expand
     content, small enough to keep the per-character InputConnection
     peek cheap. */
    private static final int MAX_EXPAND_SCAN_CHARS = 4000;

    /** Bounds how many trailing characters of an "amck_patterns"
     occurrence's in-between text [find_suffix_split] tries as a
     candidate "suffix" match - realistic suffixes are a handful of
     characters at most, and without this bound, checking every
     possible split point would re-run "regex" against a
     shrinking-by-one-character prefix of the in-between text for its
     entire length on every keystroke. */
    private static final int MAX_EXPAND_SUFFIX_CHARS = 40;

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

    // How many upcoming selection-change callbacks (see
    // [consume_self_edit]) should still be treated as self-inflicted
    // rather than external. A single-call site like [commit_literal]
    // bumps this by 1 right before its one InputConnection call; a
    // multi-call site that applies a result in one batch (delete, then
    // insert, sometimes a second insert - see [try_undo_replacement],
    // [fire]'s "replace" branch, and [fire_expand]) bumps it
    // once per call it's about to make. This must be a credit count,
    // not a single "last value seen" flag: some editors fire more than
    // one [onUpdateSelection] callback for a single batched edit (one
    // per delete/insert rather than one per beginBatchEdit/endBatchEdit
    // pair), and a "last value" comparison only ever recognises the
    // *first* of those as self, then misreads every following callback
    // from that same batch as an external change and wipes state (e.g.
    // an [arm_undo]'d undo, see [reset]) that was just set up moments
    // earlier - before the user ever gets a chance to act on it.
    private int _self_edit_count = 0;

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

    /** Same length as [_config.expand_patterns], indices matching
     1:1 - see the class doc's "closed" tracking paragraph. -1 (no
     entry) means that pattern isn't currently closed. Rebuilt
     (resized and cleared) in [reload] every time [_config] changes,
     and cleared (same size, values only) in [new_field_started]. */
    private int[] _expand_closed_prefix_end = new int[0];
    /** Companion to [_expand_closed_prefix_end] - the exact
     keyword+suffix text that was closed at that position, so a later
     scan can tell "still closed" (that exact text is still sitting
     there, with something typed after it) apart from "stale, the
     user backspaced into or through it" (re-evaluate fresh). */
    private String[] _expand_closed_content = new String[0];
    /** Same length as [_config.expand_patterns] again - the
     [prefix_end] position a "Running ..." toast was last shown for
     while that "fire_on_suffix": "false" entry's occurrence was still
     live (not yet reached "suffix"), or -1 if none is currently
     active. Lets [check_expand_patterns] show that toast only once
     per occurrence rather than on every one of its repeat fires. A
     stale leftover value is harmless - see [_expand_closed_prefix_end]'s
     sibling doc for why comparing positions for exact equality never
     needs eager invalidation. */
    private int[] _expand_live_toast_prefix_end = new int[0];
    /** Same length as [_config.expand_patterns] again - the
     [prefix_end] position this occurrence's in-between text has most
     recently matched "regex" live at, or -1 if it never has (or hasn't
     since the occurrence last closed). Kept entirely separate from
     [_expand_live_toast_prefix_end] - despite both being set at the
     same moment a live match first succeeds - since they answer two
     different questions ("should I show the toast" vs "should a
     failure now count as 'stopped matching' rather than 'never
     started'") that happen to coincide today but shouldn't be
     conflated. This is the one [check_expand_patterns] actually
     checks before firing the %amck_keyword_stop=true signal below. */
    private int[] _expand_live_matched_prefix_end = new int[0];
    /** Same length as [_config.expand_patterns] again - the
     [prefix_end] position a %amck_keyword_stop=true call has already
     been fired for, since the last time that occurrence's in-between
     text matched "regex", or -1 if no such call is currently
     outstanding. Together with [_expand_live_matched_prefix_end], this
     is what lets [check_expand_patterns] fire that one-off "the word
     I was tracking stopped matching" signal exactly once per failure
     streak: set the instant that signal fires, and cleared again the
     next time this same occurrence's in-between text goes back to
     matching "regex" - so a later failure (after a resumed match) can
     fire it again. Only ever consulted/set for a "fire_on_suffix":
     "false" entry - a "true" one never live-matches "regex" at all,
     so it has nothing to "just stop" matching. */
    private int[] _expand_regex_fail_fired_prefix_end = new int[0];

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
        reset_expand_closed_state();
        reset();
    }

    /** (Re)builds [_expand_closed_prefix_end]/[_expand_closed_content]/
     [_expand_live_toast_prefix_end]/[_expand_live_matched_prefix_end]/
     [_expand_regex_fail_fired_prefix_end], sized to whatever
     [_config.expand_patterns] currently is (0 if [_config] is null),
     with every slot cleared to "nothing closed / no toast shown / never
     matched / no stop signal outstanding". Called both when the config
     itself changes (from [reload], where the size may genuinely differ
     from before) and on every field change (from [new_field_started],
     where the size never actually changes but re-clearing this way is
     simpler than a separate "same size, just reset values" path). */
    private void reset_expand_closed_state()
    {
        int n = (_config != null) ? _config.expand_patterns.size() : 0;
        _expand_closed_prefix_end = new int[n];
        _expand_closed_content = new String[n];
        _expand_live_toast_prefix_end = new int[n];
        _expand_live_matched_prefix_end = new int[n];
        _expand_regex_fail_fired_prefix_end = new int[n];
        java.util.Arrays.fill(_expand_closed_prefix_end, -1);
        java.util.Arrays.fill(_expand_live_toast_prefix_end, -1);
        java.util.Arrays.fill(_expand_live_matched_prefix_end, -1);
        java.util.Arrays.fill(_expand_regex_fail_fired_prefix_end, -1);
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
        {
            reset_expand_closed_state();
            return;
        }

        try
        {
            _config = TaskerAutomationConfig.parse(json);
        }
        catch (Exception e)
        {
            reset_expand_closed_state();
            return; // Invalid config saved somehow - behave as if unset.
        }

        for (String keyword : _config.tasks.keySet())
        {
            add_full_command(_config.replace_trigger + keyword);
            add_full_command(_config.append_trigger + keyword);
        }
        reset_expand_closed_state();
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

        // One credit per InputConnection call about to be made below
        // (delete, then [before], then optionally [after]) - see
        // [_self_edit_count].
        _self_edit_count += (after != null && after.length() > 0) ? 3 : 2;
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
                    // One credit per InputConnection call about to be
                    // made below (the delete only happens for
                    // "replace") - see [_self_edit_count].
                    _self_edit_count += final_is_replace ? 2 : 1;
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
     for each configured "amck_patterns" entry, finds the closest
     "prefix" match before the cursor and fires [fire_expand] whenever
     the class doc's "Expand patterns" rules say to - once only, for a
     "fire_on_suffix": "true" entry; possibly many times (see the
     "closed" tracking in the class doc), for a "false" one. Safe to
     call with [conn] null, with no expand patterns configured, or if
     talking to [conn] fails for any reason - always just does nothing
     rather than throwing. */
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

        // See [_expand_no_match_before_len]'s doc for why this shrinks
        // (never grows) to the field's current length here, every call.
        if (text_before.length() < _expand_no_match_before_len)
            _expand_no_match_before_len = text_before.length();

        if (text_before.isEmpty())
            return;

        int global_boundary = Math.min(_expand_no_match_before_len, text_before.length());

        for (int i = 0; i < _config.expand_patterns.size(); i++)
        {
            TaskerAutomationConfig.ExpandPattern p = _config.expand_patterns.get(i);

            // Collect every "prefix" match not blocked by
            // [global_boundary], as [start, end] pairs, then try them
            // from CLOSEST to the cursor backward - that's virtually
            // always the intended occurrence, since any earlier
            // match's in-between text would have to span all the way
            // through this later, more specific one too, which will
            // essentially never satisfy "regex".
            List<int[]> prefix_matches = new ArrayList<>();
            try
            {
                Matcher pm = p.compiled_prefix.matcher(text_before);
                while (pm.find())
                {
                    if (pm.end() >= global_boundary)
                        prefix_matches.add(new int[]{ pm.start(), pm.end() });
                }
            }
            catch (Exception e)
            {
                Log.w(LOG_TAG, "matching \"prefix\" failed for an amck_patterns entry", e);
                continue;
            }

            boolean acted = false;
            for (int j = prefix_matches.size() - 1; j >= 0 && !acted; j--)
            {
                int prefix_start = prefix_matches.get(j)[0];
                int prefix_end = prefix_matches.get(j)[1];

                // Is this occurrence "closed" (see the class doc)? Only
                // still closed if the EXACT text that closed it is
                // still sitting there unedited, with something typed
                // after it - otherwise (backspaced into or past it)
                // this is stale: fall through and evaluate fresh.
                if (_expand_closed_prefix_end[i] == prefix_end)
                {
                    String closed_content = _expand_closed_content[i];
                    if (text_before.length() > prefix_end + closed_content.length()
                            && text_before.regionMatches(prefix_end, closed_content, 0, closed_content.length()))
                        continue; // Still closed - try an earlier prefix match, if any.
                }

                String content = text_before.substring(prefix_end);

                // A newline anywhere in the in-between text means the
                // user has moved to a new line since [prefix] matched -
                // a match (live or final) can never span one, so this
                // occurrence can no longer complete as-is. It's NOT
                // simply skipped outright the way it used to be, though
                // - see the "stopped matching" branch below, which this
                // now feeds into just like an ordinary regex failure,
                // so typing Enter right after a live match correctly
                // still fires the one-off %amck_keyword_stop signal
                // instead of silently doing nothing.
                int newline_idx = content.indexOf('\n');
                boolean has_newline = newline_idx >= 0;
                // What "regex" last matched before the newline (or all
                // of [content], when there isn't one) - used as
                // %amck_keyword for the "stopped matching" branch below
                // when a newline is what ended this occurrence, since
                // sending a multi-line %amck_keyword there would make
                // little sense.
                String content_before_newline = has_newline ? content.substring(0, newline_idx) : content;

                int split = has_newline ? -1 : find_suffix_split(content, p);
                if (split >= 0)
                {
                    // A full "regex"+"suffix" match, ending exactly at
                    // the cursor. Fires for BOTH values of
                    // "fire_on_suffix" - it's what makes a
                    // "fire_on_suffix": "true" entry fire at all, and
                    // what makes a "false" one fire ONE LAST TIME. Close
                    // this occurrence either way - see the class doc.
                    String prefix_match = text_before.substring(prefix_start, prefix_end);
                    String keyword = content.substring(0, split);
                    String suffix_match = content.substring(split);
                    _expand_closed_prefix_end[i] = prefix_end;
                    _expand_closed_content[i] = keyword + suffix_match;
                    _expand_live_toast_prefix_end[i] = -1; // Finished - nothing left to (re)toast for.
                    _expand_live_matched_prefix_end[i] = -1; // Finished - nothing left to "stop" from here.
                    _expand_regex_fail_fired_prefix_end[i] = -1; // Finished - nothing left to signal a failure for.
                    if (p.fire_on_suffix)
                        android.widget.Toast.makeText(ctx,
                                "Running \"" + p.task + "\"\u2026", android.widget.Toast.LENGTH_SHORT).show();
                    fire_expand(ctx, conn, wt, late_conn_provider,
                            text_before.substring(0, prefix_start), prefix_match, keyword, suffix_match, p, false);
                    acted = true;
                }
                else if (!has_newline && !p.fire_on_suffix && matches_keyword(content, p))
                {
                    // Still live - no "suffix" yet, but the in-between
                    // text so far already satisfies "regex" (or is just
                    // non-empty, if "regex" is omitted). Only entries
                    // with "fire_on_suffix": "false" ever take this
                    // branch - a "true" entry stays silent until
                    // "suffix" completes a match above. Toast only the
                    // first time THIS occurrence (same [prefix_end])
                    // starts qualifying, not on every repeat keystroke.
                    if (_expand_live_toast_prefix_end[i] != prefix_end)
                    {
                        _expand_live_toast_prefix_end[i] = prefix_end;
                        android.widget.Toast.makeText(ctx,
                                "Running \"" + p.task + "\"\u2026", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    // Recorded as "has matched live" for the "stopped
                    // matching" branch below, and any earlier failure
                    // signal for this occurrence no longer applies -
                    // "regex" matches again (even if it never stopped),
                    // so a future failure should be free to signal once
                    // more.
                    _expand_live_matched_prefix_end[i] = prefix_end;
                    _expand_regex_fail_fired_prefix_end[i] = -1;
                    String prefix_match = text_before.substring(prefix_start, prefix_end);
                    fire_expand(ctx, conn, wt, late_conn_provider,
                            text_before.substring(0, prefix_start), prefix_match, content, null, p, false);
                    acted = true;
                }
                else if (!p.fire_on_suffix && _expand_live_matched_prefix_end[i] == prefix_end
                        && _expand_regex_fail_fired_prefix_end[i] != prefix_end)
                {
                    // This occurrence has matched "regex" live at least
                    // once before (see [_expand_live_matched_prefix_end]),
                    // but no longer does right now - either the
                    // in-between text stopped satisfying "regex" (most
                    // often: it got shorter, e.g. via backspace), or a
                    // newline just ended it outright - and no "suffix"
                    // has matched either. Either way, "regex" just
                    // stopped being satisfiable. Fire [task] ONE more
                    // time, with %amck_keyword_stop=true, purely so it
                    // can react (e.g. dismiss a popup it showed) - then
                    // don't fire again for this same failure streak; a
                    // later keystroke that goes back to matching
                    // "regex" resets this above, so a further failure
                    // after that can signal again.
                    _expand_regex_fail_fired_prefix_end[i] = prefix_end;
                    String prefix_match = text_before.substring(prefix_start, prefix_end);
                    fire_expand(ctx, conn, wt, late_conn_provider,
                            text_before.substring(0, prefix_start), prefix_match, content_before_newline, null, p, true);
                    acted = true;
                }
                // Otherwise: too little typed yet, or content never
                // matched at all - try an earlier prefix match, if any.
            }

            if (acted)
                return; // Only one entry acts per keystroke.
        }
    }

    /** Whether [content] counts as a valid keyword on its own - used
     both by [find_suffix_split] (for the part before a candidate
     "suffix" match) and directly for a live ("fire_on_suffix": "false",
     no "suffix" yet) check: non-empty is always required, and, when
     [p.regex] is configured, [content] must match it in full (i.e. as
     if wrapped in ^...$ - a partial/"contains" match is not enough).
     With no "regex" configured, any non-empty [content] qualifies. */
    private boolean matches_keyword(String content, TaskerAutomationConfig.ExpandPattern p)
    {
        if (content.isEmpty())
            return false;
        if (p.compiled_regex != null)
            return p.compiled_regex.matcher(content).matches();
        return true;
    }

    /** Finds where, if anywhere, [content] (the text right after a
     "prefix" match, up to the cursor) splits into a part fully
     matching "regex" (see [matches_keyword]) immediately followed by a
     part fully matching [p]'s "suffix", with the split landing exactly
     at the end of [content] (i.e. at the cursor). Tried longest-
     keyword-first, so the shortest possible suffix wins if more than
     one split would work. Returns the split index (the keyword's
     length), or -1 if no valid split exists. [MAX_EXPAND_SUFFIX_CHARS]
     bounds this to realistic suffix lengths rather than re-testing
     "regex" against a shrinking prefix of [content] for its entire
     (possibly thousands of characters) length on every keystroke. */
    private int find_suffix_split(String content, TaskerAutomationConfig.ExpandPattern p)
    {
        int min_k = Math.max(0, content.length() - MAX_EXPAND_SUFFIX_CHARS);
        for (int k = content.length(); k >= min_k; k--)
        {
            if (!p.compiled_suffix.matcher(content.substring(k)).matches())
                continue;
            if (!matches_keyword(content.substring(0, k), p))
                continue;
            return k;
        }
        return -1;
    }

    /** Fires [p.task] for one "amck_patterns" occurrence. For a
     "fire_on_suffix": "true" entry this is a one-shot call - the only
     one this occurrence will ever make. For a "false" entry it may be
     called potentially many times for the very same occurrence, once
     per qualifying keystroke (see [check_expand_patterns]), the LAST
     of which has [suffix_match] non-null (a "true" entry's one and
     only call always does, by definition). Nothing is deleted or
     otherwise touched here - [prefix_match]+[keyword]+[suffix_match]
     (as they are at THIS particular call) are left exactly as typed
     in the field. Only if (and whenever) this specific call's reply
     actually arrives with non-empty text does the callback below
     touch the field at all - and even then, only if that span is
     still sitting there untouched (see the
     [current_text_before.startsWith] check): since several
     overlapping calls can be in flight for the same "false"-entry
     occurrence, whichever one's reply lands FIRST wins and mutates
     the field; every other call's check then correctly fails (the
     field it was expecting is gone, replaced by the winner's output)
     and its reply is silently dropped, however much later it
     eventually arrives.

     [prefix_match] is the actual text "prefix" matched (often, but
     not always, non-empty - e.g. "" for a zero-width "^" match).
     [keyword] is the actual text "regex" (or, with none configured,
     whatever non-empty in-between text) matched or, when
     [keyword_stop] is true, whatever in-between text most recently
     FAILED to match it. [suffix_match] is null on every "still live"
     or "just stopped matching" call (nothing to send as %amck_suffix
     yet, and nothing but [keyword] is ever eligible to be replaced);
     once "suffix" has matched, it's the actual text "suffix" matched -
     sent as %amck_suffix, and included (along with [keyword]) in
     whatever gets replaced. [p.replace_prefix] additionally decides
     whether [prefix_match] itself is ALSO part of what gets replaced,
     on top of [keyword] (+ [suffix_match] if present) - see the class
     doc. [keyword_stop] is true only for the one-off call fired the
     instant a "fire_on_suffix": "false" entry's in-between text stops
     matching "regex" (before "suffix" ever matched) - see
     [check_expand_patterns] - sent as %amck_keyword_stop so the task
     can react (e.g. dismiss a popup), otherwise left unset; a reply
     to this call is applied exactly like any other, on the off chance
     the task still has something useful to say. */
    private void fire_expand(final Context ctx, InputConnection conn,
                             final KeymapEngine.WordTrackerCallback wt,
                             final InputConnectionProvider late_conn_provider,
                             final String text1, final String prefix_match, String keyword,
                             final String suffix_match, final TaskerAutomationConfig.ExpandPattern p,
                             final boolean keyword_stop)
    {
        // Every fire clears any earlier one-shot undo, same reasoning
        // as dictionary triggers - the user has moved on. Since a
        // "fire_on_suffix": "false" occurrence fires many times, this
        // simply runs again on every one of them.
        clear_undo();

        final int session = _session_id;
        // What's actually eligible to ever be replaced - [prefix_match]
        // is tacked on the front only when [p.replace_prefix] says so;
        // otherwise it's left out entirely; it's never touched at all
        // either way (see [text1]/[expected_prefix] below, which always
        // includes it regardless, since it's still sitting in the
        // field either way - only whether it's part of the DELETE
        // differs).
        final String replaceable_span = (p.replace_prefix ? prefix_match : "") + keyword + (suffix_match != null ? suffix_match : "");
        final String keyword_final = keyword;

        final int MAX_FIELD_CHARS = 20000;
        final String text2;
        try
        {
            CharSequence after = conn.getTextAfterCursor(MAX_FIELD_CHARS, 0);
            text2 = (after != null) ? after.toString() : "";
        }
        catch (Exception e)
        {
            Log.w(LOG_TAG, "reading field text failed, aborting expand pattern", e);
            return;
        }

        TaskerBridge.run_task(ctx, p.task, text1, text2, prefix_match, keyword_final, suffix_match, keyword_stop, _config.timeout_ms,
                new TaskerBridge.ResultCallback()
                {
                    public void result(String output, String error_message)
                    {
                        if (session != _session_id)
                            return; // Field/app changed (or keyboard closed) while the task was running - see [_session_id].

                        if (output == null || output.isEmpty())
                        {
                            // No usable reply for THIS call - timeout,
                            // Tasker unreachable, or it just didn't send
                            // a matching "text" extra. Nothing was ever
                            // touched by this call (see this method's
                            // doc), so there is nothing to restore -
                            // leave the field exactly as it is; some
                            // other in-flight call for this same
                            // occurrence may still succeed later.
                            if (error_message != null)
                                android.widget.Toast.makeText(ctx, error_message, android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }

                        InputConnection late_conn = late_conn_provider.get();
                        if (late_conn == null)
                            return; // No field focused at all right now - nothing safe to do.

                        String current_text_before;
                        try
                        {
                            CharSequence cur = late_conn.getTextBeforeCursor(MAX_FIELD_CHARS, 0);
                            current_text_before = (cur != null) ? cur.toString() : "";
                        }
                        catch (Exception e)
                        {
                            Log.w(LOG_TAG, "reading field text failed, aborting expand pattern result", e);
                            return;
                        }

                        // Only apply if [text1]+[prefix_match]+[keyword]
                        // (+[suffix_match], if this call had one) - this
                        // particular call's span, exactly as it was
                        // when THIS call started - is still sitting
                        // there untouched. Whatever follows it now
                        // (grown, shrunk, or even already past "suffix")
                        // is [trailing], carried through untouched right
                        // after the replacement. If some OTHER call for
                        // this same occurrence already won this race and
                        // mutated the field first, this check correctly
                        // fails here and the reply is simply dropped -
                        // see this method's doc.
                        String matched_span = prefix_match + keyword_final + (suffix_match != null ? suffix_match : "");
                        String expected_prefix = text1 + matched_span;
                        if (!current_text_before.startsWith(expected_prefix))
                            return;

                        final String trailing = current_text_before.substring(expected_prefix.length());
                        // Only [replaceable_span] (see this method's
                        // doc - [keyword](+[suffix_match]), plus
                        // [prefix_match] too if [p.replace_prefix]) is
                        // ever deleted - when [p.replace_prefix] is
                        // false, [prefix_match] sits between [text1] and
                        // [replaceable_span] and is left completely
                        // untouched, which is what stops a leading
                        // separator like a space from being swallowed
                        // along with the replacement.
                        final int delete_len = replaceable_span.length() + trailing.length();

                        android.widget.Toast.makeText(ctx,
                                "Tasker returned: \"" + output + "\"",
                                android.widget.Toast.LENGTH_SHORT).show();

                        try
                        {
                            // One credit per InputConnection call about
                            // to be made below (delete, insert output,
                            // optionally insert trailing) - see
                            // [_self_edit_count].
                            _self_edit_count += trailing.isEmpty() ? 2 : 3;
                            late_conn.beginBatchEdit();
                            try
                            {
                                late_conn.deleteSurroundingText(delete_len, 0);
                                late_conn.commitText(output, 1);
                                // newCursorPosition=0 here (not 1) is
                                // deliberate: it leaves the cursor right
                                // after [output], not at the very end
                                // past [trailing], which is what lets an
                                // immediate backspace undo just the
                                // replacement itself (see [arm_undo]
                                // below) even when the user kept typing
                                // after this occurrence while the task
                                // was running.
                                if (!trailing.isEmpty())
                                    late_conn.commitText(trailing, 0);
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

                        if (wt != null)
                        {
                            wt.remove_surrounding_text(delete_len, 0);
                            wt.typed(trailing.isEmpty() ? output : output + trailing);
                        }

                        // The field's text before the cursor is now
                        // text1+prefix_match(if kept)+output. Nothing at
                        // or before just-past-[output] may be treated as
                        // a fresh "prefix" match by a later
                        // [check_expand_patterns] scan - in particular
                        // [output] itself, which may well contain
                        // characters that look like a prefix/suffix
                        // (e.g. a doMath task returning "8*3=24" for a
                        // "="/"\n" pattern) but were never typed by the
                        // user. See [_expand_no_match_before_len]. The
                        // value being overwritten here is saved so
                        // undoing this fire can put it back - see
                        // [_undo_expand_prev_boundary].
                        String text_before_output = text1 + (p.replace_prefix ? "" : prefix_match);
                        final int prev_boundary = _expand_no_match_before_len;
                        _expand_no_match_before_len = text_before_output.length() + output.length();

                        // Only [replaceable_span] itself is ever touched
                        // by the undo either - [trailing] (and, when
                        // [p.replace_prefix] is false, [prefix_match])
                        // sit right where they are throughout, untouched
                        // by either this replacement or its undo.
                        arm_undo(replaceable_span, "", output.length());
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

    /** Whether the selection change currently being reported to
     [KeyEventHandler.selection_updated] should be treated as
     self-inflicted. Consumes (decrements) one pending credit if any
     are available - see [_self_edit_count] - rather than comparing
     against a single "last seen" value, so a batch of several
     InputConnection calls that ends up producing several separate
     callbacks is still correctly recognised as self for every one of
     them, not just the first. */
    public boolean consume_self_edit()
    {
        if (_self_edit_count > 0)
        {
            _self_edit_count--;
            return true;
        }
        return false;
    }

    public void reset()
    {
        _pending = "";
        _self_edit_count = 0;
        clear_undo();
    }
}