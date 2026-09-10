package com.adiraimaji.customkeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Runs a named Tasker task via Tasker's official "External Access"
 broadcast API (ACTION_TASK), and asynchronously retrieves a result
 from it via a *custom* broadcast that the task itself sends back
 using Tasker's own "Send Intent" action - rather than relying on
 Tasker's built-in ACTION_TASK_COMPLETE/Return mechanism, which in
 practice doesn't reliably deliver the Return value to external
 callers.

 The Tasker task must be built to explicitly send this app a
 broadcast (Task > Send Intent) once it has a result, passing back
 the same %amck_requestid this class hands it, so a reply can be
 matched to the call that produced it even if several calls are in
 flight at once. See the class-level setup notes below for the exact
 Tasker action fields.

 Requires: the app declares
 <uses-permission android:name="net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"/>
 in AndroidManifest.xml, Tasker is installed, and the user has
 enabled Tasker > Preferences > Misc > "Allow External Access".
 None of this can be forced from here - failures are reported via
 [ResultCallback.result]'s error_message. */
public final class TaskerBridge
{
    private static final String TASKER_PACKAGE = "net.dinglisch.android.tasker";
    private static final String ACTION_TASK = TASKER_PACKAGE + ".ACTION_TASK";
    private static final String EXTRA_TASK_NAME = "task_name";
    private static final String EXTRA_VAR_NAMES_LIST = "varNames";
    private static final String EXTRA_VAR_VALUES_LIST = "varValues";

    /** Action of the broadcast the Tasker task sends *back* to us via
     its own "Send Intent" action. Built from the app's own package
     name at runtime, so it automatically matches whichever build
     variant (".debug" suffix or not) is actually installed - see
     the Tasker task setup notes below for what to put in the
     "Action" field of Send Intent. */
    private static final String RESULT_ACTION_SUFFIX = ".TASKER_RESULT";

    /** Extra name (inside the Send Intent's "Extra" fields, as
     "requestid:%amck_requestid") the task must echo back so replies
     can be matched to the call that produced them. This is the
     extra's KEY on the incoming reply broadcast, a fixed name of
     this app's own choosing - unrelated to (and NOT renamed
     alongside) the %amck_-prefixed local variable names below, which
     is just the VALUE the task is told to put there. */
    private static final String EXTRA_REQUEST_ID = "requestid";

    /** Extra name (as "text:%hai" or similar, in Send Intent's
     "Extra" fields) carrying the actual result text. */
    private static final String EXTRA_RESULT_TEXT = "text";

    private static final AtomicLong request_counter = new AtomicLong(0);

    public interface ResultCallback
    {
        /** [output] is the text the task's Send Intent handed back under
         the "text" extra, or null if the task never sent a matching
         reply within the timeout, or Tasker wasn't reachable at all
         (see [error_message], non-null only in the latter cases and
         suitable for showing directly to the user). */
        void result(String output, String error_message);
    }

    private TaskerBridge() {}

    /** Runs [task_name], passing in:
      %amck_text1     - the field's text before the matched
                        trigger/pattern span, with that span itself
                        removed (always set, possibly to an empty
                        string)
      %amck_text2     - the field's text after the matched span -
                        left UNSET (not merely empty) when there's
                        nothing there, so Tasker's own "is set" checks
                        work as expected
      %amck_keyword   - just the keyword/content itself, e.g. "one"
                        for "##one"/"@@one", or the free-form text
                        between an expand pattern's prefix and suffix
                        - never the trigger/prefix/suffix symbols
                        themselves
      %amck_requestid - a freshly generated correlation id, echoed
                        back by the task's own "Send Intent" (under
                        the fixed extra key [EXTRA_REQUEST_ID]) so the
                        reply can be matched to this specific call
     then waits up to [timeout_ms] for that same task to send back a
     matching "Send Intent" broadcast, reporting its "text" extra
     asynchronously via [callback]. */
    public static void run_task(final Context ctx, String task_name,
                                String text1, String text2, String keyword,
                                long timeout_ms, final ResultCallback callback)
    {
        ArrayList<String> var_names = new ArrayList<>();
        ArrayList<String> var_values = new ArrayList<>();
        var_names.add("%amck_text1");
        var_values.add(text1 != null ? text1 : "");
        if (text2 != null && !text2.isEmpty())
        {
            var_names.add("%amck_text2");
            var_values.add(text2);
        }
        var_names.add("%amck_keyword");
        var_values.add(keyword != null ? keyword : "");
        run_task_internal(ctx, task_name, var_names, var_values, timeout_ms, callback);
    }

