package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/** Storage for the SINGLE Tasker Automation JSON blob - unlike keymaps,
 there is only ever one of these, so this is a plain key-value pair,
 not a list. */
public class TaskerAutomationManager
{
    private static final String PREF_NAME = "tasker_automation";
    private static final String KEY_JSON = "json";

    private static SharedPreferences prefs(Context ctx)
    {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String load(Context ctx)
    {
        return prefs(ctx).getString(KEY_JSON, null);
    }

    public static void save(Context ctx, String json)
    {
        prefs(ctx).edit().putString(KEY_JSON, json).apply();
    }
}