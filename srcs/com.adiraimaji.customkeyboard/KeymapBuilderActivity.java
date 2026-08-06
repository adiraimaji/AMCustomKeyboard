package com.adiraimaji.customkeyboard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.adiraimaji.customkeyboard.prefs.KeymapManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import java.util.LinkedHashMap;
import java.util.Map;

/** A guided editor for building a keymap JSON without hand-writing it.

 Row 0 is a permanent, non-removable "name row": its output (left)
 field is where the user types the keymap's name (the JSON value for
 "keymap_name"), and its keys (right) side is a fixed, non-editable
 label always showing "keymap_name" (the JSON key). This reuses the
 exact same row layout as every other mapping row rather than a
 separate top-level field.

 Every following row is a normal output/keys mapping row: an output
 field (left, multiline) and a comma-separated list of keys that
 should produce it (right). Typing into the last mapping row's output
 field automatically appends a new empty mapping row below it. */
public class KeymapBuilderActivity extends Activity
{
    /** The output field of the fixed name row (row 0). Holds the keymap's
     name - i.e. the JSON value for the "keymap_name" key. */
    private EditText _name_output;

    private LinearLayout _rows_container;
    /** Mapping rows only (row 0, the name row, is NOT included here). */
    private final List<Row> _rows = new ArrayList<>();

    private static final class Row
    {
        final View container;
        final EditText output;
        final EditText keys;

        Row(View container_, EditText output_, EditText keys_)
        {
            container = container_;
            output = output_;
            keys = keys_;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView instructions = new TextView(this);
        instructions.setText(R.string.keymap_builder_instructions);
        instructions.setTextColor(Color.DKGRAY);
        instructions.setTextSize(12f);
        LinearLayout.LayoutParams instr_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        instr_params.bottomMargin = dp(12);
        root.addView(instructions, instr_params);

        _rows_container = new LinearLayout(this);
        _rows_container.setOrientation(LinearLayout.VERTICAL);
        root.addView(_rows_container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        add_name_row();     // Row 0: fixed, holds the keymap name.
        add_mapping_row();  // Row 1: first regular output/keys row.

        Button create_btn = new Button(this);
        create_btn.setText(R.string.keymap_builder_create);
        LinearLayout.LayoutParams create_btn_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        create_btn_params.topMargin = dp(24);
        create_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v) { on_create_clicked(); }
        });
        root.addView(create_btn, create_btn_params);

