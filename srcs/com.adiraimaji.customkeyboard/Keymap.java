package com.adiraimaji.customkeyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Keymap {

    public final String name;

    private final HashMap<String, String> mappings = new HashMap<>();

    public Keymap(String json) throws Exception {

        KeymapJsonUtils.FlattenResult result = KeymapJsonUtils.parse_and_flatten(json);

        if (result.keymap_name == null || result.keymap_name.trim().isEmpty())
            throw new Exception("Missing keymap_name");

        name = result.keymap_name.trim();

        for (Map.Entry<String, String> e : result.flattened)
            mappings.put(e.getKey(), e.getValue());
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