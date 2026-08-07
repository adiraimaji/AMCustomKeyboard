package com.adiraimaji.customkeyboard;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.widget.TextView;
import android.widget.Toast;

import com.adiraimaji.customkeyboard.prefs.KeymapManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.graphics.drawable.GradientDrawable;

import android.widget.RadioButton;
import android.widget.RadioGroup;


/** A guided editor for building a keymap JSON without hand-writing it.

 Row 0 is a permanent, non-removable "name row": a fixed label reading
 "keymap_name" on the left, and an editable field on the right where
 the user types the keymap's actual name.

 Every following row is a normal output/keys mapping row: an output
 field (left, multiline, wider) and a comma-separated list of keys
 that should produce it (right). Typing into the last mapping row's
 output field automatically appends a new empty mapping row below it.
 Pasting multiline text into any mapping row's output field offers to
 split it into one new row per line, inserted right after that row.

 Pass EXTRA_EDIT_KEYMAP_NAME to open this activity pre-filled with an
 existing stored keymap for editing instead of creating a new one.
 Pass EXTRA_INITIAL_JSON_TEXT to pre-fill from arbitrary (possibly
 duplicate-key-containing) JSON text instead of a stored keymap - used
 when opened via the "Keymap Builder" button inside the raw JSON
 editor dialog, so in-progress/unsaved edits aren't lost. */
public class KeymapBuilderActivity extends Activity
{
    public static final String EXTRA_EDIT_KEYMAP_NAME = "edit_keymap_name";
    public static final String EXTRA_INITIAL_JSON_TEXT = "initial_json_text";

    private TextView _header;
    private EditText _name_input;

    private EditText _quick_output;
    private EditText _quick_keys;
    private ImageButton _quick_add_button;

    private LinearLayout _rows_container;
    private CheckBox _solo_duplicates_checkbox;

    private EditText _search_input;
    private RadioGroup _search_radio_group;
    private RadioButton _search_output_radio;
    private RadioButton _search_keys_radio;
    private ImageButton _raw_json_button;

    private ImageButton _import_json_button;
    /** Row indices (into _rows) that currently contain at least one
     duplicate key, as of the last recompute_duplicates() call. */
    private Set<Integer> _last_duplicate_row_indices = new HashSet<>();

    private final List<Row> _rows = new ArrayList<>();
    private String _editing_original_name = null;
    private boolean _suppress_auto_add_row = false;

    private static final class Row
    {
        final View container;
        final EditText output;
        final EditText keys;
        final TextView number;

        Row(View container_, EditText output_, EditText keys_, TextView number_)
        {
            container = container_;
            output = output_;
            keys = keys_;
            number = number_;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_keymap_builder);

        _header = findViewById(R.id.keymap_builder_header);
        _name_input = findViewById(R.id.keymap_builder_name_input);
        _rows_container = findViewById(R.id.keymap_builder_rows_container);
        _solo_duplicates_checkbox = findViewById(R.id.keymap_builder_solo_duplicates_checkbox);
        _solo_duplicates_checkbox.setEnabled(true);
        _raw_json_button = findViewById(R.id.keymap_builder_raw_json_button);

        _quick_output = findViewById(R.id.keymap_builder_quick_output);
        _quick_keys = findViewById(R.id.keymap_builder_quick_keys);
        _quick_add_button = findViewById(R.id.keymap_builder_quick_add_button);

        style_edit_text(_quick_output, R.string.keymap_builder_output_hint, true);
        style_edit_text(_quick_keys, R.string.keymap_builder_keys_hint, false);

        _search_input = findViewById(R.id.keymap_builder_search_input);
        _search_radio_group = findViewById(R.id.keymap_builder_search_radio_group);
        _search_output_radio = findViewById(R.id.keymap_builder_search_output_radio);
        _search_keys_radio = findViewById(R.id.keymap_builder_search_keys_radio);

        _search_input.addTextChangedListener(new TextWatcher()
        {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s)
            {
                apply_row_filters();
            }
        });

