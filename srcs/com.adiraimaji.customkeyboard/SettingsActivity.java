package com.adiraimaji.customkeyboard;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.adiraimaji.customkeyboard.prefs.LayoutsPreference;

public class SettingsActivity extends PreferenceActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
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
    // KeymapBuilderActivity (and anything else that saves a keymap outside
    // LayoutsPreference's own dialogs) only triggers onResume() when we
    // return to this screen, not a fresh onCreate()/onSetInitialValue().
    // Re-sync here so any newly created keymap shows up immediately.
    Preference p = findPreference("layouts");
    if (p instanceof LayoutsPreference)
      ((LayoutsPreference)p).refresh_keymap_entries();
  }

  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
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