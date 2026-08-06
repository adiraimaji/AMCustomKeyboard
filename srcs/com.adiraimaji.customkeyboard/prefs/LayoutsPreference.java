package com.adiraimaji.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.adiraimaji.customkeyboard.*;
import org.json.JSONException;
import org.json.JSONObject;

public class LayoutsPreference extends ListGroupPreference<LayoutsPreference.Layout>
{
  static final String KEY = "layouts";
  static final List<Layout> DEFAULT =
          Collections.singletonList((Layout)new SystemLayout());
  static final ListGroupPreference.Serializer<Layout> SERIALIZER =
          new Serializer();

  /** Text displayed for each layout in the dialog list. */
  String[] _layout_display_names;

  public LayoutsPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setKey(KEY);
    Resources res = ctx.getResources();
    _layout_display_names = res.getStringArray(R.array.pref_layout_entries);
  }

  /** Obtained from [res/values/layouts.xml]. */
  static List<String> _unsafe_layout_ids_str = null;
  static TypedArray _unsafe_layout_ids_res = null;

  /** Layout internal names. Contains "system" and "custom". */
  public static List<String> get_layout_names(Resources res)
  {
    if (_unsafe_layout_ids_str == null)
      _unsafe_layout_ids_str = Arrays.asList(
              res.getStringArray(R.array.pref_layout_values));
    return _unsafe_layout_ids_str;
  }

  /** Layout resource id for a layout name. [-1] if not found. */
  public static int layout_id_of_name(Resources res, String name)
  {
    if (_unsafe_layout_ids_res == null)
      _unsafe_layout_ids_res = res.obtainTypedArray(R.array.layout_ids);
    int i = get_layout_names(res).indexOf(name);
    if (i >= 0)
      return _unsafe_layout_ids_res.getResourceId(i, 0);
    return -1;
  }

  /** [null] for the "system" layout. KeymapEntry items are skipped - they
   are references to a saved keymap JSON (see KeymapManager), not
   actual keyboard layouts, and must never end up in Config.layouts. */
  public static List<KeyboardData> load_from_preferences(Resources res, SharedPreferences prefs)
  {
    List<KeyboardData> layouts = new ArrayList<KeyboardData>();
    for (Layout l : load_from_preferences(KEY, prefs, DEFAULT, SERIALIZER))
    {
      if (l instanceof KeymapEntry)
        continue;
      if (l instanceof NamedLayout)
        layouts.add(layout_of_string(res, ((NamedLayout)l).name));
      else if (l instanceof CustomLayout)
        layouts.add(((CustomLayout)l).parsed);
      else // instanceof SystemLayout
        layouts.add(null);
    }
    return layouts;
  }

  /** Does not call [prefs.commit()]. */
  public static void save_to_preferences(SharedPreferences.Editor prefs, List<Layout> items)
  {
    save_to_preferences(KEY, prefs, items, SERIALIZER);
  }

  public static KeyboardData layout_of_string(Resources res, String name)
  {
    int id = layout_id_of_name(res, name);
    if (id > 0)
      return KeyboardData.load(res, id);
    // Might happen when the app is downgraded, return the system layout.
    return null;
  }

  @Override
  protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
  {
    super.onSetInitialValue(restoreValue, defaultValue);
    if (_values.size() == 0)
      set_values(new ArrayList<Layout>(DEFAULT), false);
    sync_keymap_entries();
  }

  /** Ensures every keymap saved in [KeymapManager] has a corresponding
   [KeymapEntry] row here, adding any that are missing. Handles keymaps
   saved before KeymapEntry rows existed, or through any path other
   than the "Add new Keymap JSON" dialog (e.g. Keymap Builder), so the
   Settings list always reflects everything KeymapManager has stored. */
  void sync_keymap_entries()
  {
    List<KeymapManager.StoredKeymap> stored = KeymapManager.load(getContext());
    if (stored.isEmpty())
      return;

    boolean changed = false;

    for (KeymapManager.StoredKeymap k : stored)
    {
      boolean already_present = false;

      for (Layout v : _values)
      {
        if (v instanceof KeymapEntry && ((KeymapEntry)v).name.equals(k.name))
        {
          already_present = true;
          break;
        }
      }

      if (!already_present)
      {
        _values.add(new KeymapEntry(k.name));
        changed = true;
      }
    }

    if (changed)
      set_values(_values, true);
  }

  /** Public entry point for re-syncing after returning from an activity
   that may have added a keymap outside this preference's own dialogs
   (e.g. KeymapBuilderActivity), since returning to SettingsActivity
   only triggers onResume(), not onSetInitialValue(). See
   SettingsActivity.onResume(). */
  public void refresh_keymap_entries()
  {
    sync_keymap_entries();
  }

  String label_of_layout(Layout l)
  {
    if (l instanceof NamedLayout)
    {
      String lname = ((NamedLayout)l).name;
      int value_i = get_layout_names(getContext().getResources()).indexOf(lname);
      return value_i < 0 ? lname : _layout_display_names[value_i];
    }
    else if (l instanceof CustomLayout)
    {
      // Use the layout's name if possible
      CustomLayout cl = (CustomLayout)l;
      if (cl.parsed != null && cl.parsed.name != null
              && !cl.parsed.name.equals(""))
        return cl.parsed.name;
      else
        return getContext().getString(R.string.pref_layout_e_custom);
    }
    else // instanceof SystemLayout
      return getContext().getString(R.string.pref_layout_e_system);
  }

  /** Layout rows and Keymap rows are numbered independently, e.g.
   "Layout 1", "Layout 2", "Keymap 1", "Keymap 2", even though they all
   live in the same underlying list. */
  @Override
  String label_of_value(Layout value, int i)
  {
    if (value instanceof KeymapEntry)
    {
      int keymap_number = 0;
      for (int j = 0; j <= i; j++)
        if (_values.get(j) instanceof KeymapEntry)
          keymap_number++;
      return "Keymap " + keymap_number + ": " + ((KeymapEntry)value).name;
    }

    int layout_number = 0;
    for (int j = 0; j <= i; j++)
      if (!(_values.get(j) instanceof KeymapEntry))
        layout_number++;

    return getContext().getString(R.string.pref_layouts_item, layout_number,
            label_of_layout(value));
  }

  @Override
  AddButton on_attach_add_button(AddButton prev_btn)
  {
    if (prev_btn == null)
      return new LayoutsAddButton(getContext());
    return prev_btn;
  }

  /** Adds the standalone "Add new Keymap JSON" and "Keymap Builder"
   buttons, rendered right after the regular "Add an alternate layout"
   button (see reattach() below for exact ordering). */
  @Override
  List<Preference> on_attach_extra_buttons(List<Preference> prev_buttons)
  {
    if (prev_buttons != null && prev_buttons.size() > 0)
      return prev_buttons;
    ArrayList<Preference> l = new ArrayList<Preference>();
    l.add(new AddKeymapButton(getContext()));
    l.add(new AddKeymapBuilderButton(getContext()));
    return l;
  }

  /** Keeps all Layout entries before all Keymap entries, regardless of the
   order the user added them in, so the rendered list always reads
   "Layout 1, Layout 2, ..., Keymap 1, Keymap 2, ..." with the add
   buttons after everything. */
  @Override
  void add_item(Layout v)
  {
    int insert_at = _values.size();

    if (!(v instanceof KeymapEntry))
    {
      insert_at = 0;
      for (Layout existing : _values)
      {
        if (existing instanceof KeymapEntry)
          break;
        insert_at++;
      }
    }

    _values.add(insert_at, v);
    set_values(_values, true);
  }

  /** KeymapEntry rows are edited/removed only via their own dialog (like
   CustomLayout), never via the small inline remove icon. Real layouts
   also always require at least one to remain. */
  @Override
  boolean should_allow_remove_item(Layout value)
  {
    if (value instanceof KeymapEntry)
      return false;

    int layout_count = 0;
    for (Layout v : _values)
      if (!(v instanceof KeymapEntry))
        layout_count++;

    return (layout_count > 1 && !(value instanceof CustomLayout));
  }

  @Override
  ListGroupPreference.Serializer<Layout> get_serializer() { return SERIALIZER; }

  /** Overrides the base class's flat rendering order (all items, then add
   button, then extra buttons) with two visually separate groups:
   Layout rows + their add button, followed by Keymap rows + their
   "Add new Keymap JSON" / "Keymap Builder" buttons. Indices passed to
   Item must stay the item's real position in [_values] (not its
   position within its sub-group), since Item uses that index to call
   change_item()/remove_item() on the real list.

   IMPORTANT: _add_button and each entry in _extra_buttons are REUSED
   across calls, so their order must be reset to DEFAULT_ORDER right
   before re-adding them each time - otherwise they keep whatever order
   number Android auto-assigned the first time, which becomes stale
   once rows are added/removed later. */
  @Override
  void reattach()
  {
    if (!_attached)
      return;
    removeAll();

    for (int i = 0; i < _values.size(); i++)
    {
      Layout v = _values.get(i);
      if (!(v instanceof KeymapEntry))
        addPreference(this.new Item(getContext(), i, v));
    }

    _add_button = on_attach_add_button(_add_button);
    _add_button.setOrder(Preference.DEFAULT_ORDER);
    addPreference(_add_button);

    for (int i = 0; i < _values.size(); i++)
    {
      Layout v = _values.get(i);
      if (v instanceof KeymapEntry)
        addPreference(this.new Item(getContext(), i, v));
    }

    _extra_buttons = on_attach_extra_buttons(_extra_buttons);
    if (_extra_buttons != null)
    {
      for (Preference p : _extra_buttons)
      {
        p.setOrder(Preference.DEFAULT_ORDER);
        addPreference(p);
      }
    }
  }

  /** Dialog shown by "Add an alternate layout": built-in/system/custom
   layouts only. Adding a keymap has its own dedicated buttons (see
   AddKeymapButton and AddKeymapBuilderButton) and no longer appears
   in this dialog. */
  void select_dialog(final SelectionCallback callback)
  {
    ArrayList<String> entries = new ArrayList<>();
    Collections.addAll(entries, _layout_display_names);

    ArrayAdapter<String> adapter =
            new ArrayAdapter<>(
                    getContext(),
                    android.R.layout.simple_list_item_1,
                    entries);

    new AlertDialog.Builder(getContext())
            .setAdapter(adapter, new DialogInterface.OnClickListener(){
              public void onClick(DialogInterface _dialog, int which)
              {
                String name = get_layout_names(getContext().getResources()).get(which);
                switch (name)
                {
                  case "system":
                    callback.select(new SystemLayout());
                    break;
                  case "custom":
                    select_custom(callback, read_initial_custom_layout());
                    break;
                  default:
                    callback.select(new NamedLayout(name));
                    break;
                }
              }
            })
            .show();
  }

  /** Dialog for specifying a custom layout. [initial_text] is the layout
   description when modifying a layout. */
  void select_custom(final SelectionCallback callback, String initial_text)
  {
    boolean allow_remove = callback.allow_remove() && _values.size() > 1;
    CustomLayoutEditDialog.show(getContext(), initial_text, allow_remove,
            new CustomLayoutEditDialog.Callback()
            {
              public void select(String text)
              {
                if (text == null)
                  callback.select(null);
                else
                  callback.select(CustomLayout.parse(text));
              }

              public String validate(String text)
              {
                try
                {
                  KeyboardData.load_string_exn(text);
                  return null; // Validation passed
                }
                catch (Exception e)
                {
                  return e.getMessage();
                }
              }
            });
  }

  /** Called when modifying an existing row. Custom layouts and keymaps
   each get their own editor; everything else re-opens the picker. */
  @Override
  void select(final SelectionCallback callback, Layout prev_layout)
  {
    if (prev_layout != null && prev_layout instanceof CustomLayout)
    {
      select_custom(callback, ((CustomLayout)prev_layout).xml);
    }
    else if (prev_layout != null && prev_layout instanceof KeymapEntry)
    {
      String name = ((KeymapEntry)prev_layout).name;
      KeymapManager.StoredKeymap stored = KeymapManager.find(getContext(), name);
      String initial = (stored != null) ? stored.json : read_initial_keymap();
      select_keymap(callback, initial, name);
    }
    else
    {
      select_dialog(callback);
    }
  }

  /** The initial text for the custom layout entry box. The qwerty_us layout is
   a good default and contains a bit of documentation. */
  String read_initial_custom_layout()
  {
    try
    {
      Resources res = getContext().getResources();
      return Utils.read_all_utf8(res.openRawResource(R.raw.latn_qwerty_us));
    }
    catch (Exception _e)
    {
      return "";
    }
  }

  String read_initial_keymap()
  {
    return "{\n" +
            "  \"keymap_name\": \"\",\n" +
            "\n" +
            "  \"a\": \"அ\",\n" +
            "  \"aa\": \"ஆ\"\n" +
            "}";
  }

  /** Shows the keymap JSON editor. [existing_name] is null when adding a
   brand new keymap, or the current name when editing/removing one
   already saved. On success, resolves [callback] with a lightweight
   KeymapEntry reference - the full JSON itself lives in
   KeymapManager's own storage, not in this preference's list. */
  void select_keymap(final SelectionCallback callback, String initialText,
                     final String existing_name)
  {
    boolean allow_remove = existing_name != null;

    KeymapEditDialog.show(
            getContext(),
            initialText,
            allow_remove,
            new KeymapEditDialog.Callback()
            {
              @Override
              public void select(String json)
              {
                if (json == null)
                {
                  // Cancelled (add flow: no-op), or "Remove" pressed while
                  // editing an existing keymap.
                  if (existing_name != null)
                  {
                    KeymapManager.remove(getContext(), existing_name);
                    callback.select(null);
                  }
                  return;
                }

                try
                {
                  JSONObject obj = new JSONObject(json);
                  String name = obj.getString("keymap_name").trim();

                  // Renamed while editing: drop the old stored entry.
                  if (existing_name != null && !existing_name.equals(name))
                    KeymapManager.remove(getContext(), existing_name);

                  KeymapManager.add(
                          getContext(),
                          new KeymapManager.StoredKeymap(name, json)
                  );

                  callback.select(new KeymapEntry(name));
                }
                catch (Exception e)
                {
                  e.printStackTrace();
                }
              }

              @Override
              public String validate(String text)
              {
                try
                {
                  JSONObject obj = new JSONObject(text);

                  if (!obj.has("keymap_name")
                          || obj.getString("keymap_name").trim().isEmpty())
                    return "\"keymap_name\" is required";

                  return null;
                }
                catch (Exception e)
                {
                  return e.getMessage();
                }
              }
            });
  }

  class LayoutsAddButton extends AddButton
  {
    public LayoutsAddButton(Context ctx)
    {
      super(ctx);
      setLayoutResource(R.layout.pref_layouts_add_btn);
      setTitle(R.string.pref_layouts_add);
    }
  }

  /** Standalone button that opens the keymap JSON editor directly, without
   going through the layout picker dialog. Not backed by an item in
   [_values] the way AddButton's onClick() implies (add_item is called
   manually from select_keymap's callback), so this extends [Preference]
   directly rather than [AddButton]. */
  class AddKeymapButton extends Preference
  {
    public AddKeymapButton(Context ctx)
    {
      super(ctx);
      setPersistent(false);
      setLayoutResource(R.layout.pref_layouts_add_btn);
      setTitle(R.string.pref_layouts_add_keymap);
    }

    @Override
    protected void onClick()
    {
      select_keymap(new SelectionCallback<Layout>()
      {
        public void select(Layout value)
        {
          if (value != null)
            add_item(value);
        }

        public boolean allow_remove() { return false; }
      }, read_initial_keymap(), null);
    }
  }

  /** Launches KeymapBuilderActivity, a guided form for constructing a
   keymap JSON instead of hand-writing it. The activity saves directly
   to KeymapManager and finishes; the new row appears here once the
   user returns, via SettingsActivity.onResume() calling
   refresh_keymap_entries(). */
  class AddKeymapBuilderButton extends Preference
  {
    public AddKeymapBuilderButton(Context ctx)
    {
      super(ctx);
      setPersistent(false);
      setLayoutResource(R.layout.pref_layouts_add_btn);
      setTitle(R.string.pref_layouts_add_keymap_builder);
    }

    @Override
    protected void onClick()
    {
      Intent intent = new Intent(getContext(), KeymapBuilderActivity.class);
      getContext().startActivity(intent);
    }
  }

  /** A layout selected by the user. The implementations are
   [NamedLayout], [SystemLayout], [CustomLayout] and [KeymapEntry]. */
  public interface Layout {}

  public static final class SystemLayout implements Layout
  {
    public SystemLayout() {}
  }

  /** The name of a layout defined in [srcs/layouts]. */
  public static final class NamedLayout implements Layout
  {
    public final String name;
    public NamedLayout(String n) { name = n; }
  }

  /** The XML description of a custom layout. */
  public static final class CustomLayout implements Layout
  {
    public final String xml;
    /** Might be null. */
    public final KeyboardData parsed;
    public CustomLayout(String xml_, KeyboardData k) { xml = xml_; parsed = k; }
    public static CustomLayout parse(String xml)
    {
      KeyboardData parsed = null;
      try { parsed = KeyboardData.load_string_exn(xml); }
      catch (Exception e) {}
      return new CustomLayout(xml, parsed);
    }
  }

  /** A lightweight reference to a keymap saved via [KeymapManager], kept in
   the same list as layouts so it renders as its own row in Settings
   (grouped after all Layout rows - see add_item()). It never
   contributes to Config.layouts - see load_from_preferences(). */
  public static final class KeymapEntry implements Layout
  {
    public final String name;
    public KeymapEntry(String n) { name = n; }
  }

  /** Named layouts are serialized to strings, custom layouts and keymap
   references to JSON objects with a [kind] field. */
  public static class Serializer implements ListGroupPreference.Serializer<Layout>
  {
    public Layout load_item(Object obj) throws JSONException
    {
      if (obj instanceof String)
      {
        String name = (String)obj;
        if (name.equals("system"))
          return new SystemLayout();
        return new NamedLayout(name);
      }
      JSONObject obj_ = (JSONObject)obj;
      switch (obj_.getString("kind"))
      {
        case "custom": return CustomLayout.parse(obj_.getString("xml"));
        case "keymap": return new KeymapEntry(obj_.getString("name"));
        case "system": default: return new SystemLayout();
      }
    }

    public Object save_item(Layout v) throws JSONException
    {
      if (v instanceof NamedLayout)
        return ((NamedLayout)v).name;
      if (v instanceof CustomLayout)
        return new JSONObject().put("kind", "custom")
                .put("xml", ((CustomLayout)v).xml);
      if (v instanceof KeymapEntry)
        return new JSONObject().put("kind", "keymap")
                .put("name", ((KeymapEntry)v).name);
      return new JSONObject().put("kind", "system");
    }
  }
}