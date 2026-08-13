package com.adiraimaji.customkeyboard;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-rolled parser for flat {"key":"value", ...} JSON objects that
 PRESERVES duplicate keys as separate entries in encounter order,
 unlike org.json.JSONObject which silently collapses duplicates (last
 value wins) with no way to detect that it happened.

 Keymaps are stored in exactly one format - "grouped": each entry is
 one OUTPUT mapped to a comma-separated list of KEYS that all produce
 it, e.g. {"keymap_name": "x", "\u0b85": "a", "\u0b86": "aa,A"}.
 A literal comma inside a key is written as "\," (backslash-escaped),
 same convention used everywhere else in the app.

 Also supports one additional shape needed by the Tasker Automation
 config: a top-level object where every value is a plain string
 EXCEPT one specific key, whose value is an array of nested flat
 objects (see [parse_object_with_array_field]) - used for
 "amck_patterns". This is intentionally narrow (a single named
 array field, one level of nesting) rather than a general JSON parser,
 since that's all any caller currently needs. */
public final class KeymapJsonUtils
{
    private KeymapJsonUtils() {}

    public static final class ParseError extends Exception
    {
        public ParseError(String msg) { super(msg); }
    }

    /** Result of parse_and_flatten(): the keymap's declared name (may be
     null if missing - callers must check) and the flattened key->output
     list, ready for direct use (duplicate detection, building the
     runtime Keymap.mappings map, or populating Keymap Builder rows). */
    public static final class FlattenResult
    {
        public final String keymap_name;
        public final List<Map.Entry<String, String>> flattened;

        public FlattenResult(String keymap_name_, List<Map.Entry<String, String>> flattened_)
        {
            keymap_name = keymap_name_;
            flattened = flattened_;
        }
    }

    /** Result of [parse_object_with_array_field]: every plain
     "key":"string value" pair (in encounter order, excluding the one
     array field), plus the array field's own entries - each element
     of the array is itself a flat {"key":"value", ...} object,
     represented the same way [parse_flat_object] would. [array_field_present]
     distinguishes "the key was present with an empty array" from "the
     key wasn't present at all" - both leave [array_objects] empty. */
    public static final class MixedObjectResult
    {
        public final List<Map.Entry<String, String>> string_entries;
        public final List<List<Map.Entry<String, String>>> array_objects;
        public final boolean array_field_present;

        public MixedObjectResult(List<Map.Entry<String, String>> string_entries_,
                                 List<List<Map.Entry<String, String>>> array_objects_,
                                 boolean array_field_present_)
        {
            string_entries = string_entries_;
            array_objects = array_objects_;
            array_field_present = array_field_present_;
        }
    }

    public static List<Map.Entry<String, String>> parse_flat_object(String json) throws ParseError
    {
        int[] pos = new int[]{ 0 };
        return parse_flat_object_body(json, pos, json.length());
    }

