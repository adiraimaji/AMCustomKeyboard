package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import java.util.ArrayList;
import java.util.List;
import com.adiraimaji.customkeyboard.*;
import org.json.JSONArray;
import org.json.JSONException;

/** A list of preferences where the users can add items to the end and modify
    and remove items. Backed by a string list. Implement user selection in
    [select()]. */
public abstract class ListGroupPreference<E> extends PreferenceGroup
{
  boolean _attached = false;
  List<E> _values;
  /** The "add" button currently displayed. */
  AddButton _add_button = null;

  /** Extra buttons displayed after the "add" button, e.g. a dedicated
   "Add new Keymap JSON" button in LayoutsPreference. Empty by default. */
  List<Preference> _extra_buttons = null;

  public ListGroupPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setOrderingAsAdded(true);
    setLayoutResource(R.layout.pref_listgroup_group);
    _values = new ArrayList<E>();
  }

  /** Called every time the list changes, after the "Add" button. Allows a
   subclass to attach one or more extra bottom buttons that are NOT
   backed by an item in [_values] (unlike AddButton, which always adds
   to the list). [prev_buttons] is the previously attached list, might
   be null. Return null or an empty list for none. */
  List<Preference> on_attach_extra_buttons(List<Preference> prev_buttons)
  {
    return null;
  }

  /** Overrideable */

  /** The label to display on the item for a given value. */
  abstract String label_of_value(E value, int i);

  /** The icon resource id to display on the item for a given value.
   Overrideable - defaults to the same generic icon used everywhere
   else in Settings. A subclass (e.g. LayoutsPreference) can return
   something more specific per item, such as a different icon for a
   Keymap row than for a plain Layout row. */
  int icon_of_value(E value, int i)
  {
    return R.drawable.ic_pref_default;
  }


  /** Called every time the list changes and allows to change the "Add" button
      appearance.
      [prev_btn] is the previously attached button, might be null. */
  AddButton on_attach_add_button(AddButton prev_btn)
  {
    if (prev_btn == null)
      return new AddButton(getContext());
    return prev_btn;
  }

  /** Called every time the list changes and allows to disable the "Remove"
      buttons on every items. Might be used to enforce a minimum number of
      items. */
  boolean should_allow_remove_item(E _value)
  {
    return true;
  }

  /** Called when an item is added or modified. [old_value] is [null] if the
      item is being added. */
  abstract void select(SelectionCallback<E> callback, E old_value);

  /** A separate class is used as the same serializer must be used in the
      static context. See [Serializer] below. */
  abstract Serializer<E> get_serializer();

  /** Load/save utils */

  /** Read a value saved by preference from a [SharedPreferences] object.
      [serializer] must be the same that is returned by [get_serializer()].
      Returns [null] on error. */
  static <E> List<E> load_from_preferences(String key,
      SharedPreferences prefs, List<E> def, Serializer<E> serializer)
  {
    String s = prefs.getString(key, null);
    return (s != null) ? load_from_string(s, serializer) : def;
  }

  /** Save items into the preferences. Does not call [prefs.commit()]. */
  static <E> void save_to_preferences(String key, SharedPreferences.Editor prefs, List<E> items, Serializer<E> serializer)
  {
    prefs.putString(key, save_to_string(items, serializer));
  }

  /** Decode a list of string previously encoded with [save_to_string]. Returns
      [null] on error. */
  static <E> List<E> load_from_string(String inp, Serializer<E> serializer)
  {
    try
    {
      List<E> l = new ArrayList<E>();
      JSONArray arr = new JSONArray(inp);
      for (int i = 0; i < arr.length(); i++)
        l.add(serializer.load_item(arr.get(i)));
      return l;
    }
    catch (JSONException e)
    {
      Logs.exn("load_from_string", e);
      return null;
    }
  }

  /** Encode a list of string so it can be passed to
      [Preference.persistString()]. Decode with [load_from_string]. */
  static <E> String save_to_string(List<E> items, Serializer<E> serializer)
  {
    List<Object> serialized_items = new ArrayList<Object>();
    for (E it : items)
    {
      try
      {
        serialized_items.add(serializer.save_item(it));
      }
      catch (JSONException e)
      {
        Logs.exn("save_to_string", e);
      }
    }
    return (new JSONArray(serialized_items)).toString();
  }

  /** Protected API */

  /** Set the values. If [persist] is [true], persist into the store. */
  void set_values(List<E> vs, boolean persist)
  {
    _values = vs;
    reattach();
    if (persist)
      persistString(save_to_string(vs, get_serializer()));
  }

  void add_item(E v)
  {
    _values.add(v);
    set_values(_values, true);
  }

  void change_item(int i, E v)
  {
    _values.set(i, v);
    set_values(_values, true);
  }

  void remove_item(int i)
  {
    _values.remove(i);
    set_values(_values, true);
  }

  /** Internal */

  @Override
  protected void onSetInitialValue(Object defaultValue)
  {
    String input = isPersistent() ? getPersistedString(null) : (String)defaultValue;
    if (input != null)
    {
      List<E> values = load_from_string(input, get_serializer());
      if (values != null)
        set_values(values, false);
    }
  }

  @Override
  public void onAttached()
  {
    super.onAttached();
    if (_attached)
      return;
    _attached = true;
    reattach();
  }

  void reattach()
  {
    if (!_attached)
      return;
    removeAll();
    int i = 0;
    for (E v : _values)
    {
      addPreference(this.new Item(getContext(), i, v));
      i++;
    }
    _add_button = on_attach_add_button(_add_button);
    _add_button.setOrder(Preference.DEFAULT_ORDER);
    addPreference(_add_button);
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

  class Item extends Preference
  {
    final E _value;
    final int _index;

    public Item(Context ctx, int index, E value)
    {
      super(ctx);
      _value = value;
      _index = index;
      setPersistent(false);
      setTitle(label_of_value(value, index));
      setIcon(icon_of_value(value, index));
      if (should_allow_remove_item(value))
        setWidgetLayoutResource(R.layout.pref_listgroup_item_widget);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
      super.onBindViewHolder(holder);
      View remove_btn = holder.findViewById(R.id.pref_listgroup_remove_btn);
      if (remove_btn != null)
        remove_btn.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View _v)
          {
            remove_item(_index);
          }
        });
      holder.itemView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View _v)
        {
          select(new SelectionCallback<E>() {
            public void select(E value)
            {
              if (value == null)
                remove_item(_index);
              else
                change_item(_index, value);
            }

            public boolean allow_remove() { return true; }
          }, _value);
        }
      });
    }
  }

  class AddButton extends Preference
  {
    public AddButton(Context ctx)
    {
      super(ctx);
      setPersistent(false);
      setLayoutResource(R.layout.pref_listgroup_add_btn);
    }

    @Override
    protected void onClick()
    {
      select(new SelectionCallback<E>() {
        public void select(E value)
        {
          add_item(value);
        }

        public boolean allow_remove() { return false; }
      }, null);
    }
  }

  public interface SelectionCallback<E>
  {
    public void select(E value);

    /** If this method returns [true], [null] might be passed to [select] to
        remove the item. */
    public boolean allow_remove();
  }

  /** Methods for serializing and deserializing abstract items.
      [StringSerializer] is an implementation. */
  public interface Serializer<E>
  {
    /** [obj] is an object returned by [save_item()]. */
    E load_item(Object obj) throws JSONException;

    /** Serialize an item into JSON. Might return an object that can be inserted
        in a [JSONArray]. */
    Object save_item(E v) throws JSONException;
  }

  public static class StringSerializer implements Serializer<String>
  {
    public String load_item(Object obj) { return (String)obj; }
    public Object save_item(String v) { return v; }
  }
}
