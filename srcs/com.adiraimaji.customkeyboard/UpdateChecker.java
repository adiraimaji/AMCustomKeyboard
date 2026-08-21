package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks GitHub's "latest release" API for
 https://github.com/adiraimaji/AMCustomKeyboard and reports back
 whether a newer version than the one currently installed is
 available - used by [CheckUpdatePreference]. The APK asset in each
 release is always named exactly "AMCustomKeyboard.apk" (the version
 lives in the release tag, not the filename), so [fetch_latest_release]
 looks for that name specifically, falling back to the first ".apk"
 asset it finds if a release is ever published without one exactly
 matching (rather than reporting "no update" just because of that). */
public final class UpdateChecker
{
    private static final String REPO_OWNER = "adiraimaji";
    private static final String REPO_NAME = "AMCustomKeyboard";
    private static final String LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    /** Filename every release's APK asset is published under. */
    public static final String EXPECTED_APK_ASSET_NAME = "AMCustomKeyboard.apk";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static final class ReleaseInfo
    {
        /** Raw tag, e.g. "v1.3.0" or "1.3.0". */
        public final String tag_name;
        /** Release title, falls back to [tag_name] if the release has none. */
        public final String release_name;
        /** Markdown release notes ("body" in the GitHub API), never null (empty if none). */
        public final String release_notes;
        /** Direct download URL for the APK asset, or null if the release has no APK attached at all. */
        public final String apk_download_url;

        ReleaseInfo(String tag_name_, String release_name_, String release_notes_, String apk_download_url_)
        {
            tag_name = tag_name_;
            release_name = release_name_;
            release_notes = release_notes_;
            apk_download_url = apk_download_url_;
        }

        /** [tag_name] with a leading "v"/"V" stripped, for display and
         version comparison - GitHub tags conventionally include it,
         the app's own versionName does not. */
        public String version_name()
        {
            if (tag_name.length() > 0 && (tag_name.charAt(0) == 'v' || tag_name.charAt(0) == 'V'))
                return tag_name.substring(1);
            return tag_name;
        }
    }

    public interface Callback
    {
        /** Called on the main thread. Exactly one of [error] or
         [latest] is non-null. [update_available] is only meaningful
         when [error] is null. */
        void on_result(ReleaseInfo latest, boolean update_available, Exception error);
    }

    /** Fetches the latest GitHub release and compares it against this
     app's own installed versionName (read from PackageManager, so it
     always matches whatever build actually shipped - debug builds
     included). Network + parsing happen on a background thread;
     [cb] fires on the main thread either way. */
    public static void check(final Context ctx, final Callback cb)
    {
        final Context app_ctx = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() ->
        {
            try
            {
                final ReleaseInfo latest = fetch_latest_release();
                final String current = current_version_name(app_ctx);
                final boolean update_available = compare_versions(current, latest.version_name()) < 0;
                main.post(() -> cb.on_result(latest, update_available, null));
            }
            catch (final Exception e)
            {
                main.post(() -> cb.on_result(null, false, e));
            }
        });
    }

    public static String current_version_name(Context ctx)
    {
        try
        {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "?";
        }
        catch (PackageManager.NameNotFoundException e)
        {
            return "?";
        }
    }

    private static ReleaseInfo fetch_latest_release() throws IOException
    {
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection)new URL(LATEST_RELEASE_API_URL).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            // GitHub's API rejects requests with no User-Agent.
            conn.setRequestProperty("User-Agent", "AMCustomKeyboard-UpdateChecker");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK)
                throw new IOException("GitHub API returned HTTP " + code);

            String body = read_all(conn.getInputStream());
            JSONObject obj = new JSONObject(body);
            String tag = obj.optString("tag_name", "");
            if (tag.isEmpty())
                throw new IOException("Release response had no \"tag_name\"");
            String name = obj.optString("name", "");
            if (name.isEmpty())
                name = tag;
            String notes = obj.optString("body", "");

            String apk_url = null;
            JSONArray assets = obj.optJSONArray("assets");
            if (assets != null)
            {
                // First pass: the exact expected asset name.
                for (int i = 0; i < assets.length() && apk_url == null; i++)
                {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null)
                        continue;
                    String asset_name = asset.optString("name", "");
                    if (asset_name.equalsIgnoreCase(EXPECTED_APK_ASSET_NAME))
                        apk_url = asset.optString("browser_download_url", null);
                }
                // Fallback: any ".apk" asset, in case a release is
                // ever published without the usual exact name.
                for (int i = 0; i < assets.length() && apk_url == null; i++)
                {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null)
                        continue;
                    String asset_name = asset.optString("name", "");
                    if (asset_name.toLowerCase(java.util.Locale.US).endsWith(".apk"))
                        apk_url = asset.optString("browser_download_url", null);
                }
            }

            return new ReleaseInfo(tag, name, notes, apk_url);
        }
        catch (org.json.JSONException je)
        {
            throw new IOException("Could not parse GitHub API response: " + je.getMessage(), je);
        }
        finally
        {
            if (conn != null)
                conn.disconnect();
        }
    }

    private static String read_all(InputStream in) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1)
            out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    /** Compares 2 dot-separated version strings numerically component
     by component (e.g. "1.9.0" &lt; "1.10.0", unlike a plain string
     compare), padding the shorter one with 0s. A non-numeric
     component falls back to a plain string compare of just that
     component, so an unusual tag like "1.2.0-beta" still compares
     reasonably instead of throwing. Returns &lt;0 if [a] &lt; [b], 0 if
     equal, &gt;0 if [a] &gt; [b]. */
    public static int compare_versions(String a, String b)
    {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++)
        {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            Integer ia = try_parse_int(sa);
            Integer ib = try_parse_int(sb);
            int cmp;
            if (ia != null && ib != null)
                cmp = Integer.compare(ia, ib);
            else
                cmp = sa.compareTo(sb);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    private static Integer try_parse_int(String s)
    {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private UpdateChecker() {}
}
