package com.adiraimaji.customkeyboard;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;

/** Downloads a release APK entirely in-app (via the system
 [DownloadManager], so the user never has to leave the app or a
 browser tab) and, once it finishes, launches the system package
 installer against it. Requires the REQUEST_INSTALL_PACKAGES
 manifest permission (declared in AndroidManifest.xml) plus, on API
 26+, the user separately granting "install unknown apps" for this
 app - [start] checks that and shows [show_permission_dialog]
 instead of downloading if it isn't granted yet, rather than
 downloading a file the system will then silently refuse to let the
 user install. */
public final class UpdateInstaller
{
    private static final String DOWNLOADED_APK_FILENAME = "AMCustomKeyboard.apk";

    /** Starts the download-then-install flow for [release]. Safe to
     call from a UI click handler; shows its own dialogs/toasts for
     every outcome (missing asset, missing permission, download
     failure) rather than returning anything for the caller to
     handle. */
    public static void start(final Context ctx, final UpdateChecker.ReleaseInfo release)
    {
        if (release.apk_download_url == null)
        {
            Toast.makeText(ctx, R.string.update_download_failed_toast, Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.getPackageManager().canRequestPackageInstalls())
        {
            show_permission_dialog(ctx, release);
            return;
        }

        enqueue_download(ctx, release);
    }

    private static void show_permission_dialog(final Context ctx, final UpdateChecker.ReleaseInfo release)
    {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setPositiveButton(R.string.update_install_permission_open_settings, (dialog, which) ->
                {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + ctx.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(intent); }
                    catch (Exception e) { /* Device without this settings screen - nothing more we can do. */ }
                })
                .setNegativeButton(R.string.update_install_permission_cancel, null)
                .show();
    }

    private static void enqueue_download(final Context ctx, final UpdateChecker.ReleaseInfo release)
    {
        final Context app_ctx = ctx.getApplicationContext();
        DownloadManager dm = (DownloadManager)app_ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null)
        {
            Toast.makeText(ctx, R.string.update_download_failed_toast, Toast.LENGTH_LONG).show();
            return;
        }

        // Any previous partial/stale download under the same name is
        // replaced, not appended to.
        File existing = new File(app_ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOADED_APK_FILENAME);
        if (existing.exists())
            existing.delete();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apk_download_url));
        request.setTitle(app_ctx.getString(R.string.update_download_notification_title));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(app_ctx, Environment.DIRECTORY_DOWNLOADS, DOWNLOADED_APK_FILENAME);
        request.setMimeType("application/vnd.android.package-archive");
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);

        final long download_id = dm.enqueue(request);

        final BroadcastReceiver receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context receiver_ctx, Intent intent)
            {
                long finished_id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (finished_id != download_id)
                    return;
                app_ctx.unregisterReceiver(this);
                handle_download_finished(app_ctx, download_id);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(app_ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED);

        Toast.makeText(ctx, R.string.update_download_started_toast, Toast.LENGTH_SHORT).show();
    }

    private static void handle_download_finished(Context app_ctx, long download_id)
    {
        DownloadManager dm = (DownloadManager)app_ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null)
            return;

        Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(download_id));
        if (cursor == null)
            return;
        try
        {
            if (!cursor.moveToFirst())
                return;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL)
            {
                Toast.makeText(app_ctx, R.string.update_download_failed_toast, Toast.LENGTH_LONG).show();
                return;
            }

            File apk_file = new File(app_ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOADED_APK_FILENAME);
            if (!apk_file.exists())
            {
                Toast.makeText(app_ctx, R.string.update_download_failed_toast, Toast.LENGTH_LONG).show();
                return;
            }

            Uri content_uri = FileProvider.getUriForFile(app_ctx, app_ctx.getPackageName() + ".fileprovider", apk_file);
            Intent install_intent = new Intent(Intent.ACTION_VIEW);
            install_intent.setDataAndType(content_uri, "application/vnd.android.package-archive");
            install_intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            app_ctx.startActivity(install_intent);
        }
        finally
        {
            cursor.close();
        }
    }

    private UpdateInstaller() {}
}
