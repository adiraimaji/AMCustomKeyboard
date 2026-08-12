package com.adiraimaji.customkeyboard;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.adiraimaji.customkeyboard.prefs.LayoutsPreference;

public class SettingsActivity extends AppCompatActivity
  implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);
    Toolbar toolbar = findViewById(R.id.settings_toolbar);
    setSupportActionBar(toolbar);
    setTitle(R.string.app_name);

    SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
    try
    {
      Config.migrate(prefs);
    }
    catch (Exception _e) { fallbackEncrypted(); return; }

    getSupportFragmentManager().addOnBackStackChangedListener(this::update_navigation);
    if (savedInstanceState == null)
    {
      getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.settings_container, new SettingsFragment())
        .commit();
    }
    update_navigation();
  }

  void update_navigation()
  {
    boolean can_go_back = getSupportFragmentManager().getBackStackEntryCount() > 0;
    if (getSupportActionBar() != null)
      getSupportActionBar().setDisplayHomeAsUpEnabled(can_go_back);
  }

  @Override
  public boolean onSupportNavigateUp()
  {
    FragmentManager fm = getSupportFragmentManager();
    if (fm.getBackStackEntryCount() > 0)
    {
      fm.popBackStack();
      return true;
    }
    return super.onSupportNavigateUp();
  }

  /** Navigates into a nested <PreferenceScreen> (e.g. "Add keys to the
   keyboard", "Bottom margin"). Requires the PreferenceScreen to have an
   android:key set in res/xml/settings.xml - see SettingsFragment. */
  @Override
  public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref)
  {
    SettingsFragment fragment = new SettingsFragment();
    Bundle args = new Bundle();
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
    fragment.setArguments(args);
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.settings_container, fragment, pref.getKey())
      .addToBackStack(pref.getKey())
      .commit();
    setTitle(pref.getTitle());
    return true;
  }

  void fallbackEncrypted()
  {
    finish();
  }

  @Override
  protected void onStop()
  {
    DirectBootAwarePreferences
            .copy_preferences_to_protected_storage(this,
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));
    super.onStop();
  }

  /** The single PreferenceFragmentCompat used for both the root screen and
   every nested <PreferenceScreen> (distinguished by ARG_PREFERENCE_ROOT). */
  public static class SettingsFragment extends PreferenceFragmentCompat
  {
    boolean _is_root;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
      setPreferencesFromResource(R.xml.settings, rootKey);
      apply_default_icons(getPreferenceScreen());
      _is_root = (rootKey == null);
      if (_is_root)
      {
        boolean foldableDevice = FoldStateTracker.isFoldableDevice(requireContext());
        set_enabled_if_present("margin_bottom_portrait_unfolded", foldableDevice);
        set_enabled_if_present("margin_bottom_landscape_unfolded", foldableDevice);
        set_enabled_if_present("horizontal_margin_portrait_unfolded", foldableDevice);
        set_enabled_if_present("horizontal_margin_landscape_unfolded", foldableDevice);
        set_enabled_if_present("keyboard_height_unfolded", foldableDevice);
        set_enabled_if_present("keyboard_height_landscape_unfolded", foldableDevice);
      }
    }

    void set_enabled_if_present(String key, boolean enabled)
    {
      Preference p = findPreference(key);
      if (p != null)
        p.setEnabled(enabled);
    }

    /** AndroidX hides a row's icon view whenever Preference.getIcon() is
        null (blank reserved space otherwise) - see pref_item_settings.xml.
        This walks every preference declared in res/xml/settings.xml (this
        covers everything except a handful of rows built at runtime in Java,
        which set their own icon directly - see ListGroupPreference.Item and
        ExtraKeysPreference.ExtraKeyCheckBoxPreference) and gives it the one
        default icon if it doesn't already have one. */
    static void apply_default_icons(Preference pref)
    {
      if (pref == null)
        return;
      if (pref.getIcon() == null)
        pref.setIcon(R.drawable.ic_pref_default);
      if (pref instanceof PreferenceGroup)
      {
        PreferenceGroup group = (PreferenceGroup)pref;
        for (int i = 0; i < group.getPreferenceCount(); i++)
          apply_default_icons(group.getPreference(i));
      }
    }

    @Override
    public void onResume()
    {
      super.onResume();
      // Picks up writes made directly to SharedPreferences elsewhere (e.g.
      // keymap rename propagation from KeymapBuilderActivity, a separate
      // Activity) that this screen's in-memory state wouldn't otherwise
      // reflect while paused. Only meaningful on the root screen, where the
      // "layouts" preference lives.
      if (!_is_root)
        return;
      Preference p = findPreference("layouts");
      if (p instanceof LayoutsPreference)
        ((LayoutsPreference)p).reload_from_preferences_and_sync();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference)
    {
      if (preference instanceof com.adiraimaji.customkeyboard.prefs.IntSlideBarPreference)
      {
        androidx.fragment.app.DialogFragment f =
          com.adiraimaji.customkeyboard.prefs.IntSlideBarPreferenceDialogFragment
            .newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
      }
      else if (preference instanceof com.adiraimaji.customkeyboard.prefs.SlideBarPreference)
      {
        androidx.fragment.app.DialogFragment f =
          com.adiraimaji.customkeyboard.prefs.SlideBarPreferenceDialogFragment
            .newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
      }
      else
      {
        super.onDisplayPreferenceDialog(preference);
      }
    }
  }
}
