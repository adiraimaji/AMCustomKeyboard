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

    /** Whether the currently loaded keymap should also apply to text
     produced by a directional swipe (as opposed to only center taps).
     Controlled by the active layout's "swipekeymap" XML attribute -
     see Keyboard2.refresh_keymap(). Irrelevant (and never consulted)
     when no keymap is loaded at all. */
    private boolean allowSwipe = false;

    private int selfEditCount = 0;
    private int lastConsumedSelfEditCount = 0;

    private KeymapEngine()
    {
    }

    /** [allow_swipe] mirrors the active layout's "swipekeymap" attribute -
     pass true only when that attribute is present and "true". If the
     layout has no "keymap" attribute at all, [keymap_name] will be
     null and [allow_swipe]'s value has no effect (the map stays empty
     and process() always returns false / lets the caller commit raw
     text, regardless of swipe or not). */
    public void load(Context context, String keymap_name, boolean allow_swipe)
    {
        pendingRaw = "";
        pendingOutputLen = 0;
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

    /** [isSwipe] is true when [text] came from a directional swipe rather
     than a center tap. If true and the active layout's "swipekeymap"
     attribute isn't enabled, transliteration is skipped entirely for
     this call and the caller should commit [text] as-is (same as if
     no keymap were loaded at all). */
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