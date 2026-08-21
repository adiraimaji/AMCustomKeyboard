package com.adiraimaji.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;

import com.adiraimaji.customkeyboard.DialogUtils;
import com.adiraimaji.customkeyboard.R;
import com.adiraimaji.customkeyboard.UpdateChecker;
import com.adiraimaji.customkeyboard.UpdateInstaller;

/** Settings row (placed in its own "About" category, at the very
 bottom of the settings screen) that checks
 https://github.com/adiraimaji/AMCustomKeyboard for a newer release
 than the one currently installed. The summary always shows the
 currently installed version; tapping the row re-checks and, if a
 newer release exists, shows a dialog with its release notes and a
 "Download now" button that hands off to [UpdateInstaller] to
 download the APK in-app and launch the system installer on it. */
public class CheckUpdatePreference extends Preference
{
    public CheckUpdatePreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setPersistent(false);
        show_current_version_summary();
    }

    private void show_current_version_summary()
    {
        setSummary(getContext().getString(R.string.pref_check_update_summary_current,
                UpdateChecker.current_version_name(getContext())));
    }

    @Override
    protected void onClick()
    {
        final Context ctx = getContext();
        setSummary(R.string.pref_check_update_checking);
        UpdateChecker.check(ctx, (latest, update_available, error) ->
        {
            if (error != null)
            {
                show_current_version_summary();
                Toast.makeText(ctx, R.string.pref_check_update_error, Toast.LENGTH_LONG).show();
                return;
            }
            if (!update_available)
            {
                show_current_version_summary();
                Toast.makeText(ctx,
                        ctx.getString(R.string.pref_check_update_uptodate, UpdateChecker.current_version_name(ctx)),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            show_current_version_summary();
            show_update_dialog(ctx, latest);
        });
    }

    private void show_update_dialog(final Context ctx, final UpdateChecker.ReleaseInfo latest)
    {
        String version_line = ctx.getString(R.string.update_dialog_version_line,
                UpdateChecker.current_version_name(ctx), latest.version_name());
        String notes = latest.release_notes == null || latest.release_notes.trim().isEmpty()
                ? ctx.getString(R.string.update_dialog_no_notes)
                : latest.release_notes.trim();

        int pad = DialogUtils.dp(ctx, 20);
        TextView body = new TextView(ctx);
        body.setText(ctx.getString(R.string.update_dialog_message) + "\n\n" + version_line + "\n\n" + notes);
        body.setTextColor(Color.BLACK);
        body.setTextSize(14f);
        body.setPadding(pad, DialogUtils.dp(ctx, 12), pad, DialogUtils.dp(ctx, 4));
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.update_dialog_title)
                .setView(scroll)
                .setPositiveButton(R.string.update_dialog_download_now, (d, w) -> UpdateInstaller.start(ctx, latest))
                .setNegativeButton(R.string.update_dialog_later, null)
                .show();
        DialogUtils.apply_modern_style(dialog, ctx);
    }
}