        _search_radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId)
            {
                apply_row_filters();
            }
        });

        _quick_add_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                add_via_quick_fields();
            }
        });

        _name_input.setTextColor(Color.rgb(23, 32, 42));
        _name_input.setHintTextColor(Color.rgb(152, 162, 171));
        _name_input.setTextSize(14f);
        apply_modern_edit_background(_name_input);

        _import_json_button = findViewById(R.id.keymap_builder_import_json_button);
        _import_json_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                show_import_json_dialog();
            }
        });

        _solo_duplicates_checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                apply_row_filters();
            }
        });

        _raw_json_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                show_raw_json_dialog();
            }
        });

        Button create_btn = findViewById(R.id.keymap_builder_create_button);
        create_btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                on_create_clicked();
            }
        });

        add_mapping_row(); // Default mapping row 1.

        String edit_name = getIntent().getStringExtra(EXTRA_EDIT_KEYMAP_NAME);
        String initial_json_text = getIntent().getStringExtra(EXTRA_INITIAL_JSON_TEXT);

        if (edit_name != null)
        {
            _editing_original_name = edit_name;
            _header.setText(R.string.keymap_builder_edit_title);
        }

        if (initial_json_text != null)
        {
            populate_from_json_text(initial_json_text);
        }
        else if (edit_name != null)
        {
            populate_for_edit(edit_name);
        }
    }

    private void style_edit_text(EditText edit_text, int hint_res, boolean multiline)
    {
        edit_text.setHint(hint_res);
        edit_text.setTextColor(Color.rgb(23, 32, 42));
        edit_text.setHintTextColor(Color.rgb(152, 162, 171));
        edit_text.setTextSize(14f);
        apply_modern_edit_background(edit_text);

        if (multiline)
        {
            edit_text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            edit_text.setGravity(Gravity.TOP | Gravity.START);
            edit_text.setMinLines(1);
            edit_text.setMaxLines(Integer.MAX_VALUE);
            edit_text.setHorizontallyScrolling(true);
            edit_text.setHorizontalScrollBarEnabled(false);
        }
        else
        {
            edit_text.setInputType(InputType.TYPE_CLASS_TEXT);
            edit_text.setSingleLine(true);
            edit_text.setGravity(Gravity.CENTER_VERTICAL);
        }
    }

    private EditText make_edit_text(int hint_res, boolean multiline)
    {
        EditText et = new EditText(this);
        style_edit_text(et, hint_res, multiline);
        return et;
    }

    private Row add_mapping_row_at(int index, String initial_output)
    {
        LinearLayout row_layout = new LinearLayout(this);
        row_layout.setOrientation(LinearLayout.HORIZONTAL);
        row_layout.setGravity(Gravity.CENTER_VERTICAL);
        row_layout.setPadding(dp(8), dp(8), dp(8), dp(8));
        row_layout.setBackgroundColor(Color.WHITE);
        row_layout.setElevation(dp(2));

        LinearLayout.LayoutParams row_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row_params.topMargin = dp(8);

        TextView row_number = new TextView(this);
        row_number.setTextColor(Color.rgb(102, 112, 133));
        row_number.setTextSize(14f);
        row_number.setGravity(Gravity.CENTER);
        row_number.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams number_params = new LinearLayout.LayoutParams(dp(28), dp(48));
        row_layout.addView(row_number, number_params);

        final PasteAwareEditText output = new PasteAwareEditText(this);
        style_edit_text(output, R.string.keymap_builder_output_hint, true);
        output.setTextSize(14f);
        output.setMinHeight(dp(48));
        LinearLayout.LayoutParams output_params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        output_params.setMarginStart(dp(8));
        output_params.setMarginEnd(dp(8));
        row_layout.addView(output, output_params);

        final EditText keys = make_edit_text(R.string.keymap_builder_keys_hint, false);
        keys.setTextSize(14f);
        keys.setSingleLine(true);
        keys.setMinHeight(dp(48));
        LinearLayout.LayoutParams keys_params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        keys_params.setMarginEnd(dp(8));
        row_layout.addView(keys, keys_params);

        final ImageButton remove_btn = new ImageButton(this);
        remove_btn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove_btn.setBackgroundColor(Color.TRANSPARENT);
        remove_btn.setColorFilter(Color.rgb(180, 45, 45));
        remove_btn.setScaleType(ImageView.ScaleType.CENTER);
        remove_btn.setPadding(0, 0, 0, 0);
        remove_btn.setMinimumWidth(0);
        remove_btn.setMinimumHeight(0);
        remove_btn.setContentDescription("Remove row");
        LinearLayout.LayoutParams remove_params = new LinearLayout.LayoutParams(dp(28), dp(28));
        row_layout.addView(remove_btn, remove_params);

        final Row row = new Row(row_layout, output, keys, row_number);
        _rows.add(index, row);
        _rows_container.addView(row_layout, index, row_params);
        refresh_row_numbers();

        remove_btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (_rows.size() <= 1)
                    return;
                _rows.remove(row);
                _rows_container.removeView(row_layout);
                refresh_row_numbers();
                recompute_duplicates();
            }
        });

        output.addTextChangedListener(new TextWatcher()
        {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s)
            {
                if (_suppress_auto_add_row)
                    return;
                boolean is_last_row = !_rows.isEmpty() && _rows.get(_rows.size() - 1) == row;
                if (is_last_row && s.length() > 0)
                    add_mapping_row();
            }
        });

        keys.addTextChangedListener(new TextWatcher()
        {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s)
            {
                recompute_duplicates();
            }
        });

        output.set_paste_listener(new PasteAwareEditText.PasteListener()
        {
            @Override
            public boolean on_paste_multiline(EditText target, String pasted_text)
            {
                handle_paste_multiline(row, pasted_text);
                return true;
            }
        });

        if (!initial_output.isEmpty())
            output.setText(initial_output);

        apply_row_filters();

        return row;
    }

    private void refresh_row_numbers()
    {
        for (int i = 0; i < _rows.size(); i++)
            _rows.get(i).number.setText(String.valueOf(i + 1));
    }

    private void add_mapping_row()
    {
        add_mapping_row_at(_rows.size(), "");
    }

    private void ensure_trailing_empty_row()
    {
        if (_rows.isEmpty() || _rows.get(_rows.size() - 1).output.getText().length() > 0)
            add_mapping_row();
    }

    /** Recomputes which rows currently share a duplicated key, based on
     every row's "keys" field. Enables/disables the "Dup only"
     checkbox accordingly, auto-unchecking (and showing every row
     again) if the duplicates that caused it to be checked have since
     been resolved. */
    private void recompute_duplicates()
    {
        if (_solo_duplicates_checkbox == null)
            return;

        LinkedHashMap<String, List<Integer>> key_to_rows = new LinkedHashMap<>();
        for (int i = 0; i < _rows.size(); i++)
        {
            String keys_raw = _rows.get(i).keys.getText().toString();
            if (keys_raw.isEmpty())
                continue;
            for (String key : split_keys(keys_raw))
            {
                if (key.isEmpty())
                    continue;
                List<Integer> idx_list = key_to_rows.get(key);
                if (idx_list == null)
                {
                    idx_list = new ArrayList<>();
                    key_to_rows.put(key, idx_list);
                }
                if (!idx_list.contains(i))
                    idx_list.add(i);
            }
        }

        Set<Integer> duplicate_row_indices = new HashSet<>();
        for (Map.Entry<String, List<Integer>> e : key_to_rows.entrySet())
            if (e.getValue().size() > 1)
                duplicate_row_indices.addAll(e.getValue());

        _last_duplicate_row_indices = duplicate_row_indices;
        boolean has_duplicates = !duplicate_row_indices.isEmpty();
        _solo_duplicates_checkbox.setEnabled(has_duplicates);

        if (!has_duplicates && _solo_duplicates_checkbox.isChecked())
            _solo_duplicates_checkbox.setChecked(false); // Triggers listener -> apply_row_filters().
        else
            apply_row_filters();
    }

    /** Hides every row that isn't in _last_duplicate_row_indices when the
     checkbox is checked and enabled - without removing rows from
     _rows or renumbering, so row numbers stay stable regardless of
     which rows are currently visible. */
    /** Combines the duplicate-only filter and the search filter (AND
     between the two, OR within comma-separated search terms) and
     applies the result as row visibility - without removing rows
     from _rows or renumbering, so row numbers stay stable regardless
     of which rows are currently visible.

     The current trailing empty row (both output and keys blank) is
     always shown regardless of active filters, so there's always a
     place to type a new entry directly even while filtered. */
    private void apply_row_filters()
    {
        boolean solo = _solo_duplicates_checkbox != null
                && _solo_duplicates_checkbox.isEnabled()
                && _solo_duplicates_checkbox.isChecked();

        List<String> search_terms = new ArrayList<>();
        if (_search_input != null)
        {
            String raw = _search_input.getText().toString().trim();
            if (!raw.isEmpty())
                for (String term : raw.split(","))
                {
                    String t = term.trim();
                    if (!t.isEmpty())
                        search_terms.add(t);
                }
        }
        boolean search_by_keys = _search_keys_radio != null && _search_keys_radio.isChecked();

        for (int i = 0; i < _rows.size(); i++)
        {
            Row row = _rows.get(i);
            String output_text = row.output.getText().toString();
            String keys_text = row.keys.getText().toString();

            boolean is_trailing_empty_row =
                    (i == _rows.size() - 1) && output_text.isEmpty() && keys_text.isEmpty();

            boolean passes_duplicate = !solo || _last_duplicate_row_indices.contains(i);

            boolean passes_search = true;
            if (!search_terms.isEmpty())
            {
                String haystack = search_by_keys ? keys_text : output_text;
                passes_search = false;
                for (String term : search_terms)
                {
                    if (haystack.contains(term))
                    {
                        passes_search = true;
                        break;
                    }
                }
            }

            boolean visible = is_trailing_empty_row || (passes_duplicate && passes_search);
            row.container.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void handle_paste_multiline(final Row row, final String pasted_text)
    {
        ConfirmDialog.show(this,
                getString(R.string.keymap_builder_paste_title),
                getString(R.string.keymap_builder_paste_message),
                getString(R.string.keymap_builder_paste_split),
                getString(R.string.keymap_builder_paste_single),
                new ConfirmDialog.OnResult()
                {
                    public void result(boolean positive)
                    {
                        if (positive)
                        {
                            split_paste_into_rows(row, pasted_text);
                        }
                        else
                        {
                            int start = row.output.getSelectionStart();
                            int end = row.output.getSelectionEnd();
                            if (start < 0) start = row.output.getText().length();
                            if (end < 0) end = start;
                            row.output.getText().replace(
                                    Math.min(start, end), Math.max(start, end), pasted_text);
                        }
                    }
                });
    }

    private void split_paste_into_rows(Row row, String pasted_text)
    {
        String[] lines = pasted_text.split("\n", -1);
        int n = lines.length;
        if (n > 1 && lines[n - 1].isEmpty())
            n--;
        if (n == 0)
            return;

        _suppress_auto_add_row = true;
        row.output.setText(lines[0]);
        int insert_index = _rows.indexOf(row) + 1;
        for (int i = 1; i < n; i++)
        {
            add_mapping_row_at(insert_index, lines[i]);
            insert_index++;
        }
        _suppress_auto_add_row = false;
        ensure_trailing_empty_row();
    }

    static List<String> split_keys(String raw)
    {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int len = raw.length();
        while (i < len)
        {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < len && raw.charAt(i + 1) == ',')
            {
                cur.append(',');
                i += 2;
            }
            else if (c == ',')
            {
                result.add(cur.toString());
                cur.setLength(0);
                i++;
            }
            else
            {
                cur.append(c);
                i++;
            }
        }
        result.add(cur.toString());
        return result;
    }

    private static String join_keys_escaped(
            List<String> keys)
    {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < keys.size(); i++)
        {
            if (i > 0)
                b.append(",");

            String key = keys.get(i);

            /*
             * Escape only when the complete key is
             * a literal comma.
             */
            if (key.equals(","))
                b.append("\\,");
            else
                b.append(key);
        }

        return b.toString();
    }

    /** Rebuilds the row list from [raw_entries] (which may contain
     duplicate keys - each duplicate occurrence naturally lands in a
     different row, since it groups by OUTPUT value, not by key).
     [name_for_field], if non-null, also sets the name field. */
    private void populate_from_entries(List<Map.Entry<String, String>> raw_entries, String name_for_field)
    {
        if (name_for_field != null)
            _name_input.setText(name_for_field);

        LinkedHashMap<String, List<String>> grouped =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> e : raw_entries)
        {
            if (e.getKey().equals("keymap_name"))
                continue;

            String value = e.getValue();

            List<String> keys_for_value =
                    grouped.get(value);

            if (keys_for_value == null)
            {
                keys_for_value = new ArrayList<>();
                grouped.put(value, keys_for_value);
            }

            keys_for_value.add(e.getKey());
        }

        _suppress_auto_add_row = true;
        for (Map.Entry<String, List<String>> e : grouped.entrySet())
        {
            Row row = add_mapping_row_at(_rows.size(), e.getKey());
            row.keys.setText(join_keys_escaped(e.getValue()));
        }
        _suppress_auto_add_row = false;

        ensure_trailing_empty_row();
        refresh_row_numbers();
        recompute_duplicates();
    }

    private void populate_for_edit(String name)
    {
        KeymapManager.StoredKeymap stored = KeymapManager.find(this, name);
        if (stored == null)
            return;
        try
        {
            List<Map.Entry<String, String>> entries = KeymapJsonUtils.parse_flat_object(stored.json);
            String name_value = null;
            for (Map.Entry<String, String> e : entries)
                if (e.getKey().equals("keymap_name"))
                    name_value = e.getValue();
            populate_from_entries(entries, name_value != null ? name_value : name);
        }
        catch (KeymapJsonUtils.ParseError e)
        {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Populates from arbitrary JSON text (may contain duplicate keys),
     e.g. the current unsaved text from the "Keymap" dialog when the
     user tapped "Keymap Builder". */
    private void populate_from_json_text(String json_text)
    {
        try
        {
            List<Map.Entry<String, String>> entries =
                    KeymapJsonUtils.parse_flat_object(json_text);

            String name_value = null;

            for (Map.Entry<String, String> e : entries)
            {
                if (e.getKey().equals("keymap_name"))
                {
                    name_value = e.getValue();
                    break;
                }
            }

            /*
             * Dialog format:
             *
             * "aa": "ஆ"
             * "A":  "ஆ"
             *
             * populate_from_entries() groups by value and produces:
             *
             * output = "ஆ"
             * keys   = "aa,A"
             */
            populate_from_entries(entries, name_value);
        }
        catch (KeymapJsonUtils.ParseError e)
        {
            Toast.makeText(
                    this,
                    "Could not parse current text: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
    private void populate_from_raw_entries(
            List<Map.Entry<String, String>> entries)
    {
        String name_value = null;

        for (Map.Entry<String, String> e : entries)
        {
            if (e.getKey().equals("keymap_name"))
            {
                name_value = e.getValue();
                break;
            }
        }

        if (name_value != null)
            _name_input.setText(name_value);

        for (Row r : new ArrayList<>(_rows))
        {
            _rows.remove(r);
            _rows_container.removeView(r.container);
        }

        _suppress_auto_add_row = true;

        for (Map.Entry<String, String> e : entries)
        {
            if (e.getKey().equals("keymap_name"))
                continue;

            /*
             * Raw JSON format:
             *
             * JSON key   -> output field
             * JSON value -> keys field
             *
             * Example:
             * "ஆ": "aa,A"
             *
             * output = "ஆ"
             * keys   = "aa,A"
             */
            Row row = add_mapping_row_at(
                    _rows.size(),
                    e.getValue());


            /*
             * Copy the value exactly.
             * Do not call join_keys_escaped() here.
             * Do not trim or modify backslashes.
             */
            row.keys.setText(e.getKey());
        }

        _suppress_auto_add_row = false;

        ensure_trailing_empty_row();
        refresh_row_numbers();
        recompute_duplicates();
    }

    /** Shows the raw per-row JSON view: {"<output>": "<comma-separated
     keys>", ...} - a diagnostic view of the builder's own row
     structure (not the final generated keymap format), reusing the
     same dialog style as Custom Layout / Keymap but with no Remove or
     Keymap Builder button (view-only; OK just closes it). */
    private void show_raw_json_dialog()
    {
        String json = build_raw_rows_json();
        CustomLayoutEditDialog.show(this, json, false,
                R.string.keymap_builder_raw_json_title,
                0,
                null,
                new CustomLayoutEditDialog.Callback()
                {
                    @Override public void select(String text) { /* view-only */ }
                    @Override public String validate(String text) { return null; }
                });
    }

    /** Shows the same-format dialog as "Raw JSON" ({"<output>":
     "<comma-separated keys>", ...}), but editable: pasting/typing a
     JSON object here and tapping the positive button ("Import")
     replaces the current row list with rows built directly from it -
     output = key, keys = value, exactly the reverse mapping of the
     raw-rows view, with no grouping/deduplication applied (unlike
     populate_from_entries, which groups by output value for the
     actual keymap format). Validation only checks that the text is
     syntactically valid JSON; it does not require "keymap_name" or
     reject duplicate outputs, since this is a bulk-editing convenience
     rather than the final save step. */
    private void show_import_json_dialog()
    {
        String current = build_raw_rows_json();

        CustomLayoutEditDialog.show(this, current, false,
                R.string.keymap_builder_import_json_title,
                0,
                null,
                new CustomLayoutEditDialog.Callback()
                {
                    @Override
                    public void select(String text)
                    {
                        import_raw_rows_json(text);
                    }

                    @Override
                    public String validate(String text)
                    {
                        try
                        {
                            KeymapJsonUtils.parse_flat_object(normalizeRawJson(text));
                            return null;
                        }
                        catch (KeymapJsonUtils.ParseError e)
                        {
                            return e.getMessage();
                        }
                    }
                });
    }

    /** Rebuilds the row list directly from [json_text] in the raw-rows
     format: each entry becomes exactly one row (output = key, keys =
     value), except "keymap_name" which populates the name field
     instead. Unlike populate_from_entries(), this does NOT group
     multiple entries sharing the same output into a single row - the
     raw-rows format already has one entry per row by construction, so
     each is imported as-is. */
    private void import_raw_rows_json(String json_text)
    {
        try
        {
            List<Map.Entry<String, String>> entries =
                    KeymapJsonUtils.parse_flat_object(
                            normalizeRawJson(json_text));

            String name_value = null;

            for (Map.Entry<String, String> e : entries)
            {
                if (e.getKey().equals("keymap_name"))
                {
                    name_value = e.getValue();
                    break;
                }
            }

            if (name_value != null)
                _name_input.setText(name_value);

            for (Row r : new ArrayList<>(_rows))
            {
                _rows.remove(r);
                _rows_container.removeView(r.container);
            }

            _suppress_auto_add_row = true;

            for (Map.Entry<String, String> e : entries)
            {
                if (e.getKey().equals("keymap_name"))
                    continue;

                /*
                 * Builder raw JSON format:
                 *
                 * "ஆ": "aa,A"
                 *
                 * JSON key   -> output field
                 * JSON value -> keys field
                 */
                Row row = add_mapping_row_at(
                        _rows.size(),
                        e.getKey());

                /*
                 * Copy the value exactly.
                 * Do not call join_keys_escaped() here.
                 */
                row.keys.setText(e.getValue());
            }

            _suppress_auto_add_row = false;

            ensure_trailing_empty_row();
            refresh_row_numbers();
            recompute_duplicates();

            Toast.makeText(
                    this,
                    R.string.keymap_builder_import_success,
                    Toast.LENGTH_SHORT).show();
        }
        catch (KeymapJsonUtils.ParseError e)
        {
            Toast.makeText(
                    this,
                    e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String build_raw_rows_json()
    {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"keymap_name\": \"")
                .append(escape_json_string(_name_input.getText().toString()))
                .append("\"");

        for (Row row : _rows)
        {
            String output = row.output.getText().toString();
            String keys_raw = row.keys.getText().toString();

            // Export only complete mapping rows.
            if (output.isEmpty() || keys_raw.isEmpty())
                continue;

            b.append(",\n");
            b.append("  \"")
                    .append(escape_json_string(output))
                    .append("\": \"")
                    .append(escape_json_string(keys_raw))
                    .append("\"");
        }

        b.append("\n}");

        // escape_json_string() doubles every literal backslash (e.g. the
        // "\," used to escape a comma-as-key) into "\\" so the text is
        // valid on its own. Collapsing "\\\\" back to a single "\" here
        // restores the exact "aa,\,,A" form the user types/expects to
        // see. import_raw_rows_json()'s normalizeRawJson() step expects
        // exactly this shape as input - it re-doubles any stray
        // backslash before handing the text to the strict JSON parser.
        return b.toString().replace("\\\\", "\\");
    }


    private void on_create_clicked()
    {
        String name = _name_input.getText().toString().trim();
        if (name.isEmpty())
        {
            Toast.makeText(this, R.string.keymap_builder_error_name_required, Toast.LENGTH_SHORT).show();
            _name_input.requestFocus();
            return;
        }

        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("keymap_name", name);

        LinkedHashMap<String, List<Integer>> key_rows = new LinkedHashMap<>();
        List<Integer> name_key_rows = new ArrayList<>();
        name_key_rows.add(0);
        key_rows.put("keymap_name", name_key_rows);

        for (int i = 0; i < _rows.size(); i++)
        {
            Row row = _rows.get(i);
            int row_number = i + 1;
            String output = row.output.getText().toString();
            String keys_raw = row.keys.getText().toString();
            if (output.isEmpty() || keys_raw.isEmpty())
                continue;

            for (String key : split_keys(keys_raw))
            {
                if (key.isEmpty())
                    continue;
                List<Integer> rows_for_key = key_rows.get(key);
                if (rows_for_key == null)
                {
                    rows_for_key = new ArrayList<>();
                    key_rows.put(key, rows_for_key);
                }
                if (!rows_for_key.contains(row_number))
                    rows_for_key.add(row_number);
                if (!entries.containsKey(key))
                    entries.put(key, output);
            }
        }

        StringBuilder duplicate_message = new StringBuilder();
        boolean has_duplicates = false;
        for (Map.Entry<String, List<Integer>> entry : key_rows.entrySet())
        {
            List<Integer> rows_for_key = entry.getValue();
            if (rows_for_key.size() <= 1)
                continue;
            has_duplicates = true;
            duplicate_message.append("\"").append(entry.getKey()).append("\" appears in rows: ");
            for (int i = 0; i < rows_for_key.size(); i++)
            {
                if (i > 0) duplicate_message.append(", ");
                duplicate_message.append(rows_for_key.get(i));
            }
            duplicate_message.append("\n");
        }

        if (has_duplicates)
        {
            show_duplicate_keys_dialog(duplicate_message.toString());
            return;
        }

        if (entries.size() <= 1)
        {
            Toast.makeText(this, R.string.keymap_builder_error_no_mappings, Toast.LENGTH_SHORT).show();
            return;
        }

        KeymapManager.StoredKeymap existing = KeymapManager.find(this, name);
        boolean overwriting_different_entry = existing != null
                && (_editing_original_name == null || !_editing_original_name.equals(name));

        if (overwriting_different_entry)
        {
            final String final_name = name;
            final LinkedHashMap<String, String> final_entries = entries;
            ConfirmDialog.show(this,
                    getString(R.string.keymap_builder_overwrite_title),
                    getString(R.string.keymap_builder_overwrite_message, name),
                    getString(R.string.keymap_builder_overwrite_yes),
                    getString(R.string.keymap_builder_overwrite_cancel),
                    new ConfirmDialog.OnResult()
                    {
                        public void result(boolean positive)
                        {
                            if (positive)
                                save_and_finish(final_name, final_entries);
                        }
                    });
            return;
        }

        save_and_finish(name, entries);
    }

    private void save_and_finish(String name, LinkedHashMap<String, String> entries)
    {
        String json = build_keymap_json(entries);
        KeymapManager.add(this, new KeymapManager.StoredKeymap(name, json));
        if (_editing_original_name != null && !_editing_original_name.equals(name))
            KeymapManager.remove(this, _editing_original_name);
        Toast.makeText(this, R.string.keymap_builder_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private static String build_keymap_json(LinkedHashMap<String, String> entries)
    {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        int i = 0;
        int last = entries.size() - 1;
        for (Map.Entry<String, String> e : entries.entrySet())
        {
            b.append("  \"").append(escape_json_string(e.getKey())).append("\": \"")
                    .append(escape_json_string(e.getValue())).append("\"");
            if (i < last)
                b.append(",");
            b.append("\n");
            i++;
        }
        b.append("}");
        return b.toString();
    }

    private static String escape_json_string(String s)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20)
                        b.append(String.format("\\u%04x", (int)c));
                    else
                        b.append(c);
            }
        }
        return b.toString();
    }

    private int dp(int value)
    {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    static class PasteAwareEditText extends EditText
    {
        interface PasteListener
        {
            boolean on_paste_multiline(EditText target, String pasted_text);
        }

        private PasteListener _listener;

        public PasteAwareEditText(Context ctx) { super(ctx); }

        public void set_paste_listener(PasteListener l) { _listener = l; }

        @Override
        public boolean onTextContextMenuItem(int id)
        {
            if (id == android.R.id.paste && _listener != null)
            {
                ClipboardManager cm = (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0)
                {
                    CharSequence pasted = cm.getPrimaryClip().getItemAt(0).coerceToText(getContext());
                    if (pasted != null && pasted.toString().contains("\n"))
                    {
                        if (_listener.on_paste_multiline(this, pasted.toString()))
                            return true;
                    }
                }
            }
            return super.onTextContextMenuItem(id);
        }
    }

    private void show_duplicate_keys_dialog(String message)
    {
        ConfirmDialog.show(this,
                "Duplicate keys",
                "The following keys are used more than once:\n\n"
                        + message
                        + "\nPlease remove or change the duplicate keys.",
                getString(android.R.string.ok),
                null,
                null);
    }

    private void apply_modern_edit_background(final EditText edit_text)
    {
        final int normal_border_color = Color.rgb(208, 213, 221);
        final int focused_border_color = Color.rgb(52, 120, 246);
        final int normal_background_color = Color.WHITE;
        final int focused_background_color = Color.rgb(248, 251, 255);

        edit_text.setPadding(dp(12), dp(10), dp(12), dp(10));
        edit_text.setElevation(dp(1));

        GradientDrawable normal_background = new GradientDrawable();
        normal_background.setShape(GradientDrawable.RECTANGLE);
        normal_background.setColor(normal_background_color);
        normal_background.setCornerRadius(dp(10));
        normal_background.setStroke(dp(1), normal_border_color);
        edit_text.setBackground(normal_background);

        edit_text.setOnFocusChangeListener(new View.OnFocusChangeListener()
        {
            @Override
            public void onFocusChange(View view, boolean has_focus)
            {
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dp(10));
                if (has_focus)
                {
                    background.setColor(focused_background_color);
                    background.setStroke(dp(2), focused_border_color);
                }
                else
                {
                    background.setColor(normal_background_color);
                    background.setStroke(dp(1), normal_border_color);
                }
                edit_text.setBackground(background);
            }
        });
    }

    private static String normalizeRawJson(String json)
    {
        StringBuilder out = new StringBuilder();

        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++)
        {
            char c = json.charAt(i);

            if (escaped)
            {
                // JSON supports these escapes already.
                if ("\"\\/bfnrtu".indexOf(c) >= 0)
                {
                    out.append(c);
                }
                else
                {
                    // Unknown escape (such as \,)
                    // Turn it into \\,
                    out.append('\\');
                    out.append(c);
                }

                escaped = false;
                continue;
            }

            if (c == '\\')
            {
                out.append(c);
                escaped = true;
                continue;
            }

            if (c == '"')
                inString = !inString;

            out.append(c);
        }

        return out.toString();
    }

    /** Fills the current trailing empty row from the fixed quick-add
     fields instead of requiring the user to scroll to the bottom of
     a long row list. Setting the row's own output field reuses the
     exact same "last row + non-empty text -> append a new empty row"
     watcher that already exists on every row, so a fresh empty row
     appears below it automatically, and setting the keys field
     reuses that field's own watcher to refresh duplicate detection -
     no separate logic is needed for either. */
    private void add_via_quick_fields()
    {
        String output = _quick_output.getText().toString();
        String keys = _quick_keys.getText().toString();

        if (output.isEmpty() || keys.isEmpty())
        {
            Toast.makeText(this, R.string.keymap_builder_quick_add_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Row last = _rows.get(_rows.size() - 1);
        last.output.setText(output);
        last.keys.setText(keys);

        _quick_output.setText("");
        _quick_keys.setText("");
    }
}