    /** Same as the 5-var-name overload above, for "amck_patterns" -
     which additionally has a (regex-matched) [prefix] and, once the
     configured "suffix" regex has actually been matched, a [suffix] -
     both passed the same "UNSET rather than empty" way as
     [text2]/%amck_text2:
      %amck_prefix - the actual text the entry's "prefix" regex
                     matched at the start of this occurrence (e.g. a
                     single space, or empty for a zero-width match
                     like "^") - left UNSET only when [prefix] is null
                     (an empty-but-real match, e.g. "^", is still
                     sent, as an empty string, since "prefix matched"
                     and "prefix wasn't configured" are different
                     things worth Tasker being able to tell apart with
                     an "is set" check)
      %amck_suffix - the actual text the entry's "suffix" regex
                     matched, only present at all on the one final
                     call fired the moment that match completes - left
                     UNSET on every earlier "still live" call for the
                     same occurrence, where the word isn't finished
                     yet and there's nothing to put here
      %amck_keyword_stop - only present (as "true") on the one call
                     fired the instant a "fire_on_suffix": "false"
                     entry's in-between text, having matched "regex"
                     at least once, stops matching it again - before
                     "suffix" has been reached at all. Left UNSET on
                     every other call (including the "suffix" one
                     above), so a Tasker task can tell "the word I was
                     tracking no longer looks valid - e.g. dismiss any
                     popup you showed" apart from every other reason
                     this task might run. See [keyword_stop].
     [keyword] here is always just the in-between content the entry's
     own "regex" matched (or, when [keyword_stop] is true, whatever
     content most recently failed to match it) - never [prefix] or
     [suffix]. */
    public static void run_task(final Context ctx, String task_name,
                                String text1, String text2, String prefix, String keyword, String suffix,
                                boolean keyword_stop, long timeout_ms, final ResultCallback callback)
    {
        ArrayList<String> var_names = new ArrayList<>();
        ArrayList<String> var_values = new ArrayList<>();
        var_names.add("%amck_text1");
        var_values.add(text1 != null ? text1 : "");
        if (text2 != null && !text2.isEmpty())
        {
            var_names.add("%amck_text2");
            var_values.add(text2);
        }
        if (prefix != null)
        {
            var_names.add("%amck_prefix");
            var_values.add(prefix);
        }
        var_names.add("%amck_keyword");
        var_values.add(keyword != null ? keyword : "");
        if (suffix != null)
        {
            var_names.add("%amck_suffix");
            var_values.add(suffix);
        }
        if (keyword_stop)
        {
            var_names.add("%amck_keyword_stop");
            var_values.add("true");
        }
        run_task_internal(ctx, task_name, var_names, var_values, timeout_ms, callback);
    }

    private static void run_task_internal(final Context ctx, String task_name,
                                          ArrayList<String> var_names, ArrayList<String> var_values,
                                          long timeout_ms, final ResultCallback callback)
    {
        final String request_id = System.currentTimeMillis() + "-" + request_counter.incrementAndGet();
        final String result_action = ctx.getPackageName() + RESULT_ACTION_SUFFIX;

        Intent request = new Intent(ACTION_TASK);
        request.putExtra(EXTRA_TASK_NAME, task_name);

        var_names.add("%amck_requestid");
        var_values.add(request_id);
        request.putStringArrayListExtra(EXTRA_VAR_NAMES_LIST, var_names);
        request.putStringArrayListExtra(EXTRA_VAR_VALUES_LIST, var_values);

        IntentFilter result_filter = new IntentFilter(result_action);

        final boolean[] finished = { false };
        final BroadcastReceiver[] receiver_holder = new BroadcastReceiver[1];
        final Handler handler = new Handler(ctx.getMainLooper());

        final Runnable timeout_runnable = new Runnable()
        {
            public void run()
            {
                if (finished[0])
                    return;
                finished[0] = true;
                if (receiver_holder[0] != null)
                {
                    try { ctx.unregisterReceiver(receiver_holder[0]); } catch (Exception ignored) {}
                }
                callback.result(null, ctx.getString(R.string.tasker_error_timeout));
            }
        };

        BroadcastReceiver receiver = new BroadcastReceiver()
        {
            public void onReceive(Context c, Intent intent)
            {
                if (finished[0])
                    return;

                if (android.util.Log.isLoggable("TaskerBridge", android.util.Log.DEBUG))
                {
                    StringBuilder dump = new StringBuilder(result_action).append(" extras: ");
                    android.os.Bundle extras = intent.getExtras();
                    if (extras != null)
                    {
                        for (String key : extras.keySet())
                            dump.append(key).append("=").append(extras.get(key)).append("; ");
                    }
                    android.util.Log.d("TaskerBridge", dump.toString());
                }

                String reply_request_id = intent.getStringExtra(EXTRA_REQUEST_ID);
                if (reply_request_id == null || !reply_request_id.equals(request_id))
                    return; // Reply to a different (earlier/concurrent) call - keep waiting for ours.

                finished[0] = true;
                handler.removeCallbacks(timeout_runnable);
                try { ctx.unregisterReceiver(this); } catch (Exception ignored) {}

                String output = intent.getStringExtra(EXTRA_RESULT_TEXT);
                callback.result(output, null);
            }
        };
        receiver_holder[0] = receiver;

        try
        {
            if (Build.VERSION.SDK_INT >= 33)
                ctx.registerReceiver(receiver, result_filter, Context.RECEIVER_EXPORTED);
            else
                ctx.registerReceiver(receiver, result_filter);
        }
        catch (Exception e)
        {
            callback.result(null, ctx.getString(R.string.tasker_error_generic));
            return;
        }

        try
        {
            ctx.sendBroadcast(request);
        }
        catch (Exception e)
        {
            finished[0] = true;
            try { ctx.unregisterReceiver(receiver); } catch (Exception ignored) {}
            callback.result(null, ctx.getString(R.string.tasker_error_generic));
            return;
        }

        handler.postDelayed(timeout_runnable, timeout_ms);
    }
}
