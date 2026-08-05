package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.view.inputmethod.InputConnection;

import com.adiraimaji.customkeyboard.prefs.KeymapManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class KeymapEngine
{
    private static final KeymapEngine INSTANCE = new KeymapEngine();

    public static KeymapEngine get()
    {
        return INSTANCE;
    }

    /** Lets KeymapEngine keep some external word-tracking state (used for
     suggestions) in sync with the edits it makes to the InputConnection,
     the same way KeyEventHandler.replace_surrounding_text() keeps
     CurrentlyTypedWord in sync with autocorrect edits. */
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

    private String loaded_name = null;

    private int selfEditCount = 0;
    private int lastConsumedSelfEditCount = 0;

    private KeymapEngine()
    {
    }

    public void load(Context context, String keymap_name)
    {
        pendingRaw = "";
        pendingOutputLen = 0;

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
    public void load(Context context)
    {
        load(context, null);
    }

    /** [wordTracker] may be null if the caller doesn't need suggestions to
     stay in sync (e.g. no dictionary loaded) - in that case only the
     InputConnection is edited. */
    public boolean process(InputConnection conn, String text, WordTrackerCallback wordTracker)
    {
        if (map.isEmpty())
            return false;

        for (int i = 0; i < text.length(); i++)
            handle_char(conn, text.charAt(i), wordTracker);

        return true;
    }

    @Deprecated
    public boolean process(InputConnection conn, String text)
    {
        return process(conn, text, null);
    }

    private void handle_char(InputConnection conn, char c, WordTrackerCallback wt)
    {
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

        // Can't extend further. Start fresh with this character alone.
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
        }
    }

    /** Deletes whatever was previously committed for the pending match (if
     any) and commits [new_text] instead, mirroring the same delete+commit
     onto [wordTracker] so suggestion tracking stays in sync with the
     actual editor text. */
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
        lastConsumedSelfEditCount = selfEditCount;
    }
}