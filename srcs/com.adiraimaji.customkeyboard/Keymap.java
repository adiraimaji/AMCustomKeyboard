package com.adiraimaji.customkeyboard;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

public class Keymap {

    public final String name;

    private final HashMap<String, String> mappings =
            new HashMap<>();

    public Keymap(String json) throws Exception {

        JSONObject obj = new JSONObject(json);

        if (!obj.has("keymap_name"))
            throw new Exception("Missing keymap_name");

        name = obj.getString("keymap_name").trim();

        Iterator<String> keys = obj.keys();

        while (keys.hasNext()) {

            String key = keys.next();

            if (key.equals("keymap_name"))
                continue;

            mappings.put(
                    key,
                    obj.getString(key)
            );
        }
    }


    public Iterator<String> keys() {
        return mappings.keySet().iterator();
    }

    public String lookup(String key) {
        return mappings.get(key);
    }

    public boolean contains(String key) {
        return mappings.containsKey(key);
    }

    public HashMap<String, String> getMappings() {
        return mappings;
    }
}