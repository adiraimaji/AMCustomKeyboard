package com.adiraimaji.customkeyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-rolled parser for flat {"key":"value", ...} JSON objects that
 PRESERVES duplicate keys as separate entries in encounter order,
 unlike org.json.JSONObject which silently collapses duplicates (last
 value wins) with no way to detect that it happened. Used to validate
 keymap JSON text and to warn about duplicate keys instead of silently
 losing data. */
public final class KeymapJsonUtils
{
    private KeymapJsonUtils() {}

    public static final class ParseError extends Exception
    {
        public ParseError(String msg) { super(msg); }
    }

    public static List<Map.Entry<String, String>> parse_flat_object(String json) throws ParseError
    {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        int len = json.length();
        int i = skip_ws(json, 0, len);
        if (i >= len || json.charAt(i) != '{')
            throw new ParseError("Expected '{' at the start of the JSON object");
        i++;
        i = skip_ws(json, i, len);
        if (i < len && json.charAt(i) == '}')
            return result;

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
            if (i >= len || json.charAt(i) != '"')
                throw new ParseError("Expected a string value for key \"" + key + "\"");
            pos[0] = i;
            String value = parse_json_string(json, pos, len);
            i = pos[0];
            result.add(new LinkedHashMap.SimpleEntry<>(key, value));
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
        return result;
    }

    /** Returns keys that appear more than once, in first-seen order. */
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