        scroll.addView(root);
        setContentView(scroll);
    }

    /** Creates an EditText with explicit, theme-independent styling so it's
     always visible regardless of what the activity inherits. */
    private EditText make_edit_text(int hint_res, boolean multiline)
    {
        EditText et = new EditText(this);
        et.setHint(hint_res);
        et.setTextColor(Color.BLACK);
        et.setHintTextColor(Color.GRAY);
        et.setBackgroundResource(android.R.drawable.edit_text);
        int inner_pad = dp(8);
        et.setPadding(inner_pad, inner_pad, inner_pad, inner_pad);
        if (multiline)
        {
            et.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(1);
        }
        else
        {
            et.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return et;
    }

    /** Row 0: output field is editable (the keymap name goes here); the
     keys side is a plain, non-interactive label fixed to "keymap_name",
     styled to match the look of the editable fields (same border,
     same size) so the row still reads as a matching pair. No remove
     button - this row can never be deleted. */
    private void add_name_row()
    {
        LinearLayout row_layout = new LinearLayout(this);
        row_layout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        _name_output = make_edit_text(R.string.keymap_builder_name_hint, false);
        _name_output.setSingleLine(true);
        LinearLayout.LayoutParams output_params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        output_params.setMarginEnd(dp(8));
        row_layout.addView(_name_output, output_params);

        TextView fixed_key_label = new TextView(this);
        fixed_key_label.setText(R.string.keymap_builder_name_key_fixed);
        fixed_key_label.setTextColor(Color.DKGRAY);
        fixed_key_label.setGravity(Gravity.CENTER_VERTICAL);
        fixed_key_label.setBackgroundResource(android.R.drawable.edit_text);
        int inner_pad = dp(8);
        fixed_key_label.setPadding(inner_pad, inner_pad, inner_pad, inner_pad);
        LinearLayout.LayoutParams key_label_params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row_layout.addView(fixed_key_label, key_label_params);

        _rows_container.addView(row_layout, row_params);
    }

    /** Appends a new empty mapping row (output field + keys field + remove
     button) to [_rows_container]. Attaches a watcher to the output
     field so that typing into it - while it's still the LAST mapping
     row - automatically appends the next empty row, letting the user
     keep typing without ever pressing an explicit "add row" button. */
    private void add_mapping_row()
    {
        LinearLayout row_layout = new LinearLayout(this);
        row_layout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row_params.topMargin = dp(8);

        final EditText output = make_edit_text(R.string.keymap_builder_output_hint, true);
        LinearLayout.LayoutParams output_params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        output_params.setMarginEnd(dp(8));
        row_layout.addView(output, output_params);

        final EditText keys = make_edit_text(R.string.keymap_builder_keys_hint, false);
        LinearLayout.LayoutParams keys_params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        keys_params.setMarginEnd(dp(8));
        row_layout.addView(keys, keys_params);

        final Button remove_btn = new Button(this);
        remove_btn.setText(R.string.keymap_builder_remove_row);
        row_layout.addView(remove_btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final Row row = new Row(row_layout, output, keys);
        _rows.add(row);
        _rows_container.addView(row_layout, row_params);

        remove_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                if (_rows.size() <= 1)
                    return; // Always keep at least one mapping row.
                _rows.remove(row);
                _rows_container.removeView(row_layout);
            }
        });

        output.addTextChangedListener(new TextWatcher()
        {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            public void afterTextChanged(Editable s)
            {
                boolean is_last_row =
                        !_rows.isEmpty() && _rows.get(_rows.size() - 1) == row;
                if (is_last_row && s.length() > 0)
                    add_mapping_row();
            }
        });
    }

    /** Splits a comma-separated keys field into individual keys, treating a
     backslash-escaped comma ("\,") as a literal comma character rather
     than a separator. This lets a row define a key that is itself a
     comma, e.g. keys field "\,,cm" produces two keys: "," and "cm". */
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
                result.add(cur.toString().trim());
                cur.setLength(0);
                i++;
            }
            else
            {
                cur.append(c);
                i++;
            }
        }
        result.add(cur.toString().trim());
        return result;
    }

    /** Builds the keymap JSON: "keymap_name" comes from row 0's output
     field, every following mapping row that has both an output and at
     least one key contributes one or more entries. The JSON is written
     out manually (not via JSONObject.toString(), which doesn't preserve
     insertion order and produces a single compact line) so that each
     key-value pair sits on its own line in the order the user entered
     it - this is what gets saved and what the user will see if they
     later reopen this keymap to edit it from the "Keymap N: name" row.
     Saves via KeymapManager (same storage the "Add new Keymap JSON"
     dialog uses) and closes. */
    private void on_create_clicked()
    {
        String name = _name_output.getText().toString().trim();
        if (name.isEmpty())
        {
            Toast.makeText(this, R.string.keymap_builder_error_name_required,
                    Toast.LENGTH_SHORT).show();
            _name_output.requestFocus();
            return;
        }

        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("keymap_name", name);

        for (Row row : _rows)
        {
            String output = row.output.getText().toString();
            String keys_raw = row.keys.getText().toString();

            if (output.isEmpty() || keys_raw.trim().isEmpty())
                continue;

            for (String key : split_keys(keys_raw))
            {
                if (key.isEmpty())
                    continue;
                entries.put(key, output);
            }
        }

        if (entries.size() <= 1) // Only "keymap_name" present, no mappings.
        {
            Toast.makeText(this, R.string.keymap_builder_error_no_mappings,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String json = build_keymap_json(entries);

        KeymapManager.add(this, new KeymapManager.StoredKeymap(name, json));

        Toast.makeText(this, R.string.keymap_builder_saved, Toast.LENGTH_SHORT).show();

        setResult(RESULT_OK);
        finish();
    }

    /** Serializes [entries] as JSON with exactly one key-value pair per
     line, in insertion order, e.g.:
     {
     "keymap_name": "tamil",
     "a": "\u0b85"
     } */
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

    /** Escapes a string for safe inclusion inside a JSON double-quoted
     value, including turning literal newlines (from a multiline output
     field) into the two-character escape sequence "\n". */
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
}