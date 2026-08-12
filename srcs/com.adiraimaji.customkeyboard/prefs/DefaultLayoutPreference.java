package com.adiraimaji.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.adiraimaji.customkeyboard.KeyboardData;
import com.adiraimaji.customkeyboard.R;

import java.util.ArrayList;
import java.util.List;

/** Lets the user pin a specific configured layout as the ALWAYS-USED
 default whenever the keyboard opens, instead of the normal
 "remember whichever layout was last active" behavior. Persisted as
 an int: -1 means "Last used layout" (default, existing behavior
 unchanged); >= 0 is an index into the same layout list
 Config.layouts uses - see Keyboard2.onStartInputView(), which
 applies it. */
public class DefaultLayoutPreference extends Preference
{
    private static final String KEY = "default_layout_index";

    public DefaultLayoutPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setKey(KEY);
    }

    @Override
    protected Object onGetDefaultValue(android.content.res.TypedArray a, int index)
    {
        return -1;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue)
    {
        int def = (defaultValue instanceof Integer) ? (Integer)defaultValue : -1;
        int value = isPersistent() ? getPersistedInt(def) : def;
        refresh_summary(value);
    }

    private List<String> build_labels()
    {
        Resources res = getContext().getResources();
        SharedPreferences prefs = getSharedPreferences();
        List<KeyboardData> layouts = LayoutsPreference.load_from_preferences(res, prefs);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++)
        {
            KeyboardData kd = layouts.get(i);
            String name = (kd != null && kd.name != null && !kd.name.isEmpty())
                    ? kd.name : res.getString(R.string.pref_layout_e_system);
            labels.add(res.getString(R.string.pref_layouts_item, i + 1, name));
        }
        return labels;
    }

    private int get_current_value()
    {
        return getSharedPreferences().getInt(KEY, -1);
    }

    private void refresh_summary(int value)
    {
        List<String> labels = build_labels();
        String summary = (value < 0 || value >= labels.size())
                ? getContext().getString(R.string.pref_default_layout_last_used)
                : labels.get(value);
        setSummary(summary);
    }

    @Override
    protected void onClick()
    {
        final List<String> labels = build_labels();
        final String[] entries = new String[labels.size() + 1];
        entries[0] = getContext().getString(R.string.pref_default_layout_last_used);
        for (int i = 0; i < labels.size(); i++)
            entries[i + 1] = labels.get(i);

        int current = get_current_value();
        int checked_item = current < 0 ? 0 : Math.min(current + 1, entries.length - 1);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.pref_default_layout_title)
                .setSingleChoiceItems(entries, checked_item, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        int new_value = which - 1; // -1 = last used, else layout index.
                        persistInt(new_value);
                        refresh_summary(new_value);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}