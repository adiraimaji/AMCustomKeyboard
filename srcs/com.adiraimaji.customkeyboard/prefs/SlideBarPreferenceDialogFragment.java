package com.adiraimaji.customkeyboard.prefs;

import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.adiraimaji.customkeyboard.R;

/** Dialog shown by SlideBarPreference. Updates the summary live while
    dragging, like the previous (pre-AndroidX) implementation did. */
public class SlideBarPreferenceDialogFragment extends PreferenceDialogFragmentCompat
{
  private SeekBar _seekBar;
  private TextView _textView;
  private float _min;
  private float _max;
  private float _value;
  private String _initialSummary;

  public static SlideBarPreferenceDialogFragment newInstance(String key)
  {
    SlideBarPreferenceDialogFragment f = new SlideBarPreferenceDialogFragment();
    Bundle b = new Bundle(1);
    b.putString(ARG_KEY, key);
    f.setArguments(b);
    return f;
  }

  @Override
  protected void onBindDialogView(View view)
  {
    super.onBindDialogView(view);
    SlideBarPreference pref = (SlideBarPreference)getPreference();
    _min = pref.getMin();
    _max = pref.getMax();
    _initialSummary = pref.getInitialSummary();
    _value = pref.getValue();
    _textView = view.findViewById(R.id.pref_slider_text);
    _seekBar = view.findViewById(R.id.pref_slider_seekbar);
    _seekBar.setMax(SlideBarPreference.STEPS);
    _seekBar.setProgress((int)((_value - _min) * SlideBarPreference.STEPS / (_max - _min)));
    updateText();
    _seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        _value = Math.round(progress * (_max - _min)) / (float)SlideBarPreference.STEPS + _min;
        updateText();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void updateText()
  {
    if (_textView != null)
      _textView.setText(String.format(_initialSummary, _value));
  }

  @Override
  public void onDialogClosed(boolean positiveResult)
  {
    SlideBarPreference pref = (SlideBarPreference)getPreference();
    if (positiveResult && _seekBar != null)
    {
      if (pref.callChangeListener(_value))
        pref.setValue(_value);
    }
  }
}
