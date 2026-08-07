package com.adiraimaji.customkeyboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A self-contained, custom-built confirmation dialog (title + message +
 up to two buttons), styled to look like the standard Material alert
 dialog (rounded white card, bold title, gray message, right-aligned
 colored text buttons) but built entirely by hand rather than relying
 on AlertDialog + a system theme.

 Uses its own Dialog window (not the host Activity's window), with
 gravity and size forced explicitly, so it always renders centered on
 the full screen regardless of the host activity's soft-input resize
 mode or whether a keyboard is currently open. */
public class ConfirmDialog
{
    public interface OnResult
    {
        void result(boolean positive);
    }

    /** Shows the dialog. [on_result] is called with true if the positive
     button was tapped, false for negative/cancel (including dismissal
     by tapping outside or pressing back). Pass null for
     [negative_label] to show only a single button. */
    public static void show(Context ctx, String title, String message,
                            String positive_label, String negative_label, final OnResult on_result)
    {
        final Dialog dialog = new Dialog(ctx);
        Window window = dialog.getWindow();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad_h = dp(ctx, 24);
        int pad_v = dp(ctx, 20);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad_h, pad_v, pad_h, dp(ctx, 8));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(ctx, 16));
        root.setBackground(bg);

        TextView title_view = new TextView(ctx);
        title_view.setText(title);
        title_view.setTextColor(Color.rgb(23, 32, 42));
        title_view.setTextSize(19f);
        title_view.setTypeface(null, Typeface.BOLD);
        root.addView(title_view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Message is wrapped in a height-capped scroll view: short messages
        // render exactly as plain text (no visible scrollbar, no wasted
        // space), while long ones (e.g. a growing "appears in rows: ..."
        // list) are capped at a fixed height and become scrollable instead
        // of endlessly growing the dialog's height.
        TextView message_view = new TextView(ctx);
        message_view.setText(message);
        message_view.setTextColor(Color.rgb(90, 100, 115));
        message_view.setTextSize(15f);

        MaxHeightScrollView message_scroll = new MaxHeightScrollView(ctx);
        message_scroll.set_max_height(dp(ctx, 240));
        message_scroll.addView(message_view, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams msg_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        msg_params.topMargin = dp(ctx, 12);
        msg_params.bottomMargin = dp(ctx, 16);
        root.addView(message_scroll, msg_params);

        LinearLayout button_row = new LinearLayout(ctx);
        button_row.setOrientation(LinearLayout.HORIZONTAL);
        button_row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams button_row_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(button_row, button_row_params);

        final int accent = Color.rgb(52, 120, 246);

        if (negative_label != null)
        {
            Button neg_btn = make_dialog_button(ctx, negative_label, accent);
            neg_btn.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    dialog.dismiss();
                    if (on_result != null)
                        on_result.result(false);
                }
            });
            LinearLayout.LayoutParams neg_params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            button_row.addView(neg_btn, neg_params);
        }

        Button pos_btn = make_dialog_button(ctx, positive_label, accent);
        pos_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                dialog.dismiss();
                if (on_result != null)
                    on_result.result(true);
            }
        });
        // Give the positive button weight only when there's a negative one
        // too (so they split the row evenly); otherwise let it hug its
        // natural size on the right, matching standard single-button alerts.
        if (negative_label != null)
        {
            LinearLayout.LayoutParams pos_params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            button_row.addView(pos_btn, pos_params);
        }
        else
        {
            button_row.addView(pos_btn);
        }

        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (window != null)
        {
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 64);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        dialog.setCancelable(true);
        dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener()
        {
            public void onCancel(android.content.DialogInterface d)
            {
                if (on_result != null)
                    on_result.result(false);
            }
        });

        dialog.show();
    }

    /** A dialog action button that wraps onto a second line rather than
     truncating with an ellipsis when its label is too long to fit
     alongside the other button (e.g. "New row per line" next to
     "Paste as one field"). */
    private static Button make_dialog_button(Context ctx, String label, int color)
    {
        Button btn = new Button(ctx);
        btn.setText(label.toUpperCase());
        btn.setTextColor(color);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setSingleLine(false);
        btn.setMaxLines(2);
        btn.setEllipsize(null);
        btn.setTextSize(13f);
        btn.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        return btn;
    }

    private static int dp(Context ctx, int value)
    {
        return (int)(value * ctx.getResources().getDisplayMetrics().density);
    }

    /** A ScrollView that never measures taller than a fixed maximum height,
     becoming scrollable instead of continuing to grow once its content
     exceeds that height. Below that height, it behaves exactly like a
     normal ScrollView sized to its content (no visible scrollbar, no
     extra empty space). */
    private static class MaxHeightScrollView extends ScrollView
    {
        private int _max_height = Integer.MAX_VALUE;

        public MaxHeightScrollView(Context ctx)
        {
            super(ctx);
        }

        public void set_max_height(int max_height_px)
        {
            _max_height = max_height_px;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            int height_mode = MeasureSpec.getMode(heightMeasureSpec);
            int height_size = MeasureSpec.getSize(heightMeasureSpec);
            int capped_size = Math.min(height_size == 0 ? _max_height : height_size, _max_height);
            int new_height_spec;
            if (height_mode == MeasureSpec.UNSPECIFIED)
                new_height_spec = MeasureSpec.makeMeasureSpec(_max_height, MeasureSpec.AT_MOST);
            else
                new_height_spec = MeasureSpec.makeMeasureSpec(capped_size, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, new_height_spec);
        }
    }
}