package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.view.inputmethod.InputConnection;

import com.adiraimaji.customkeyboard.prefs.KeymapManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class KeymapEngine
{
    private static final KeymapEngine INSTANCE = new KeymapEngine();

    public static KeymapEngine get()
    {
        return INSTANCE;
    }

    public interface WordTrackerCallback
    {
        void remove_surrounding_text(int before, int after);
        void typed(String text);
    }

    private final HashMap<String, String> map = new HashMap<>();
    private final HashSet<String> prefixSet = new HashSet<>();
    private final HashSet<String> strictPrefixSet = new HashSet<>();

    private String pendingRaw = "";
    private int pendingOutputLen = 0;

    /** The most recently fully-finalized output text and its committed
     length, used only for the "type the trigger character again to
     toggle short<->long form" feature - see find_reduce_candidate().
     Reset whenever the cursor moves for any reason unrelated to this
     engine's own edits (see reset()). */
    private String lastFinalizedOutput = null;
    private int lastFinalizedOutputLen = 0;

    private String loaded_name = null;
    private boolean allowSwipe = false;

    private int selfEditCount = 0;
    private int lastConsumedSelfEditCount = 0;

    private KeymapEngine()
    {
    }

    public void load(Context context, String keymap_name, boolean allow_swipe)
    {
        pendingRaw = "";
        pendingOutputLen = 0;
        lastFinalizedOutput = null;
        lastFinalizedOutputLen = 0;
        allowSwipe = allow_swipe;

        if (keymap_name != null && keymap_name.equals(loaded_name))
            return;

        map.clear();
        prefixSet.clear();
        strictPrefixSet.clear();
        loaded_name = keymap_name;

        if (keymap_name == null)
            return;

        Keymap keymap = KeymapManager.loadKeymap(context, keymap_name);

        if (keymap == null)
            return;

        Iterator<String> it = keymap.keys();

        while (it.hasNext())
        {
            String key = it.next();
            String value = keymap.lookup(key);

            map.put(key, value);

            for (int len = 1; len <= key.length(); len++)
            {
                String sub = key.substring(0, len);
                prefixSet.add(sub);
                if (len < key.length())
                    strictPrefixSet.add(sub);
            }
        }
    }

    @Deprecated
    public void load(Context context, String keymap_name)
    {
        load(context, keymap_name, false);
    }

    @Deprecated
    public void load(Context context)
    {
        load(context, null, false);
    }

    public boolean process(InputConnection conn, String text,
                           WordTrackerCallback wordTracker, boolean isSwipe)
    {
        if (map.isEmpty())
            return false;
        if (isSwipe && !allowSwipe)
            return false;

        for (int i = 0; i < text.length(); i++)
            handle_char(conn, text.charAt(i), wordTracker);

        return true;
    }

    @Deprecated
    public boolean process(InputConnection conn, String text, WordTrackerCallback wordTracker)
    {
        return process(conn, text, wordTracker, false);
    }

    @Deprecated
    public boolean process(InputConnection conn, String text)
    {
        return process(conn, text, null, false);
    }

    private void handle_char(InputConnection conn, char c, WordTrackerCallback wt)
    {
        // Only relevant right after a full finalize (nothing pending) -
        // an isolated keystroke that repeats/undoes the "extend" pattern
        // used to reach the currently displayed output.
        if (pendingRaw.isEmpty() && lastFinalizedOutput != null)
        {
            ReduceCandidate rc = find_reduce_candidate(lastFinalizedOutput);
            if (rc != null && rc.trigger == c)
            {
                selfEditCount++;
                conn.beginBatchEdit();
                conn.deleteSurroundingText(lastFinalizedOutputLen, 0);
                conn.commitText(rc.output, 1);
                conn.endBatchEdit();
                if (wt != null)
                {
                    wt.remove_surrounding_text(lastFinalizedOutputLen, 0);
                    wt.typed(rc.output);
                }
                lastFinalizedOutput = rc.output;
                lastFinalizedOutputLen = rc.output.length();
                pendingRaw = "";
                pendingOutputLen = 0;
                return;
            }
        }

        String candidate = pendingRaw + c;

        if (map.containsKey(candidate))
        {
            String replacement = map.get(candidate);
            replace_pending(conn, wt, replacement);
            pendingRaw = candidate;
            pendingOutputLen = replacement.length();

            if (!strictPrefixSet.contains(candidate))
            {
                pendingRaw = "";
                pendingOutputLen = 0;
                lastFinalizedOutput = replacement;
                lastFinalizedOutputLen = replacement.length();
            }
            return;
        }

        if (prefixSet.contains(candidate))
        {
            replace_pending(conn, wt, candidate);
            pendingRaw = candidate;
            pendingOutputLen = candidate.length();
            return;
        }

        pendingRaw = "";
        pendingOutputLen = 0;

        String single = String.valueOf(c);

        if (map.containsKey(single))
        {
            String replacement = map.get(single);
            commit(conn, wt, replacement);
            pendingRaw = single;
            pendingOutputLen = replacement.length();
            if (!strictPrefixSet.contains(single))
            {
                pendingRaw = "";
                pendingOutputLen = 0;
                lastFinalizedOutput = replacement;
                lastFinalizedOutputLen = replacement.length();
            }
        }
        else if (prefixSet.contains(single))
        {
            commit(conn, wt, single);
            pendingRaw = single;
            pendingOutputLen = single.length();
        }
        else
        {
            commit(conn, wt, single);
            lastFinalizedOutput = single;
            lastFinalizedOutputLen = single.length();
        }
    }

    private static final class ReduceCandidate
    {
        final String output;
        final char trigger;
        ReduceCandidate(String o, char t) { output = o; trigger = t; }
    }

    /** Searches every key mapped to [current_output] for one whose
     last-character-removed prefix is itself a defined key mapping to
     a DIFFERENT output. Among matches, prefers the one with the
     LONGEST reduced prefix (most specific), so e.g. with both "suu"
     (-> "su") and "sU" (-> "s") mapping to the same output, "su" -
     the more specific reduction - wins over the shorter "s". Returns
     null if no such structural relationship exists. */
    private ReduceCandidate find_reduce_candidate(String current_output)
    {
        String best_base = null;
        String best_output = null;
        char best_trigger = 0;

        for (Map.Entry<String, String> e : map.entrySet())
        {
            String key = e.getKey();
            String value = e.getValue();
            if (!value.equals(current_output) || key.length() < 2)
                continue;

            String base = key.substring(0, key.length() - 1);
            String base_output = map.get(base);
            if (base_output == null || base_output.equals(current_output))
                continue;

            if (best_base == null || base.length() > best_base.length())
            {
                best_base = base;
                best_output = base_output;
                best_trigger = key.charAt(key.length() - 1);
            }
        }

        if (best_base == null)
            return null;
        return new ReduceCandidate(best_output, best_trigger);
    }

    private void replace_pending(InputConnection conn, WordTrackerCallback wt, String new_text)
    {
        selfEditCount++;
        conn.beginBatchEdit();
        if (pendingOutputLen > 0)
            conn.deleteSurroundingText(pendingOutputLen, 0);
        conn.commitText(new_text, 1);
        conn.endBatchEdit();

        if (wt != null)
        {
            if (pendingOutputLen > 0)
                wt.remove_surrounding_text(pendingOutputLen, 0);
            wt.typed(new_text);
        }
    }

    private void commit(InputConnection conn, WordTrackerCallback wt, String text)
    {
        selfEditCount++;
        conn.commitText(text, 1);
        if (wt != null)
            wt.typed(text);
    }

    public boolean consume_self_edit()
    {
        if (selfEditCount != lastConsumedSelfEditCount)
        {
            lastConsumedSelfEditCount = selfEditCount;
            return true;
        }
        return false;
    }

    public void reset()
    {
        pendingRaw = "";
        pendingOutputLen = 0;
        lastFinalizedOutput = null;
        lastFinalizedOutputLen = 0;
        lastConsumedSelfEditCount = selfEditCount;
    }
}