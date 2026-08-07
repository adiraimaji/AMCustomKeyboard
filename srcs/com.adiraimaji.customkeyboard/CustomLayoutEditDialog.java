package com.adiraimaji.customkeyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.text.InputType;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.graphics.drawable.GradientDrawable;

public class CustomLayoutEditDialog
{
  /** [on_open_in_builder], if non-null, receives the dialog's CURRENT text
   at the moment the button is clicked (not the originally-loaded
   text), so unsaved edits/duplicates the user is actively fixing are
   carried over into the builder rather than lost. */
  public interface OpenInBuilder
  {
    void open(String current_text);
  }

  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, final Callback callback)
  {
    show(ctx, initial_text, allow_remove,
            R.string.pref_custom_layout_title,
            R.string.pref_layouts_remove_custom,
            null,
            callback);
  }

  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final Callback callback)
  {
    show(ctx, initial_text, allow_remove, title_res, remove_label_res, null, callback);
  }

  /** [on_open_in_builder], if non-null, adds a "Keymap Builder" button
   below the input box, with a red inline error row shown to its left
   whenever validate() reports a problem (replacing the floating error
   balloon from EditText.setError(), which used to cover the field).
   When [on_open_in_builder] is null, the same red error row is shown
   directly below the input instead.

   The OK button is blocked (does not dismiss, does not call
   callback.select()) while validate() reports an error - the "Keymap
   Builder" button is NOT blocked, since it exists specifically to let
   the user resolve problems like duplicate keys there instead. */
  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final OpenInBuilder on_open_in_builder,
                          final Callback callback)
  {
    final LayoutEntryEditText input = new LayoutEntryEditText(ctx);
    input.setText(initial_text);

    MaxHeightScrollView input_scroll = new MaxHeightScrollView(ctx);
    input_scroll.set_max_height(dp(ctx, 320));
    input_scroll.addView(input, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));

    LinearLayout container = new LinearLayout(ctx);
    container.setOrientation(LinearLayout.VERTICAL);
    container.addView(input_scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

    final TextView error_view = new TextView(ctx);
    error_view.setTextColor(Color.rgb(200, 40, 40));
    error_view.setTextSize(12f);
    error_view.setVisibility(View.GONE);

    final Button[] builder_button_holder =
            new Button[1];

    if (on_open_in_builder != null)
    {

      LinearLayout bottom_row = new LinearLayout(ctx);
      bottom_row.setOrientation(LinearLayout.HORIZONTAL);

      bottom_row.setGravity(
              Gravity.END | Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams error_params = new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      bottom_row.addView(error_view, error_params);

      Button builder_btn = new Button(ctx);

      builder_button_holder[0] = builder_btn;

      builder_btn.setText(R.string.pref_keymap_open_builder);
      builder_btn.setTextColor(Color.rgb(52, 120, 246));
      builder_btn.setBackgroundColor(Color.TRANSPARENT);
      bottom_row.addView(builder_btn, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));

      container.addView(bottom_row, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));

//      builder_btn.setOnClickListener(new View.OnClickListener()
//      {
//        public void onClick(View v)
//        {
//          String current_text = input.getText().toString();
//          on_open_in_builder.open(current_text);
//        }
//      });
    }
    else
    {
      LinearLayout.LayoutParams error_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT);
      error_params.topMargin = dp(ctx, 6);
      container.addView(error_view, error_params);
    }

    LinearLayout dialog_margin_container = new LinearLayout(ctx);
    dialog_margin_container.setOrientation(LinearLayout.VERTICAL);
    dialog_margin_container.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
    dialog_margin_container.addView(container, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

    AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
            .setTitle(title_res)
            .setView(dialog_margin_container)
            .setPositiveButton(android.R.string.ok, null) // Overridden below.
            .setNegativeButton(android.R.string.cancel, null);
    if (allow_remove)
      builder.setNeutralButton(remove_label_res, new DialogInterface.OnClickListener(){
        public void onClick(DialogInterface _dialog, int _which)
        {
          callback.select(null);
        }
      });

    final AlertDialog dialog = builder.create();

    if (on_open_in_builder != null
            && builder_button_holder[0] != null)
    {
      final Button builder_btn =
              builder_button_holder[0];

      builder_btn.setOnClickListener(
              new View.OnClickListener()
              {
                @Override
                public void onClick(View v)
                {
                  String current_text =
                          input.getText().toString();

                  dialog.dismiss();

                  on_open_in_builder.open(current_text);
                }
              });
    }

    // Show the current validation state immediately, before any typing.
    update_error_view(error_view, callback.validate(initial_text));

    dialog.setOnShowListener(new DialogInterface.OnShowListener()
    {
      public void onShow(DialogInterface d)
      {
        Button ok_btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok_btn.setOnClickListener(new View.OnClickListener()
        {
          public void onClick(View v)
          {
            String text = input.getText().toString();
            String error = callback.validate(text);
            update_error_view(error_view, error);
            if (error == null)
            {
              callback.select(text);
              dialog.dismiss();
            }
          }
        });
      }
    });

    input.set_on_text_change(new LayoutEntryEditText.OnChangeListener()
    {
      public void on_change()
      {
        update_error_view(error_view, callback.validate(input.getText().toString()));
      }
    });
    dialog.show();
  }

  private static void update_error_view(TextView error_view, String error)
  {
    if (error == null || error.isEmpty())
    {
      error_view.setVisibility(View.GONE);
      error_view.setText("");
    }
    else
    {
      error_view.setText("\u26A0 " + error);
      error_view.setVisibility(View.VISIBLE);
    }
  }

  public interface Callback
  {
    public void select(String text);
    public String validate(String text);
  }

  private static int dp(Context ctx, int value)
  {
    return (int)(value * ctx.getResources().getDisplayMetrics().density);
  }

  private static class MaxHeightScrollView extends ScrollView
  {
    private int _max_height = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context ctx) { super(ctx); }

    public void set_max_height(int max_height_px) { _max_height = max_height_px; }

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

  static class LayoutEntryEditText extends EditText
  {
    Paint _ln_paint;
    OnChangeListener _on_change_listener = null;
    Handler _on_change_throttler;
    Runnable _on_change_delayed = new Runnable()
    {
      public void run()
      {
        OnChangeListener l = LayoutEntryEditText.this._on_change_listener;
        if (l != null)
          l.on_change();
      }
    };

    public LayoutEntryEditText(Context ctx)
    {
      super(ctx);
      _ln_paint = new Paint(getPaint());
      _ln_paint.setTextSize(_ln_paint.getTextSize() * 0.8f);
      setHorizontallyScrolling(true);
      setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
      setGravity(Gravity.TOP | Gravity.START);
      setMinLines(3);
      setMaxLines(Integer.MAX_VALUE);
      style_input_box(this);
      _on_change_throttler = new Handler(ctx.getMainLooper());
    }

    public void set_on_text_change(OnChangeListener l) { _on_change_listener = l; }

    Rect _clip_bounds = new Rect();
    int _prev_padding = Integer.MIN_VALUE;

    @Override
    protected void onDraw(Canvas canvas)
    {
      float digit_width = _ln_paint.measureText("0");
      int line_count = getLineCount();
      int padding = (int)(((int)Math.log10(line_count) + 1 + 1) * digit_width);
      if (padding != _prev_padding) {
        setPadding(padding, 0, 0, 0);
        _prev_padding = padding;
      }
      super.onDraw(canvas);
      _ln_paint.setColor(getPaint().getColor());
      canvas.getClipBounds(_clip_bounds);
      Layout layout = getLayout();
      int offset = (int)(digit_width / 2.f);
      int line = layout.getLineForVertical(_clip_bounds.top);
      while (line < line_count)
      {
        int baseline = getLineBounds(line, null);
        canvas.drawText(String.valueOf(line), offset, baseline, _ln_paint);
        line++;
        if (baseline >= _clip_bounds.bottom)
          break;
      }
    }

    @Override
    protected void onTextChanged(CharSequence text, int _s, int _lb, int _la)
    {
      if (_on_change_throttler != null)
      {
        _on_change_throttler.removeCallbacks(_on_change_delayed);
        _on_change_throttler.postDelayed(_on_change_delayed, 1000);
      }
    }

    public static interface OnChangeListener { public void on_change(); }
  }

  private static void style_input_box(EditText input)
  {
    final Context ctx = input.getContext();
    input.setTextColor(Color.rgb(23, 32, 42));
    input.setHintTextColor(Color.rgb(152, 162, 171));
    input.setTextSize(14f);
    input.setGravity(Gravity.TOP | Gravity.START);
    input.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
    input.setBackground(create_input_background(Color.WHITE, Color.rgb(208, 213, 221), dp(ctx, 1), dp(ctx, 10)));
    input.setOnFocusChangeListener(new View.OnFocusChangeListener()
    {
      @Override
      public void onFocusChange(View view, boolean has_focus)
      {
        if (has_focus)
          input.setBackground(create_input_background(Color.rgb(248, 251, 255), Color.rgb(52, 120, 246), dp(ctx, 2), dp(ctx, 10)));
        else
          input.setBackground(create_input_background(Color.WHITE, Color.rgb(208, 213, 221), dp(ctx, 1), dp(ctx, 10)));
      }
    });
  }

  private static GradientDrawable create_input_background(int fill_color, int border_color, int border_width, int corner_radius)
  {
    GradientDrawable background = new GradientDrawable();
    background.setShape(GradientDrawable.RECTANGLE);
    background.setColor(fill_color);
    background.setCornerRadius(corner_radius);
    background.setStroke(border_width, border_color);
    return background;
  }
}