    /** Parses a top-level {"key":"value", ...} object, same as
     [parse_flat_object], except that [array_field_key]'s value must be
     a JSON array of flat objects rather than a string - every other
     key must still have a plain string value, same as
     [parse_flat_object]. */
    public static MixedObjectResult parse_object_with_array_field(String json, String array_field_key) throws ParseError
    {
        List<Map.Entry<String, String>> string_entries = new ArrayList<>();
        List<List<Map.Entry<String, String>>> array_objects = new ArrayList<>();
        boolean array_field_present = false;

        int len = json.length();
        int i = skip_ws(json, 0, len);
        if (i >= len || json.charAt(i) != '{')
            throw new ParseError("Expected '{' at the start of the JSON object");
        i++;
        i = skip_ws(json, i, len);
        if (i < len && json.charAt(i) == '}')
            return new MixedObjectResult(string_entries, array_objects, array_field_present);

        while (true)
        {
            i = skip_ws(json, i, len);
            if (i >= len || json.charAt(i) != '"')
                throw new ParseError("Expected a key string near position " + i);
            int[] pos = new int[]{ i };
            String key = parse_json_string(json, pos, len);
            i = skip_ws(json, pos[0], len);
            if (i >= len || json.charAt(i) != ':')
                throw new ParseError("Expected ':' after key \"" + key + "\"");
            i++;
            i = skip_ws(json, i, len);

            if (key.equals(array_field_key))
            {
                if (i >= len || json.charAt(i) != '[')
                    throw new ParseError("Expected an array for key \"" + key + "\"");
                i++;
                i = skip_ws(json, i, len);
                array_field_present = true;
                if (i < len && json.charAt(i) == ']')
                {
                    i++;
                }
                else
                {
                    while (true)
                    {
                        i = skip_ws(json, i, len);
                        if (i >= len || json.charAt(i) != '{')
                            throw new ParseError("Expected an object inside \"" + key + "\" near position " + i);
                        int[] op = new int[]{ i };
                        List<Map.Entry<String, String>> obj = parse_flat_object_body(json, op, len);
                        array_objects.add(obj);
                        i = skip_ws(json, op[0], len);
                        if (i < len && json.charAt(i) == ',')
                        {
                            i++;
                            continue;
                        }
                        else if (i < len && json.charAt(i) == ']')
                        {
                            i++;
                            break;
                        }
                        else
                        {
                            throw new ParseError("Expected ',' or ']' near position " + i + " in \"" + key + "\"");
                        }
                    }
                }
            }
            else
            {
                if (i >= len || json.charAt(i) != '"')
                    throw new ParseError("Expected a string value for key \"" + key + "\"");
                pos[0] = i;
                String value = parse_json_string(json, pos, len);
                i = pos[0];
                string_entries.add(new AbstractMap.SimpleEntry<>(key, value));
            }

            i = skip_ws(json, i, len);
            if (i < len && json.charAt(i) == ',')
            {
                i++;
                continue;
            }
            else if (i < len && json.charAt(i) == '}')
            {
                break;
            }
            else
            {
                throw new ParseError("Expected ',' or '}' near position " + i);
            }
        }
        return new MixedObjectResult(string_entries, array_objects, array_field_present);
    }

    /** Parses a single flat {"key":"value", ...} object starting at
     [pos[0]] (which must point at the opening '{'), advancing
     [pos[0]] to just past the matching closing '}'. Shared by
     [parse_flat_object] (the whole document is one such object) and
     [parse_object_with_array_field] (each array element is one). */
    private static List<Map.Entry<String, String>> parse_flat_object_body(String json, int[] pos, int len) throws ParseError
    {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        int i = skip_ws(json, pos[0], len);
        if (i >= len || json.charAt(i) != '{')
            throw new ParseError("Expected '{' near position " + i);
        i++;
        i = skip_ws(json, i, len);
        if (i < len && json.charAt(i) == '}')
        {
            pos[0] = i + 1;
            return result;
        }

        while (true)
        {
            i = skip_ws(json, i, len);
            if (i >= len || json.charAt(i) != '"')
                throw new ParseError("Expected a key string near position " + i);
            int[] kp = new int[]{ i };
            String key = parse_json_string(json, kp, len);
            i = skip_ws(json, kp[0], len);
            if (i >= len || json.charAt(i) != ':')
                throw new ParseError("Expected ':' after key \"" + key + "\"");
            i++;
            i = skip_ws(json, i, len);
            if (i >= len || json.charAt(i) != '"')
                throw new ParseError("Expected a string value for key \"" + key + "\"");
            kp[0] = i;
            String value = parse_json_string(json, kp, len);
            i = kp[0];
            result.add(new AbstractMap.SimpleEntry<>(key, value));
            i = skip_ws(json, i, len);
            if (i < len && json.charAt(i) == ',')
            {
                i++;
                continue;
            }
            else if (i < len && json.charAt(i) == '}')
            {
                i++;
                break;
            }
            else
            {
                throw new ParseError("Expected ',' or '}' near position " + i);
            }
        }
        pos[0] = i;
        return result;
    }

