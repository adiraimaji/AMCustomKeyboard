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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.graphics.drawable.GradientDrawable;

import com.adiraimaji.customkeyboard.prefs.KeymapManager;

import java.util.ArrayList;
import java.util.List;

public class CustomLayoutEditDialog
{
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
            callback);
  }

  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final Callback callback)
  {
    show(ctx, initial_text, allow_remove, title_res, remove_label_res, null, callback);
  }

  /** Used by KeymapEditDialog. No keymap-selector row. */
  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final OpenInBuilder on_open_in_builder,
                          final Callback callback)
  {
    show(ctx, initial_text, allow_remove, title_res, remove_label_res, false, on_open_in_builder, callback);
  }

  /** Full version. [show_keymap_selector], if true, adds a Spinner
   (listing every saved keymap plus "(No keymap)") and a "Swipe"
   checkbox above the input box - used only for the Layout dialog
   (LayoutsPreference.select_custom()), never for the Keymap JSON
   dialog. Picking a keymap rewrites the "keymap"/"swipekeymap"
   attributes on the input's <keyboard> tag; conversely, manually
   typing those attributes updates the Spinner/checkbox to match -
   both directions are guarded against re-entrant loops.

   [on_open_in_builder], if non-null, adds a "Keymap Builder" button
   below the input box, with an inline red error row next to it
   (never both true - Layout and Keymap dialogs are mutually
   exclusive uses of this method). */
  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final boolean show_keymap_selector,
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

    final TextView error_view = new TextView(ctx);
    error_view.setTextColor(Color.rgb(200, 40, 40));
    error_view.setTextSize(12f);
    error_view.setVisibility(View.GONE);

    final boolean[] syncing = { false };
    final Spinner[] spinner_holder = { null };
    final CheckBox[] swipe_cb_holder = { null };
    final List<String> keymap_names = new ArrayList<>();
    final EditText[] name_input_holder = { null };



    if (show_keymap_selector)
    {
      keymap_names.add(ctx.getString(R.string.layout_keymap_none));
      for (KeymapManager.StoredKeymap k : KeymapManager.load(ctx))
        keymap_names.add(k.name);

      // "Keyboard Attributes" card: bordered container with three
      // labeled rows (Name / Keymap / Swipekeymap), each a TextView
      // label on the left and its control on the right, matching the
      // visual style used elsewhere (rounded corners, light border).
      LinearLayout attrs_card = new LinearLayout(ctx);
      attrs_card.setOrientation(LinearLayout.VERTICAL);
      int card_pad = dp(ctx, 10);
      attrs_card.setPadding(card_pad, card_pad, card_pad, card_pad);
      GradientDrawable card_bg = new GradientDrawable();
      card_bg.setShape(GradientDrawable.RECTANGLE);
      card_bg.setColor(Color.rgb(248, 250, 252));
      card_bg.setCornerRadius(dp(ctx, 10));
      card_bg.setStroke(dp(ctx, 1), Color.rgb(224, 228, 233));
      attrs_card.setBackground(card_bg);

      TextView card_title = new TextView(ctx);
      card_title.setText(R.string.layout_attributes_title);
      card_title.setTextColor(Color.rgb(102, 112, 133));
      card_title.setTextSize(11f);
      card_title.setTypeface(null, android.graphics.Typeface.BOLD);
      LinearLayout.LayoutParams card_title_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      card_title_params.bottomMargin = dp(ctx, 8);
      attrs_card.addView(card_title, card_title_params);

      int label_width = dp(ctx, 90);

      // Name row.
      LinearLayout name_row = new LinearLayout(ctx);
      name_row.setOrientation(LinearLayout.HORIZONTAL);
      name_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView name_label = new TextView(ctx);
      name_label.setText(R.string.layout_name_label);
      name_label.setTextColor(Color.rgb(71, 84, 103));
      name_label.setTextSize(13f);
      name_row.addView(name_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final EditText name_input = new EditText(ctx);
      name_input.setHint(R.string.layout_name_hint);
      name_input.setSingleLine(true);
      name_input.setTextSize(13f);
      name_row.addView(name_input, new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      LinearLayout.LayoutParams name_row_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      name_row_params.bottomMargin = dp(ctx, 8);
      attrs_card.addView(name_row, name_row_params);
      name_input_holder[0] = name_input;

      // Keymap row.
      LinearLayout keymap_row = new LinearLayout(ctx);
      keymap_row.setOrientation(LinearLayout.HORIZONTAL);
      keymap_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView keymap_label = new TextView(ctx);
      keymap_label.setText(R.string.layout_keymap_label);
      keymap_label.setTextColor(Color.rgb(71, 84, 103));
      keymap_label.setTextSize(13f);
      keymap_row.addView(keymap_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final Spinner spinner = new Spinner(ctx);
      ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
              android.R.layout.simple_spinner_item, keymap_names);
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      spinner.setAdapter(adapter);
      keymap_row.addView(spinner, new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      LinearLayout.LayoutParams keymap_row_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      keymap_row_params.bottomMargin = dp(ctx, 4);
      attrs_card.addView(keymap_row, keymap_row_params);
      spinner_holder[0] = spinner;

      // Swipekeymap row.
      LinearLayout swipe_row = new LinearLayout(ctx);
      swipe_row.setOrientation(LinearLayout.HORIZONTAL);
      swipe_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView swipe_label = new TextView(ctx);
      swipe_label.setText(R.string.layout_swipekeymap_row_label);
      swipe_label.setTextColor(Color.rgb(71, 84, 103));
      swipe_label.setTextSize(13f);
      swipe_row.addView(swipe_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final CheckBox swipe_cb = new CheckBox(ctx);
      swipe_row.addView(swipe_cb, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      attrs_card.addView(swipe_row, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      swipe_cb_holder[0] = swipe_cb;

      LinearLayout.LayoutParams attrs_card_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      attrs_card_params.bottomMargin = dp(ctx, 10);
      container.addView(attrs_card, attrs_card_params);

      // Initial state from the XML, guarded so it doesn't immediately
      // rewrite the text back at itself.
      syncing[0] = true;
      name_input.setText(KeymapXmlAttrUtils.get_name_attr(initial_text));
      String current_keymap = KeymapXmlAttrUtils.get_keymap_attr(initial_text);
      int initial_index = (current_keymap != null)
              ? Math.max(0, keymap_names.indexOf(current_keymap)) : 0;
      spinner.setSelection(initial_index);
      swipe_cb.setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(initial_text));
      swipe_cb.setEnabled(initial_index != 0);
      syncing[0] = false;

      name_input.addTextChangedListener(new android.text.TextWatcher()
      {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(android.text.Editable s)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text = KeymapXmlAttrUtils.set_name_attr(
                  input.getText().toString(), s.toString());
          int cursor = input.getSelectionStart();
          input.setText(new_text);
          input.setSelection(Math.min(cursor, new_text.length()));
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
      });

      spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
      {
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text;
          if (position == 0)
          {
            swipe_cb.setChecked(false);
            swipe_cb.setEnabled(false);
            new_text = KeymapXmlAttrUtils.remove_keymap_attrs(input.getText().toString());
          }
          else
          {
            swipe_cb.setEnabled(true);
            String t = KeymapXmlAttrUtils.set_keymap_attr(
                    input.getText().toString(), keymap_names.get(position));
            t = KeymapXmlAttrUtils.set_swipekeymap_attr(t, swipe_cb.isChecked());
            new_text = t;
          }
          input.setText(new_text);
          input.setSelection(new_text.length());
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
        public void onNothingSelected(AdapterView<?> parent) {}
      });

      swipe_cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
      {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text = KeymapXmlAttrUtils.set_swipekeymap_attr(
                  input.getText().toString(), isChecked);
          input.setText(new_text);
          input.setSelection(new_text.length());
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
      });
    }

    container.addView(input_scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

    final Button[] builder_button_holder = new Button[1];

    if (on_open_in_builder != null)
    {
//      View divider = new View(ctx);
//      divider.setBackgroundColor(Color.rgb(52, 120, 246));
//      LinearLayout.LayoutParams divider_params = new LinearLayout.LayoutParams(
//              LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 2));
//      divider_params.topMargin = dp(ctx, 4);
//      divider_params.bottomMargin = dp(ctx, 8);
//      container.addView(divider, divider_params);

      LinearLayout bottom_row = new LinearLayout(ctx);
      bottom_row.setOrientation(LinearLayout.HORIZONTAL);
      bottom_row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

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
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);
    if (allow_remove)
      builder.setNeutralButton(remove_label_res, new DialogInterface.OnClickListener(){
        public void onClick(DialogInterface _dialog, int _which)
        {
          callback.select(null);
        }
      });

    final AlertDialog dialog = builder.create();

    if (on_open_in_builder != null && builder_button_holder[0] != null)
    {
      builder_button_holder[0].setOnClickListener(new View.OnClickListener()
      {
        public void onClick(View v)
        {
          String current_text = input.getText().toString();
          dialog.dismiss();
          on_open_in_builder.open(current_text);
        }
      });
    }

    update_error_view(error_view, safe_validate(callback, initial_text));

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
            String error = safe_validate(callback, text);
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
        String text = input.getText().toString();
        update_error_view(error_view, safe_validate(callback, text));

        if (show_keymap_selector && spinner_holder[0] != null && !syncing[0])
        {
          syncing[0] = true;
          if (name_input_holder[0] != null)
            name_input_holder[0].setText(KeymapXmlAttrUtils.get_name_attr(text));
          String cur = KeymapXmlAttrUtils.get_keymap_attr(text);
          int idx = (cur != null) ? Math.max(0, keymap_names.indexOf(cur)) : 0;
          spinner_holder[0].setSelection(idx);
          swipe_cb_holder[0].setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(text));
          swipe_cb_holder[0].setEnabled(idx != 0);
          syncing[0] = false;
        }
      }
    });
    dialog.show();
  }

  /** Wraps [callback.validate] so a bug in some Callback implementation
   (or a not-yet-handled edge case in whatever it parses) shows up as
   a normal inline error message, same as any other invalid input,
   rather than crashing the whole app - this runs on every keystroke,
   including from a Handler-posted callback with no other surrounding
   try/catch, so nothing here may be allowed to escape uncaught. */
  private static String safe_validate(Callback callback, String text)
  {
    try
    {
      return callback.validate(text);
    }
    catch (Exception e)
    {
      String msg = e.getMessage();
      return (msg != null && !msg.isEmpty()) ? msg : ("Invalid input (" + e.getClass().getSimpleName() + ")");
    }
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