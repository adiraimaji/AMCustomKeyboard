package com.adiraimaji.customkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Small shared helpers so custom dialog content looks visually
 consistent across the app (the "Keymap N: name" actions dialog, the
 overwrite-confirmation dialog, etc), since AlertDialog.setMessage()
 renders inconsistently across devices/themes. */
public final class DialogUtils
{
    private DialogUtils() {}

    public static int dp(Context ctx, int value)
    {
        return (int)(value * ctx.getResources().getDisplayMetrics().density);
    }

    /** A simple padded message view, for use as an AlertDialog's custom
     view (via .setView()) instead of .setMessage(). */
    public static View styled_message_view(Context ctx, String message)
    {
        int pad = dp(ctx, 20);
        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(15f);
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    /** A scrollable, selectable, monospace JSON preview with an explicit
     "Copy" button, for use as an AlertDialog's custom view. */
    public static View styled_json_view(final Context ctx, String json)
    {
        int pad = dp(ctx, 20);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(ctx, 12), pad, dp(ctx, 4));

        Button copy_btn = new Button(ctx);
        copy_btn.setText(R.string.dialog_copy);
        LinearLayout.LayoutParams copy_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        copy_params.gravity = Gravity.END;
        copy_params.bottomMargin = dp(ctx, 8);
        root.addView(copy_btn, copy_params);

        ScrollView scroll = new ScrollView(ctx);
        int max_h = dp(ctx, 280);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, max_h));

        final TextView json_view = new TextView(ctx);
        json_view.setText(json);
        json_view.setTextColor(Color.BLACK);
        json_view.setTextIsSelectable(true); // Long-press to select/copy.
        json_view.setTypeface(Typeface.MONOSPACE);
        json_view.setTextSize(13f);
        json_view.setBackgroundColor(Color.parseColor("#F0F0F0"));
        int inner_pad = dp(ctx, 12);
        json_view.setPadding(inner_pad, inner_pad, inner_pad, inner_pad);

        scroll.addView(json_view, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        copy_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                ClipboardManager cm =
                        (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null)
                {
                    cm.setPrimaryClip(ClipData.newPlainText("keymap json", json_view.getText()));
                    Toast.makeText(ctx, R.string.dialog_copied, Toast.LENGTH_SHORT).show();
                }
            }
        });

        return root;
    }

    /** Applies rounded-corner white card styling and accent-colored buttons
     to an already-created AlertDialog, matching the modern look used
     for EditText fields elsewhere in the app (see
     KeymapBuilderActivity.apply_modern_edit_background). Must be called
     AFTER dialog.show(), since getButton() only returns valid views
     once the dialog has actually been shown. */
    public static void apply_modern_style(android.app.AlertDialog dialog, Context ctx)
    {
        final int accent = Color.rgb(52, 120, 246);
        final int corner_radius = dp(ctx, 16);

        if (dialog.getWindow() != null)
        {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(corner_radius);
            dialog.getWindow().setBackgroundDrawable(bg);
        }

        android.widget.Button pos = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(accent);
        android.widget.Button neg = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(accent);
        android.widget.Button neu = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(accent);
    }

    /** A bold title view matching the same modern styling, for use with
     .setCustomTitle() instead of .setTitle() where a plain system title
     bar would look inconsistent with the rest of the dialog's content. */
    public static View styled_title_view(Context ctx, String title)
    {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(Color.rgb(23, 32, 42));
        tv.setTextSize(18f);
        tv.setTypeface(null, Typeface.BOLD);
        int pad_h = dp(ctx, 20);
        tv.setPadding(pad_h, dp(ctx, 20), pad_h, dp(ctx, 4));
        return tv;
    }
}