    /** Parses [json] (grouped format) and returns the flattened key->output
     representation. This is what Keymap.java (runtime loading), keymap
     dialog validation, and Keymap Builder's edit/import pre-fill all go
     through, so there's exactly one place that understands the storage
     shape. */
    public static FlattenResult parse_and_flatten(String json) throws ParseError
    {
        List<Map.Entry<String, String>> raw = parse_flat_object(json);

        String keymap_name = null;
        for (Map.Entry<String, String> e : raw)
            if (e.getKey().equals("keymap_name"))
                keymap_name = e.getValue();

        List<Map.Entry<String, String>> flattened = new ArrayList<>();
        for (Map.Entry<String, String> e : raw)
        {
            if (e.getKey().equals("keymap_name"))
                continue;
            String output = e.getKey();
            String keys_csv = e.getValue();
            for (String key : split_keys(keys_csv))
                if (!key.isEmpty())
                    flattened.add(new AbstractMap.SimpleEntry<>(key, output));
        }

        return new FlattenResult(keymap_name, flattened);
    }

    /** Returns keys that appear more than once, in first-seen order. Pass
     in [.flattened] from parse_and_flatten(). */
    public static List<String> find_duplicate_keys(List<Map.Entry<String, String>> entries)
    {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries)
        {
            Integer c = counts.get(e.getKey());
            counts.put(e.getKey(), c == null ? 1 : c + 1);
        }
        List<String> dups = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet())
            if (e.getValue() > 1)
                dups.add(e.getKey());
        return dups;
    }

    /** Splits a comma-separated keys field into individual keys, treating
     a backslash-escaped comma ("\,") as a literal comma character
     rather than a separator, e.g. "\,,cm" produces two keys: "," and
     "cm". Shared by grouped-format flattening and
     KeymapBuilderActivity's row keys fields. */
    public static List<String> split_keys(String raw)
    {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int len = raw.length();
        while (i < len)
        {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < len && raw.charAt(i + 1) == ',')
            {
                cur.append(',');
                i += 2;
            }
            else if (c == ',')
            {
                result.add(cur.toString());
                cur.setLength(0);
                i++;
            }
            else
            {
                cur.append(c);
                i++;
            }
        }
        result.add(cur.toString());
        return result;
    }

    private static int skip_ws(String s, int i, int len)
    {
        while (i < len && Character.isWhitespace(s.charAt(i)))
            i++;
        return i;
    }

    private static String parse_json_string(String s, int[] pos, int len) throws ParseError
    {
        int i = pos[0];
        if (s.charAt(i) != '"')
            throw new ParseError("Expected '\"' near position " + i);
        i++;
        StringBuilder b = new StringBuilder();
        while (true)
        {
            if (i >= len)
                throw new ParseError("Unterminated string");
            char c = s.charAt(i);
            if (c == '"')
            {
                i++;
                break;
            }
            else if (c == '\\')
            {
                i++;
                if (i >= len)
                    throw new ParseError("Unterminated escape sequence");
                char esc = s.charAt(i);
                switch (esc)
                {
                    case '"': b.append('"'); i++; break;
                    case '\\': b.append('\\'); i++; break;
                    case '/': b.append('/'); i++; break;
                    case 'n': b.append('\n'); i++; break;
                    case 'r': b.append('\r'); i++; break;
                    case 't': b.append('\t'); i++; break;
                    case 'b': b.append('\b'); i++; break;
                    case 'f': b.append('\f'); i++; break;
                    case 'u':
                        if (i + 4 >= len)
                            throw new ParseError("Invalid unicode escape");
                        String hex = s.substring(i + 1, i + 5);
                        try { b.append((char)Integer.parseInt(hex, 16)); }
                        catch (NumberFormatException e) { throw new ParseError("Invalid unicode escape \\u" + hex); }
                        i += 5;
                        break;
                    default:
                        throw new ParseError("Invalid escape sequence \\" + esc);
                }
            }
            else
            {
                b.append(c);
                i++;
            }
        }
        pos[0] = i;
        return b.toString();
    }
}
