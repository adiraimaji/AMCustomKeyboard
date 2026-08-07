package com.adiraimaji.customkeyboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilities for reading and rewriting the "keymap" and "swipekeymap"
 attributes on the top-level <keyboard> tag of a custom layout's raw
 XML text, without fully parsing/reserializing the layout. Used to
 keep layout XML in sync with keymap-selector UI (see
 CustomLayoutEditDialog) and to propagate keymap renames/deletions
 across every layout that references them (see LayoutsPreference). */
public final class KeymapXmlAttrUtils
{
    private KeymapXmlAttrUtils() {}

    private static final Pattern KEYBOARD_TAG =
            Pattern.compile("<keyboard\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern KEYMAP_ATTR =
            Pattern.compile("\\s+keymap\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern SWIPEKEYMAP_ATTR =
            Pattern.compile("\\s+swipekeymap\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern NAME_ATTR =
            Pattern.compile("\\s+name\\s*=\\s*\"([^\"]*)\"");

    /** Returns the current keymap="..." value on the first <keyboard> tag,
     or null if absent or no <keyboard> tag is found. */

    /** Returns the current name="..." value on the first <keyboard> tag,
     or null if absent or no <keyboard> tag is found. */
    public static String get_name_attr(String xml)
    {
        String tag = find_tag(xml);
        if (tag == null)
            return null;
        Matcher m = NAME_ATTR.matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /** Sets/replaces the name attribute. Pass null or "" to remove it. */
    public static String set_name_attr(String xml, String name)
    {
        if (name == null || name.isEmpty())
            return replace_in_tag(xml, NAME_ATTR, "");
        return replace_in_tag(xml, NAME_ATTR, " name=\"" + escape_attr(name) + "\"");
    }
    public static String get_keymap_attr(String xml)
    {
        String tag = find_tag(xml);
        if (tag == null)
            return null;
        Matcher m = KEYMAP_ATTR.matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /** True only if swipekeymap="true" is present (matching
     KeyboardData.attribute_bool's own comparison). */
    public static boolean get_swipekeymap_attr(String xml)
    {
        String tag = find_tag(xml);
        if (tag == null)
            return false;
        Matcher m = SWIPEKEYMAP_ATTR.matcher(tag);
        return m.find() && "true".equals(m.group(1));
    }

    /** Sets/replaces the keymap attribute. Pass null or "" to remove it
     (along with swipekeymap, which is meaningless without a keymap). */
    public static String set_keymap_attr(String xml, String name)
    {
        if (name == null || name.isEmpty())
            return remove_keymap_attrs(xml);
        return replace_in_tag(xml, KEYMAP_ATTR, " keymap=\"" + escape_attr(name) + "\"");
    }

    /** Sets or removes the swipekeymap attribute. No-op if there's no
     <keyboard> tag. */
    public static String set_swipekeymap_attr(String xml, boolean enabled)
    {
        if (!enabled)
            return replace_in_tag(xml, SWIPEKEYMAP_ATTR, "");
        return replace_in_tag(xml, SWIPEKEYMAP_ATTR, " swipekeymap=\"true\"");
    }

    /** Removes both attributes entirely from the first <keyboard> tag. */
    public static String remove_keymap_attrs(String xml)
    {
        String result = replace_in_tag(xml, KEYMAP_ATTR, "");
        result = replace_in_tag(result, SWIPEKEYMAP_ATTR, "");
        return result;
    }

    private static String find_tag(String xml)
    {
        if (xml == null)
            return null;
        Matcher m = KEYBOARD_TAG.matcher(xml);
        return m.find() ? m.group() : null;
    }

    /** Replaces (or inserts, if absent) [attr_pattern]'s match within the
     first <keyboard> tag with [replacement] (should include its own
     leading space, e.g. ' keymap="foo"', or be "" to just remove the
     attribute). Leaves the rest of the XML untouched. Safe no-op if
     there's no <keyboard> tag (e.g. mid-edit, invalid XML). */
    private static String replace_in_tag(String xml, Pattern attr_pattern, String replacement)
    {
        if (xml == null)
            return xml;
        Matcher tag_matcher = KEYBOARD_TAG.matcher(xml);
        if (!tag_matcher.find())
            return xml;
        String tag = tag_matcher.group();
        Matcher attr_matcher = attr_pattern.matcher(tag);
        String new_tag;
        if (attr_matcher.find())
            new_tag = attr_matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        else if (!replacement.isEmpty())
            new_tag = tag.replaceFirst("^<keyboard", Matcher.quoteReplacement("<keyboard" + replacement));
        else
            new_tag = tag;
        return xml.substring(0, tag_matcher.start()) + new_tag + xml.substring(tag_matcher.end());
    }

    private static String escape_attr(String s)
    {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}