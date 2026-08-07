package com.adiraimaji.customkeyboard;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import com.adiraimaji.customkeyboard.prefs.LayoutsPreference;

public class SettingsActivity extends PreferenceActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    // Picks up writes made directly to SharedPreferences elsewhere (e.g.
    // keymap rename propagation from KeymapBuilderActivity, a separate
    // Activity) that this screen's in-memory state wouldn't otherwise
    // reflect while paused.
    Preference p = findPreference("layouts");
    if (p instanceof LayoutsPreference)
      ((LayoutsPreference)p).reload_from_preferences_and_sync();
  }

  void fallbackEncrypted()
  {
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
            .copy_preferences_to_protected_storage(this,
                    getPreferenceManager().getSharedPreferences());
    super.onStop();
  }
}