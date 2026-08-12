package com.adiraimaji.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  String[] _layout_display_names;

  public LayoutsPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setKey(KEY);
    Resources res = ctx.getResources();
    _layout_display_names = res.getStringArray(R.array.pref_layout_entries);
  }

  static List<String> _unsafe_layout_ids_str = null;
  static TypedArray _unsafe_layout_ids_res = null;

  public static List<String> get_layout_names(Resources res)
  {
    if (_unsafe_layout_ids_str == null)
      _unsafe_layout_ids_str = Arrays.asList(
              res.getStringArray(R.array.pref_layout_values));
    return _unsafe_layout_ids_str;
  }

  public static int layout_id_of_name(Resources res, String name)
  {
    if (_unsafe_layout_ids_res == null)
      _unsafe_layout_ids_res = res.obtainTypedArray(R.array.layout_ids);
    int i = get_layout_names(res).indexOf(name);
    if (i >= 0)
      return _unsafe_layout_ids_res.getResourceId(i, 0);
    return -1;
  }

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
      else
        layouts.add(null);
    }
    return layouts;
  }

  public static void save_to_preferences(SharedPreferences.Editor prefs, List<Layout> items)
  {
    save_to_preferences(KEY, prefs, items, SERIALIZER);
  }

  public static KeyboardData layout_of_string(Resources res, String name)
  {
    int id = layout_id_of_name(res, name);
    if (id > 0)
      return KeyboardData.load(res, id);
    return null;
  }

  @Override
  protected void onSetInitialValue(Object defaultValue)
  {
    super.onSetInitialValue(defaultValue);
    if (_values.size() == 0)
      set_values(new ArrayList<Layout>(DEFAULT), false);
    sync_keymap_entries();
  }

  /** Adds a row for any stored keymap without one yet, and removes any
   row whose keymap no longer exists (renamed/deleted). */
  void sync_keymap_entries()
  {
    List<KeymapManager.StoredKeymap> stored = KeymapManager.load(getContext());
    boolean changed = false;

    for (KeymapManager.StoredKeymap k : stored)
    {
      boolean already_present = false;
      for (Layout v : _values)
        if (v instanceof KeymapEntry && ((KeymapEntry)v).name.equals(k.name))
        {
          already_present = true;
          break;
        }
      if (!already_present)
      {
        _values.add(new KeymapEntry(k.name));
        changed = true;
      }
    }

    for (int i = _values.size() - 1; i >= 0; i--)
    {
      Layout v = _values.get(i);
      if (v instanceof KeymapEntry)
      {
        boolean still_exists = false;
        for (KeymapManager.StoredKeymap k : stored)
          if (k.name.equals(((KeymapEntry)v).name))
          {
            still_exists = true;
            break;
          }
        if (!still_exists)
        {
          _values.remove(i);
          changed = true;
        }
      }
    }

    if (changed)
      set_values(_values, true);
  }

  /** Reloads _values fresh from SharedPreferences, discarding any
   in-memory state, then re-syncs keymap rows. Called by
   SettingsActivity.onResume() so a paused Settings screen picks up
   writes made directly to SharedPreferences elsewhere (e.g. keymap
   rename propagation from KeymapBuilderActivity, a separate
   Activity) instead of later clobbering them with stale in-memory
   data. */
  public void reload_from_preferences_and_sync()
  {
    String input = getPersistedString(null);
    if (input != null)
    {
      List<Layout> values = load_from_string(input, get_serializer());
      if (values != null)
        set_values(values, false);
    }
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
      CustomLayout cl = (CustomLayout)l;
      if (cl.parsed != null && cl.parsed.name != null && !cl.parsed.name.equals(""))
        return cl.parsed.name;
      else
        return getContext().getString(R.string.pref_layout_e_custom);
    }
    else
      return getContext().getString(R.string.pref_layout_e_system);
  }

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

    boolean has_keymaps = false;
    for (Layout v : _values)
      if (v instanceof KeymapEntry) { has_keymaps = true; break; }
    if (has_keymaps)
      addPreference(new KeymapsSectionHeader(getContext()));

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

  void select_dialog(final SelectionCallback callback)
  {
    ArrayList<String> entries = new ArrayList<>();
    Collections.addAll(entries, _layout_display_names);

    ArrayAdapter<String> adapter =
            new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, entries);

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

  void select_custom(final SelectionCallback callback, String initial_text)
  {
    boolean allow_remove = callback.allow_remove() && _values.size() > 1;
    CustomLayoutEditDialog.show(getContext(), initial_text, allow_remove,
            R.string.pref_custom_layout_title, R.string.pref_layouts_remove_custom,
            true, null,
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
                  return null;
                }
                catch (Exception e)
                {
                  return e.getMessage();
                }
              }
            });
  }

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
            "  \"அ\": \"a\",\n" +
            "  \"ஆ\": \"aa,A\"\n" +
            "}";
  }

  void select_keymap(final SelectionCallback callback, String initialText,
                     final String existing_name)
  {
    boolean allow_remove = existing_name != null;

    KeymapEditDialog.show(
            getContext(),
            initialText,
            allow_remove,
            existing_name,
            new KeymapEditDialog.Callback()
            {
              @Override
              public void select(String json)
              {
                if (json == null)
                {
                  if (existing_name != null)
                  {
                    int ref_count = count_keymap_references(existing_name);
                    if (ref_count > 0)
                    {
                      final String name_to_remove = existing_name;
                      ConfirmDialog.show(getContext(),
                              getContext().getString(R.string.keymap_delete_in_use_title),
                              getContext().getString(R.string.keymap_delete_in_use_message,
                                      name_to_remove, ref_count),
                              getContext().getString(R.string.keymap_delete_in_use_confirm),
                              getContext().getString(android.R.string.cancel),
                              new ConfirmDialog.OnResult()
                              {
                                public void result(boolean positive)
                                {
                                  if (positive)
                                  {
                                    clear_keymap_references(name_to_remove);
                                    KeymapManager.remove(getContext(), name_to_remove);
                                    callback.select(null);
                                  }
                                }
                              });
                    }
                    else
                    {
                      KeymapManager.remove(getContext(), existing_name);
                      callback.select(null);
                    }
                  }
                  return;
                }

                try
                {
                  JSONObject obj = new JSONObject(json);
                  final String name = obj.getString("keymap_name").trim();

                  KeymapManager.StoredKeymap existing = KeymapManager.find(getContext(), name);
                  boolean overwriting_different_entry = existing != null
                          && (existing_name == null || !existing_name.equals(name));

                  if (overwriting_different_entry)
                  {
                    final String final_json = json;
                    ConfirmDialog.show(getContext(),
                            getContext().getString(R.string.keymap_builder_overwrite_title),
                            getContext().getString(R.string.keymap_builder_overwrite_message, name),
                            getContext().getString(R.string.keymap_builder_overwrite_yes),
                            getContext().getString(R.string.keymap_builder_overwrite_cancel),
                            new ConfirmDialog.OnResult()
                            {
                              public void result(boolean positive)
                              {
                                if (positive)
                                  finish_save_keymap(existing_name, name, final_json);
                              }
                            });
                    return;
                  }

                  finish_save_keymap(existing_name, name, json);
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
                  KeymapJsonUtils.FlattenResult result = KeymapJsonUtils.parse_and_flatten(text);

                  if (result.keymap_name == null || result.keymap_name.trim().isEmpty())
                    return "\"keymap_name\" is required";

                  List<String> dup_keys = KeymapJsonUtils.find_duplicate_keys(result.flattened);
                  if (!dup_keys.isEmpty())
                  {
                    StringBuilder b = new StringBuilder("Duplicate keys: ");
                    for (int i = 0; i < dup_keys.size(); i++)
                    {
                      if (i > 0) b.append(", ");
                      b.append(dup_keys.get(i));
                    }
                    return b.toString();
                  }

                  return null;
                }
                catch (KeymapJsonUtils.ParseError e)
                {
                  return e.getMessage();
                }
              }
            });
  }

  /** Persists [json] under [name], ensures _values has exactly one
   KeymapEntry row for it (removing any row for the old name on
   rename, and any PRE-EXISTING row that already had [name] on
   overwrite - deliberately bypassing add_item/change_item, which
   only know "append" or "replace one index" and previously caused
   overwrite to leave a duplicate row instead of replacing it). */
  void finish_save_keymap(String existing_name, String name, String json)
  {
    if (existing_name != null && !existing_name.equals(name))
      KeymapManager.remove(getContext(), existing_name);

    KeymapManager.add(getContext(), new KeymapManager.StoredKeymap(name, json));

    for (int i = _values.size() - 1; i >= 0; i--)
    {
      Layout v = _values.get(i);
      if (v instanceof KeymapEntry)
      {
        String n = ((KeymapEntry)v).name;
        if (n.equals(name) || (existing_name != null && n.equals(existing_name)))
          _values.remove(i);
      }
    }

    add_item(new KeymapEntry(name));
  }

  /** How many CustomLayout entries in _values reference [keymap_name]
   via their keymap="..." XML attribute. */
  int count_keymap_references(String keymap_name)
  {
    int count = 0;
    for (Layout v : _values)
      if (v instanceof CustomLayout
              && keymap_name.equals(KeymapXmlAttrUtils.get_keymap_attr(((CustomLayout)v).xml)))
        count++;
    return count;
  }

  /** Strips the keymap/swipekeymap attributes from every CustomLayout in
   _values that references [keymap_name], and persists the change. */
  void clear_keymap_references(String keymap_name)
  {
    boolean changed = false;
    for (int i = 0; i < _values.size(); i++)
    {
      Layout v = _values.get(i);
      if (v instanceof CustomLayout)
      {
        CustomLayout cl = (CustomLayout)v;
        if (keymap_name.equals(KeymapXmlAttrUtils.get_keymap_attr(cl.xml)))
        {
          _values.set(i, CustomLayout.parse(KeymapXmlAttrUtils.remove_keymap_attrs(cl.xml)));
          changed = true;
        }
      }
    }
    if (changed)
      set_values(_values, true);
  }

  /** Renames every keymap="..." reference from [old_name] to [new_name]
   across stored CustomLayout entries, reading/writing
   SharedPreferences directly. Used from KeymapBuilderActivity, a
   separate Activity with no live LayoutsPreference instance to route
   the change through the normal in-memory _values/set_values path.
   See reload_from_preferences_and_sync() for how a paused Settings
   screen picks this up afterward. Returns the number updated. */
  public static int rename_keymap_references_in_preferences(Context ctx, String old_name, String new_name)
  {
    SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
    String raw = prefs.getString(KEY, null);
    if (raw == null)
      return 0;
    List<Layout> values = load_from_string(raw, SERIALIZER);
    if (values == null)
      return 0;

    int updated = 0;
    for (int i = 0; i < values.size(); i++)
    {
      Layout v = values.get(i);
      if (v instanceof CustomLayout)
      {
        CustomLayout cl = (CustomLayout)v;
        if (old_name.equals(KeymapXmlAttrUtils.get_keymap_attr(cl.xml)))
        {
          values.set(i, CustomLayout.parse(KeymapXmlAttrUtils.set_keymap_attr(cl.xml, new_name)));
          updated++;
        }
      }
    }

    if (updated > 0)
      prefs.edit().putString(KEY, save_to_string(values, SERIALIZER)).apply();

    return updated;
  }

  class LayoutsAddButton extends AddButton
  {
    public LayoutsAddButton(Context ctx)
    {
      super(ctx);
      setLayoutResource(R.layout.pref_layouts_add_btn);
      setTitle(R.string.pref_layouts_add);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
      super.onBindViewHolder(holder);
      tint_title(holder, R.color.settings_primary);
    }
  }

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
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
      super.onBindViewHolder(holder);
      tint_title(holder, R.color.settings_secondary);
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
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
      super.onBindViewHolder(holder);
      tint_title(holder, R.color.settings_tertiary);
    }

    @Override
    protected void onClick()
    {
      Intent intent = new Intent(getContext(), KeymapBuilderActivity.class);
      getContext().startActivity(intent);
    }
  }

  static void tint_title(PreferenceViewHolder holder, int color_res)
  {
    View title = holder.findViewById(android.R.id.title);
    if (title instanceof TextView)
      ((TextView)title).setTextColor(ContextCompat.getColor(title.getContext(), color_res));
  }

  /** Small non-clickable caption ("Keymaps") shown once, above the keymap
      rows, so the layout list and the keymap list read as two distinct
      sections instead of one flat list. Reuses the same caption look as the
      real PreferenceCategory headers (pref_category_settings.xml). */
  class KeymapsSectionHeader extends Preference
  {
    public KeymapsSectionHeader(Context ctx)
    {
      super(ctx);
      setPersistent(false);
      setSelectable(false);
      setLayoutResource(R.layout.pref_category_settings);
      setTitle(R.string.pref_layouts_keymaps_header);
    }
  }

  public interface Layout {}

  public static final class SystemLayout implements Layout
  {
    public SystemLayout() {}
  }

  public static final class NamedLayout implements Layout
  {
    public final String name;
    public NamedLayout(String n) { name = n; }
  }

  public static final class CustomLayout implements Layout
  {
    public final String xml;
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

  public static final class KeymapEntry implements Layout
  {
    public final String name;
    public KeymapEntry(String n) { name = n; }
  }

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
        return new JSONObject().put("kind", "custom").put("xml", ((CustomLayout)v).xml);
      if (v instanceof KeymapEntry)
        return new JSONObject().put("kind", "keymap").put("name", ((KeymapEntry)v).name);
      return new JSONObject().put("kind", "system");
    }
  }
}