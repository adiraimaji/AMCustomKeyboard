package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;

/*
 ** SlideBarPreference
 ** -
 ** Open a dialog showing a seekbar for a float value. The dialog itself is
 ** implemented in SlideBarPreferenceDialogFragment (AndroidX Preference
 ** dialogs are owned by a DialogFragment, not by the Preference).
 ** -
 ** xml attrs:
 **   android:defaultValue  Default value (float)
 **   min                   min value (float)
 **   max                   max value (float)
 ** -
 ** Summary field allow to show the current value using %f or %s flag
 */
public class SlideBarPreference extends DialogPreference
{
  public static final int STEPS = 100;

  private final float _min;
  private final float _max;
  private final String _initialSummary;

  public SlideBarPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    CharSequence summary = getSummary();
    _initialSummary = (summary == null) ? "%s" : summary.toString();
    _min = float_of_string(attrs.getAttributeValue(null, "min"));
    _max = Math.max(1f, float_of_string(attrs.getAttributeValue(null, "max")));
    setDialogLayoutResource(com.adiraimaji.customkeyboard.R.layout.pref_dialog_slider);
  }

  public float getMin() { return _min; }
  public float getMax() { return _max; }
  public String getInitialSummary() { return _initialSummary; }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index)
  {
    return a.getFloat(index, _min);
  }

  @Override
  protected void onSetInitialValue(Object defaultValue)
  {
    float value;
    if (isPersistent())
      value = getPersistedFloat((defaultValue instanceof Float) ? (Float)defaultValue : _min);
    else
      value = (defaultValue instanceof Float) ? (Float)defaultValue : _min;
    setValue(value);
  }

  /** Current value, from the persisted store. */
  public float getValue()
  {
    return getPersistedFloat(_min);
  }

  /** Persists [value] and refreshes the summary shown in the list. */
  public void setValue(float value)
  {
    persistFloat(value);
    setSummary(String.format(_initialSummary, value));
  }

  private static float float_of_string(String str)
  {
    if (str == null)
      return 0f;
    return Float.parseFloat(str);
  }
}
