package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class KeymapManager {

    private static final String PREF_NAME = "keymaps";
    private static final String KEY_KEYMAPS = "items";

    private static final String KEY_ACTIVE_KEYMAP = "active";

    public static class StoredKeymap {

        public final String name;
        public final String json;

        public StoredKeymap(String name, String json) {
            this.name = name;
            this.json = json;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static ArrayList<StoredKeymap> load(Context context) {

        ArrayList<StoredKeymap> result = new ArrayList<>();

        String raw = prefs(context).getString(KEY_KEYMAPS, "[]");

        try {

            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {

                JSONObject obj = array.getJSONObject(i);

                result.add(new StoredKeymap(
                        obj.getString("name"),
                        obj.getString("json")
                ));
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    public static void save(Context context, ArrayList<StoredKeymap> list) {

        JSONArray array = new JSONArray();

        try {

            for (StoredKeymap k : list) {

                JSONObject obj = new JSONObject();

                obj.put("name", k.name);
                obj.put("json", k.json);

                array.put(obj);
            }

        } catch (JSONException ignored) {
        }

        prefs(context)
                .edit()
                .putString(KEY_KEYMAPS, array.toString())
                .apply();
    }

    /** Adds or updates a keymap by name. Does NOT change which layout uses it -
     a keymap is only linked to a layout via the layout's own "keymap"
     attribute, looked up by name at runtime. */
    public static void add(Context context, StoredKeymap keymap) {

        ArrayList<StoredKeymap> list = load(context);

        for (int i = 0; i < list.size(); i++) {

            if (list.get(i).name.equals(keymap.name)) {

                list.set(i, keymap);

                save(context, list);

                return;
            }
        }

        list.add(keymap);

        save(context, list);
    }

    /** Removes a keymap by name. Safe to call even if it's referenced by a
     layout's "keymap" attribute - the layout will simply stop
     transliterating (KeymapEngine.load will find nothing and no-op). */
    public static void remove(Context context, String name) {

        ArrayList<StoredKeymap> list = load(context);

        for (int i = 0; i < list.size(); i++) {

            if (list.get(i).name.equals(name)) {

                list.remove(i);

                break;
            }
        }

        save(context, list);

        if (name.equals(getActive(context)))
            setActive(context, null);
    }

    public static StoredKeymap find(Context context, String name) {

        for (StoredKeymap k : load(context)) {

            if (k.name.equals(name))
                return k;
        }

        return null;
    }

    public static com.adiraimaji.customkeyboard.Keymap loadKeymap(
            Context context,
            String name) {

        if (name == null)
            return null;

        StoredKeymap stored = find(context, name);

        if (stored == null)
            return null;

        try {

            return new com.adiraimaji.customkeyboard.Keymap(stored.json);

        } catch (Exception e) {

            return null;
        }
    }

    /** @deprecated Kept only for backward compatibility. Per-layout keymaps
    (via the "keymap" XML attribute, resolved with loadKeymap(name)) are
    the supported mechanism now. */
    @Deprecated
    public static void setActive(Context context, String name)
    {
        prefs(context)
                .edit()
                .putString(KEY_ACTIVE_KEYMAP, name)
                .apply();
    }

    @Deprecated
    public static String getActive(Context context)
    {
        return prefs(context)
                .getString(KEY_ACTIVE_KEYMAP, null);
    }

    @Deprecated
    public static com.adiraimaji.customkeyboard.Keymap loadActiveKeymap(Context context)
    {
        String name = getActive(context);

        if (name == null)
            return null;

        return loadKeymap(context, name);
    }

}