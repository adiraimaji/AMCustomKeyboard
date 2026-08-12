package com.adiraimaji.customkeyboard.prefs;

import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.adiraimaji.customkeyboard.R;

/** Dialog shown by IntSlideBarPreference. Updates the summary live while
    dragging, like the previous (pre-AndroidX) implementation did. */
public class IntSlideBarPreferenceDialogFragment extends PreferenceDialogFragmentCompat
{
  private SeekBar _seekBar;
  private TextView _textView;
  private int _min;
  private String _initialSummary;

  public static IntSlideBarPreferenceDialogFragment newInstance(String key)
  {
    IntSlideBarPreferenceDialogFragment f = new IntSlideBarPreferenceDialogFragment();
    Bundle b = new Bundle(1);
    b.putString(ARG_KEY, key);
    f.setArguments(b);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onBindDialogView(View view)
  {
    super.onBindDialogView(view);
    IntSlideBarPreference pref = (IntSlideBarPreference)getPreference();
    _min = pref.getMin();
    _initialSummary = pref.getInitialSummary();
    _textView = view.findViewById(R.id.pref_slider_text);
    _seekBar = view.findViewById(R.id.pref_slider_seekbar);
    _seekBar.setMax(pref.getMax() - pref.getMin());
    int current = pref.getValue();
    _seekBar.setProgress(current - _min);
    updateText(current);
    _seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        updateText(progress + _min);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void updateText(int value)
  {
    if (_textView != null)
      _textView.setText(String.format(_initialSummary, value));
  }

  @Override
  public void onDialogClosed(boolean positiveResult)
  {
    IntSlideBarPreference pref = (IntSlideBarPreference)getPreference();
    if (positiveResult && _seekBar != null)
    {
      int value = _seekBar.getProgress() + _min;
      if (pref.callChangeListener(value))
        pref.setValue(value);
    }
  }
}
