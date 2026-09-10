package com.adiraimaji.customkeyboard;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.appbar.MaterialToolbar;

import com.adiraimaji.customkeyboard.prefs.TaskerAutomationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A guided, form-based editor for the single Tasker Automation JSON -
 the same underlying config [TaskerAutomationPreference]'s raw JSON
 dialog edits, but as fixed + dynamically-growing form fields instead
 of hand-written JSON, the same way KeymapBuilderActivity is to the
 keymap JSON editor. Uses settingsTheme (declared on this activity in
 the manifest) and the same MaterialToolbar styling as
 TaskerAutomationGuideActivity - primary-blue action bar, gold title
 text - so the two screens read as one consistent "Tasker Automation"
 area rather than 2 differently-themed screens.

 Three groups, top to bottom:
 - A fixed 3-row card for "amck_replace" / "amck_append" /
 "amck_timeout" - always exactly these 3 fields, never added to or
 removed.
 - A dynamically-growing list of keyword -> Tasker task name rows
 (the "runtask1": "Task 1" style entries used with the amck_replace/
 amck_append triggers above).
 - A dynamically-growing list of "amck_patterns" entries, each its
 own collapsible card: a header (always visible - a number badge, the
 optional "name" field, and a delete button, confirmed before it
 actually removes anything) plus a body (prefix/optional regex/
 suffix/task, all regexes except task, plus two checkboxes -
 "replace_prefix" and "fire_on_suffix" - stacked rather than
 side-by-side since that many inputs would be unreadably cramped on a
 phone-width row) that only shows when the card is expanded. Cards
 loaded from an existing config start collapsed; one just added via
 "+ Add expand pattern" starts expanded, since there's nothing in it
 yet to hide.

 On Save, the form is serialized to the same JSON shape
 [TaskerAutomationConfig.parse] expects and validated with that same
 method, so every rule enforced there (non-empty prefix/suffix/task,
 no duplicate keyword, no duplicate prefix+suffix pattern, valid
 regex, timeout range, distinct replace/append triggers) applies
 identically here - errors are shown inline rather than saved. */
public class TaskerAutomationBuilderActivity extends AppCompatActivity
{
    private EditText _replace_input;
    private EditText _append_input;
    private EditText _timeout_input;

    private LinearLayout _tasks_container;
    private LinearLayout _patterns_container;
    private TextView _error_text;

    private final List<TaskRow> _task_rows = new ArrayList<>();
    private final List<PatternRow> _pattern_rows = new ArrayList<>();

    private static final class TaskRow
    {
        final LinearLayout layout;
        final EditText keyword;
        final EditText task_name;
        final TextView badge;

        TaskRow(LinearLayout layout_, EditText keyword_, EditText task_name_, TextView badge_)
        {
            layout = layout_;
            keyword = keyword_;
            task_name = task_name_;
            badge = badge_;
        }
    }

    /** [name] is the user-facing label shown in the card's header
     (see [add_pattern_row]) - purely organizational, never sent to
     Tasker, and optional (an empty field means "unnamed", same as the
     "name" JSON key being entirely absent). [body] is the collapsible
     container holding every field below the header (prefix through
     the two checkboxes) - [chevron] toggles its visibility; see
     [add_pattern_row]'s expand/collapse handling. */
    private static final class PatternRow
    {
        final LinearLayout layout;
        final EditText name;
        final View body;
        final TextView chevron;
        final EditText prefix;
        final EditText regex;
        final EditText suffix;
        final EditText task;
        final CheckBox replace_prefix;
        final CheckBox fire_on_suffix;
        final TextView badge;

        PatternRow(LinearLayout layout_, EditText name_, View body_, TextView chevron_,
                  EditText prefix_, EditText regex_, EditText suffix_, EditText task_,
                  CheckBox replace_prefix_, CheckBox fire_on_suffix_, TextView badge_)
        {
            layout = layout_;
            name = name_;
            body = body_;
            chevron = chevron_;
            prefix = prefix_;
            regex = regex_;
            suffix = suffix_;
            task = task_;
            replace_prefix = replace_prefix_;
            fire_on_suffix = fire_on_suffix_;
            badge = badge_;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_automation_builder);

        MaterialToolbar toolbar = findViewById(R.id.tab_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout global_card = findViewById(R.id.tab_global_card);
        _tasks_container = findViewById(R.id.tab_tasks_container);
        _patterns_container = findViewById(R.id.tab_patterns_container);
        _error_text = findViewById(R.id.tab_error_text);

        _replace_input = add_labeled_field(global_card, R.string.tasker_builder_replace_trigger_label,
                R.string.tasker_builder_replace_trigger_hint, false, false);
        _append_input = add_labeled_field(global_card, R.string.tasker_builder_append_trigger_label,
                R.string.tasker_builder_append_trigger_hint, false, false);
        _timeout_input = add_labeled_field(global_card, R.string.tasker_builder_timeout_label,
                R.string.tasker_builder_timeout_hint, true, true);

        load_existing_or_defaults();

        findViewById(R.id.tab_add_task_button).setOnClickListener(v -> add_task_row("", ""));
        findViewById(R.id.tab_add_pattern_button).setOnClickListener(v -> add_pattern_row("", "", "", "", "", true, true, true));
        findViewById(R.id.tab_save_button).setOnClickListener(v -> save());
    }

    /** Loads the currently stored config into the form, or - if
     nothing is stored yet, or what's stored fails to parse (e.g. it
     was hand-edited into an invalid state in the raw JSON dialog) -
     falls back to the same built-in defaults
     [TaskerAutomationPreference]'s raw JSON dialog shows for a blank
     config. Never shows a parse error here: this screen's job is to
     produce a valid config, not to explain why the previous one
     wasn't. */
    private void load_existing_or_defaults()
    {
        String stored = TaskerAutomationManager.load(this);
        TaskerAutomationConfig config = null;
        if (stored != null)
        {
            try { config = TaskerAutomationConfig.parse(stored); }
            catch (Exception e) { config = null; }
        }

        if (config != null)
        {
            _replace_input.setText(display_form(config.replace_trigger));
            _append_input.setText(display_form(config.append_trigger));
            _timeout_input.setText(String.valueOf(config.timeout_ms));
            for (Map.Entry<String, String> e : config.tasks.entrySet())
                add_task_row(display_form(e.getKey()), display_form(e.getValue()));
            for (TaskerAutomationConfig.ExpandPattern p : config.expand_patterns)
                add_pattern_row(p.name == null ? "" : display_form(p.name),
                        display_form(p.prefix), p.regex == null ? "" : display_form(p.regex),
                        display_form(p.suffix), display_form(p.task), p.replace_prefix, p.fire_on_suffix, false);
        }
        else
        {
            _replace_input.setText(TaskerAutomationConfig.DEFAULT_REPLACE_TRIGGER);
            _append_input.setText(TaskerAutomationConfig.DEFAULT_APPEND_TRIGGER);
            _timeout_input.setText(String.valueOf(TaskerAutomationConfig.DEFAULT_TIMEOUT_MS));
            add_task_row("runtask1", "Task 1");
            add_pattern_row(TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_NAME,
                    TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_PREFIX, "",
                    TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_SUFFIX, TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_TASK,
                    Boolean.parseBoolean(TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX),
                    Boolean.parseBoolean(TaskerAutomationConfig.DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX), false);
            add_pattern_row(TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_NAME,
                    TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_PREFIX, TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_REGEX,
                    TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_SUFFIX, TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_TASK,
                    Boolean.parseBoolean(TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_REPLACE_PREFIX),
                    Boolean.parseBoolean(TaskerAutomationConfig.DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX), false);
        }

        if (_task_rows.isEmpty())
            add_task_row("", "");
    }

    // ---- Row builders --------------------------------------------------

    private TextView add_field_label(LinearLayout parent, int label_res)
    {
        TextView label = new TextView(this);
        label.setText(label_res);
        label.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        label.setTextSize(11f);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(label, lp);
        return label;
    }

    /** Adds one "label above input" field, stacked, into [parent]. Used
     for the fixed global-settings card (single column, so a label
     next to a field would waste width on longer labels like
     "amck_timeout (ms)"). */
    private EditText add_labeled_field(LinearLayout parent, int label_res, int hint_res, boolean numeric, boolean single_line)
    {
        add_field_label(parent, label_res);
        EditText et = new EditText(this);
        et.setHint(hint_res);
        style_edit_text(et);
        if (numeric)
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (single_line)
            et.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        parent.addView(et, lp);
        return et;
    }

    private TaskRow add_task_row(String keyword, String task_name)
    {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(card_background());
        row.setElevation(dp(1));
        LinearLayout.LayoutParams row_lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row_lp.topMargin = dp(10);

        TextView badge = make_index_badge();
        LinearLayout.LayoutParams badge_lp = new LinearLayout.LayoutParams(dp(22), dp(22));
        badge_lp.setMarginEnd(dp(10));
        row.addView(badge, badge_lp);

        EditText keyword_input = new EditText(this);
        keyword_input.setHint(R.string.tasker_builder_keyword_hint);
        keyword_input.setSingleLine(true);
        style_edit_text(keyword_input);
        keyword_input.setText(keyword);
        LinearLayout.LayoutParams keyword_lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        keyword_lp.setMarginEnd(dp(6));
        row.addView(keyword_input, keyword_lp);

        EditText task_input = new EditText(this);
        task_input.setHint(R.string.tasker_builder_task_name_hint);
        task_input.setSingleLine(true);
        style_edit_text(task_input);
        task_input.setText(task_name);
        LinearLayout.LayoutParams task_lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        task_lp.setMarginEnd(dp(6));
        row.addView(task_input, task_lp);

        ImageButton remove = make_remove_button();
        row.addView(remove, new LinearLayout.LayoutParams(dp(28), dp(28)));

        final TaskRow task_row = new TaskRow(row, keyword_input, task_input, badge);
        _task_rows.add(task_row);
        _tasks_container.addView(row, row_lp);
        refresh_task_indices();

        remove.setOnClickListener(v ->
        {
            if (_task_rows.size() <= 1)
                return;
            _task_rows.remove(task_row);
            _tasks_container.removeView(row);
            refresh_task_indices();
        });

        return task_row;
    }

    private void refresh_task_indices()
    {
        for (int i = 0; i < _task_rows.size(); i++)
            _task_rows.get(i).badge.setText(String.valueOf(i + 1));
    }

    private PatternRow add_pattern_row(String name, String prefix, String regex, String suffix, String task,
                                       boolean replace_prefix, boolean fire_on_suffix, boolean expanded)
    {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(12));
        card.setBackground(card_background());
        card.setElevation(dp(1));
        LinearLayout.LayoutParams card_lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card_lp.topMargin = dp(10);

        // Header: always visible, whether the card is expanded or
        // collapsed - [make_index_badge]'s number, the "name" field
        // (see [make_pattern_name_input] - this IS the "name" input
        // itself, not just a read-only display of it, so it's usable
        // without ever expanding the card), and the delete button.
        // The chevron is purely a visual "tap to expand" affordance -
        // the whole row (everything except the name field and delete
        // button, which need their own touch handling) toggles
        // [body]'s visibility, via [header_row]'s own click listener
        // below, set up once [pattern_row] exists to reference.
        LinearLayout header_row = new LinearLayout(this);
        header_row.setOrientation(LinearLayout.HORIZONTAL);
        header_row.setGravity(Gravity.CENTER_VERTICAL);

        TextView chevron = make_chevron(expanded);
        LinearLayout.LayoutParams chevron_lp = new LinearLayout.LayoutParams(dp(20), dp(20));
        chevron_lp.setMarginEnd(dp(6));
        header_row.addView(chevron, chevron_lp);

        TextView badge = make_index_badge();
        LinearLayout.LayoutParams badge_lp = new LinearLayout.LayoutParams(dp(22), dp(22));
        badge_lp.setMarginEnd(dp(8));
        header_row.addView(badge, badge_lp);

        EditText name_input = make_pattern_name_input(name);
        LinearLayout.LayoutParams name_lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name_lp.setMarginEnd(dp(6));
        header_row.addView(name_input, name_lp);

        ImageButton remove = make_remove_button();
        header_row.addView(remove, new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(header_row);

        // Everything below the header - collapsed (GONE) by default
        // for a pattern loaded from an existing config, expanded for
        // one just added via "+ Add expand pattern" (so there's
        // something to immediately fill in without an extra tap) -
        // see [expanded].
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText prefix_input = add_pattern_field(body, R.string.tasker_builder_pattern_prefix_label,
                R.string.tasker_builder_pattern_prefix_hint, prefix);
        EditText regex_input = add_pattern_field(body, R.string.tasker_builder_pattern_regex_label,
                R.string.tasker_builder_pattern_regex_hint, regex);
        EditText suffix_input = add_pattern_field(body, R.string.tasker_builder_pattern_suffix_label,
                R.string.tasker_builder_pattern_suffix_hint, suffix);
        EditText task_input = add_pattern_field(body, R.string.tasker_builder_pattern_task_label,
                R.string.tasker_builder_pattern_task_hint, task);
        CheckBox replace_prefix_input = add_pattern_checkbox(body,
                R.string.tasker_builder_pattern_replace_prefix_label, replace_prefix);
        CheckBox fire_on_suffix_input = add_pattern_checkbox(body,
                R.string.tasker_builder_pattern_fire_on_suffix_label, fire_on_suffix);

        final PatternRow pattern_row = new PatternRow(card, name_input, body, chevron,
                prefix_input, regex_input, suffix_input, task_input,
                replace_prefix_input, fire_on_suffix_input, badge);
        _pattern_rows.add(pattern_row);
        _patterns_container.addView(card, card_lp);
        refresh_pattern_indices();

        header_row.setOnClickListener(v -> toggle_pattern_expanded(pattern_row));
        remove.setOnClickListener(v -> confirm_and_remove_pattern(pattern_row, card));

        return pattern_row;
    }

    /** Flips one pattern card between expanded and collapsed - see
     [add_pattern_row]'s header/body split. Reads the current state
     directly off [PatternRow.body]'s own visibility rather than
     tracking a separate boolean, since the view IS the state here. */
    private void toggle_pattern_expanded(PatternRow row)
    {
        boolean now_expanded = row.body.getVisibility() != View.VISIBLE;
        row.body.setVisibility(now_expanded ? View.VISIBLE : View.GONE);
        row.chevron.setText(now_expanded ? "\u25be" : "\u25b8");
        row.chevron.setContentDescription(getString(
                now_expanded ? R.string.tasker_builder_collapse_pattern : R.string.tasker_builder_expand_pattern));
    }

    /** Confirms before actually deleting a pattern card - unlike a
     keyword row (which stays a quick, no-confirmation ✕, same as
     before), a pattern card can hold a fair amount of typed-in
     configuration, so an accidental tap here is more costly. Names
     the pattern in the confirmation message when it has one. */
    private void confirm_and_remove_pattern(final PatternRow row, final LinearLayout card)
    {
        String name = text_of(row.name).trim();
        String message = name.isEmpty()
                ? getString(R.string.tasker_builder_delete_pattern_message_unnamed)
                : getString(R.string.tasker_builder_delete_pattern_message_named, name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.tasker_builder_delete_pattern_title)
                .setMessage(message)
                .setPositiveButton(R.string.tasker_builder_delete, (dialog, which) ->
                {
                    _pattern_rows.remove(row);
                    _patterns_container.removeView(card);
                    refresh_pattern_indices();
                })
                .setNegativeButton(R.string.tasker_builder_cancel, null)
                .show();
    }

    /** The small "▸"/"▾" tap-to-expand indicator at the start of a
     pattern card's header - purely visual (see [add_pattern_row]:
     the actual toggle is wired on the whole header row, not this
     view specifically), kept as its own [TextView] so
     [toggle_pattern_expanded] has something to update the glyph and
     content description on. */
    private TextView make_chevron(boolean expanded)
    {
        TextView chevron = new TextView(this);
        chevron.setText(expanded ? "\u25be" : "\u25b8");
        chevron.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        chevron.setTextSize(16f);
        chevron.setGravity(Gravity.CENTER);
        chevron.setContentDescription(getString(
                expanded ? R.string.tasker_builder_collapse_pattern : R.string.tasker_builder_expand_pattern));
        return chevron;
    }

    /** The pattern card's "name" field, living directly in the header
     (see [add_pattern_row]) rather than down in the collapsible body
     like every other field - it's the one field that's actually
     useful to see/edit without ever expanding the card, since its
     whole purpose is telling collapsed patterns apart at a glance.
     Styled to read as a title (bold, no input-box background) rather
     than another boxed text field, unlike [add_pattern_field]'s
     inputs and unlike [style_edit_text]. */
    private EditText make_pattern_name_input(String initial_value)
    {
        EditText et = new EditText(this);
        et.setHint(R.string.tasker_builder_pattern_name_hint);
        et.setSingleLine(true);
        et.setText(initial_value);
        et.setTextColor(Color.rgb(23, 32, 42));
        et.setHintTextColor(Color.rgb(152, 162, 171));
        et.setTextSize(14f);
        et.setTypeface(null, android.graphics.Typeface.BOLD);
        et.setBackground(null);
        et.setPadding(dp(4), dp(2), dp(4), dp(2));
        return et;
    }

    private EditText add_pattern_field(LinearLayout parent, int label_res, int hint_res, String initial_value)
    {
        add_field_label(parent, label_res);
        EditText et = new EditText(this);
        et.setHint(hint_res);
        et.setSingleLine(true);
        style_edit_text(et);
        et.setText(initial_value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        parent.addView(et, lp);
        return et;
    }

    /** Adds one boolean field (a checkbox with its label as the
     checkbox's own text, rather than a separate [add_field_label]
     above it like the text fields get - a checkbox's label reads
     naturally right next to the box, unlike a text field's). Used for
     "replace_prefix"/"fire_on_suffix" on an "amck_patterns" card. */
    private CheckBox add_pattern_checkbox(LinearLayout parent, int label_res, boolean initial_checked)
    {
        CheckBox cb = new CheckBox(this);
        cb.setText(label_res);
        cb.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        cb.setTextSize(12f);
        cb.setChecked(initial_checked);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(cb, lp);
        return cb;
    }

    private void refresh_pattern_indices()
    {
        for (int i = 0; i < _pattern_rows.size(); i++)
            _pattern_rows.get(i).badge.setText(String.valueOf(i + 1));
    }

    /** A small circular, primary-color-filled number badge - the
     "island" marker for one dynamically-added row/card, shared by
     [add_task_row] and [add_pattern_row] (see [refresh_task_indices] /
     [refresh_pattern_indices], which just update its text as rows are
     added/removed rather than rebuilding it). */
    private TextView make_index_badge()
    {
        TextView badge = new TextView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(this, R.color.settings_primary));
        badge.setBackground(bg);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        return badge;
    }

    /** Rounded, bordered white "island" background (res/drawable/
     bg_settings_card.xml) for one dynamically-added row/card, so it
     reads as clearly separate from @color/settings_background AND
     from its neighbors - elevation alone wasn't enough contrast for
     that on most screens. A fresh Drawable instance per call (not a
     cached/shared one), since sharing one Drawable object as the
     background of several Views at once is unsafe. */
    private android.graphics.drawable.Drawable card_background()
    {
        return ContextCompat.getDrawable(this, R.drawable.bg_settings_card);
    }

    private ImageButton make_remove_button()
    {
        ImageButton remove = new ImageButton(this);
        remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove.setBackgroundColor(Color.TRANSPARENT);
        remove.setColorFilter(Color.rgb(180, 45, 45));
        remove.setScaleType(ImageView.ScaleType.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setMinimumWidth(0);
        remove.setMinimumHeight(0);
        remove.setContentDescription(getString(R.string.tasker_builder_remove_row));
        return remove;
    }

    // ---- Save / serialize -----------------------------------------------

    private void save()
    {
        List<String> errors = new ArrayList<>();
        String json = build_json(errors);

        if (!errors.isEmpty())
        {
            show_error(android.text.TextUtils.join("\n", errors));
            return;
        }

        try
        {
            TaskerAutomationConfig.parse(json);
        }
        catch (KeymapJsonUtils.ParseError e)
        {
            show_error(e.getMessage());
            return;
        }
        catch (Exception e)
        {
            show_error("Invalid config: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            return;
        }

        TaskerAutomationManager.save(this, json);
        TaskerTriggerEngine.get().reload(this);
        Toast.makeText(this, R.string.tasker_builder_saved_toast, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void show_error(String message)
    {
        _error_text.setText(message);
        _error_text.setVisibility(View.VISIBLE);
    }

    /** Builds the same JSON shape [TaskerAutomationConfig.parse]
     expects out of the current form state. Completely blank rows
     (added by "+" but never filled in) are silently dropped rather
     than emitted as, say, an empty-keyword task entry - only rows the
     user actually put something into are included, and a row that's
     only PARTLY filled in is still included as-is so
     [TaskerAutomationConfig.parse]'s own error messages (e.g. "needs
     a non-empty suffix") point at the real problem instead of this
     method silently discarding it. The one thing [parse] itself
     doesn't check is a task row with just one of its two fields
     filled in (a keyword with no task name, or vice versa) - that's
     valid JSON either way, so it's flagged here instead, into
     [errors], before [json] is even handed to [parse]. */
    private String build_json(List<String> errors)
    {
        List<String> lines = new ArrayList<>();
        lines.add(json_line(TaskerAutomationConfig.KEY_REPLACE_TRIGGER, text_of(_replace_input)));
        lines.add(json_line(TaskerAutomationConfig.KEY_APPEND_TRIGGER, text_of(_append_input)));
        lines.add(json_line(TaskerAutomationConfig.KEY_TIMEOUT_MS, text_of(_timeout_input)));

        for (int i = 0; i < _task_rows.size(); i++)
        {
            TaskRow row = _task_rows.get(i);
            String keyword = text_of(row.keyword);
            String task_name = text_of(row.task_name);
            if (keyword.isEmpty() && task_name.isEmpty())
                continue;
            if (keyword.isEmpty() || task_name.isEmpty())
            {
                errors.add("Keyword row " + (i + 1) + ": fill in both the keyword and the Tasker task name, or remove the row");
                continue;
            }
            lines.add(json_line(keyword, task_name));
        }

        StringBuilder patterns = new StringBuilder();
        patterns.append("\"").append(TaskerAutomationConfig.KEY_EXPAND_PATTERNS).append("\": [");
        List<String> pattern_objects = new ArrayList<>();
        for (int i = 0; i < _pattern_rows.size(); i++)
        {
            PatternRow row = _pattern_rows.get(i);
            String name = text_of(row.name);
            String prefix = text_of(row.prefix);
            String regex = text_of(row.regex);
            String suffix = text_of(row.suffix);
            String task = text_of(row.task);
            if (name.isEmpty() && prefix.isEmpty() && regex.isEmpty() && suffix.isEmpty() && task.isEmpty())
                continue;

            StringBuilder obj = new StringBuilder("    {\n");
            if (!name.isEmpty())
                obj.append("      ").append(json_line("name", name)).append(",\n");
            obj.append("      ").append(json_line("prefix", prefix)).append(",\n");
            if (!regex.isEmpty())
                obj.append("      ").append(json_line(TaskerAutomationConfig.KEY_EXPAND_PATTERN_REGEX, regex)).append(",\n");
            obj.append("      ").append(json_line("suffix", suffix)).append(",\n");
            obj.append("      ").append(json_line("replace_prefix", String.valueOf(row.replace_prefix.isChecked()))).append(",\n");
            obj.append("      ").append(json_line("fire_on_suffix", String.valueOf(row.fire_on_suffix.isChecked()))).append(",\n");
            obj.append("      ").append(json_line("task", task)).append("\n");
            obj.append("    }");
            pattern_objects.add(obj.toString());
        }
        if (pattern_objects.isEmpty())
        {
            patterns.append("]");
        }
        else
        {
            patterns.append("\n");
            for (int i = 0; i < pattern_objects.size(); i++)
            {
                patterns.append(pattern_objects.get(i));
                if (i < pattern_objects.size() - 1)
                    patterns.append(",");
                patterns.append("\n");
            }
            patterns.append("  ]");
        }
        lines.add(patterns.toString());

        StringBuilder b = new StringBuilder("{\n");
        for (int i = 0; i < lines.size(); i++)
        {
            b.append("  ").append(lines.get(i));
            if (i < lines.size() - 1)
                b.append(",");
            b.append("\n");
        }
        b.append("}");
        return b.toString();
    }

    /** Never trims - a leading/trailing space the user actually typed
     (e.g. a prefix or suffix that's just " ") is content, not
     accidental whitespace, so it must survive exactly as typed. Only
     an untouched EditText (never focused/typed into) returns "",
     which is still what the "was this row left blank" checks in
     [build_json] rely on. */
    /** The exact inverse of [escape_json_string], for the one other
     direction text flows across that boundary: pre-filling a field
     from an already-PARSED config value (i.e. [TaskerAutomationConfig]
     - real characters, e.g. an actual newline character for a "\n"
     suffix, not the 2 visible characters "\" + "n"). Without this,
     opening the builder on a config saved with `"suffix": "\n"` would
     silently show the field as containing a real (invisible, or
     line-breaking) newline character instead of the visible text
     "\n" you'd have typed to produce it - and, per
     [escape_json_string]'s own contract of writing backslashes
     through unchanged, saving that back out unedited would need to
     reproduce "\n", not whatever a stray real newline character
     round-trips to. So each real control character this method
     might see is turned back into the same visible 2-character
     escape text typing it into the raw JSON editor would use: a real
     newline -> "\" + "n", etc. A literal backslash character in the
     decoded value (from the saved JSON having had "\\") is left
     exactly as one backslash character, not doubled back to 2 -
     that's already what a single backslash key-press looks like, and
     is what [escape_json_string] would write straight back out
     unchanged if left untouched. */
    private static String display_form(String s)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private static String text_of(EditText et)
    {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private static String json_line(String key, String value)
    {
        return "\"" + escape_json_string(key) + "\": \"" + escape_json_string(value) + "\"";
    }

    /** Encodes [s] into a JSON string literal's contents WITHOUT
     interpreting or escaping any backslash the user typed - only the
     2 things that would otherwise break the JSON syntax itself are
     touched: a literal '"' becomes \" (or it would end the string
     early), and a literal control character (an actual raw newline/
     tab/etc, e.g. from a paste) becomes its escape, since raw control
     characters aren't legal JSON. A backslash the user typed (e.g.
     the 2 characters "\" + "n" to mean "the suffix is a newline", or
     "\" + "d" for a regex digit class) is written through completely
     unchanged - specifically NOT doubled to "\\n" / "\\d". This is
     what keeps typing "\n" in the suffix field equivalent to typing
     it directly into the raw JSON editor: the saved file gets the
     literal 2-character JSON escape "\n", which the parser turns
     into an actual newline the same way either way - doubling it to
     "\\n" here would instead save a literal backslash+n as the
     suffix's actual content, which is wrong. If what's typed isn't a
     JSON escape sequence the target field's parser recognizes, that
     surfaces to the user as a save-time error from
     [TaskerAutomationConfig.parse] (via [KeymapJsonUtils.ParseError])
     - same as it would if you'd typed it directly into the raw JSON
     editor - rather than being silently "fixed" by this method. */
    private static String escape_json_string(String s)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"': b.append("\\\""); break;
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

    // ---- Styling helpers --------------------------------------------------

    private void style_edit_text(final EditText edit_text)
    {
        final int normal_border_color = Color.rgb(208, 213, 221);
        final int focused_border_color = ContextCompat.getColor(this, R.color.settings_primary);
        final int normal_background_color = Color.WHITE;
        final int focused_background_color = Color.rgb(248, 251, 255);

        edit_text.setTextColor(Color.rgb(23, 32, 42));
        edit_text.setHintTextColor(Color.rgb(152, 162, 171));
        edit_text.setTextSize(13f);
        edit_text.setPadding(dp(10), dp(8), dp(10), dp(8));
        edit_text.setElevation(dp(1));

        GradientDrawable normal_bg = new GradientDrawable();
        normal_bg.setShape(GradientDrawable.RECTANGLE);
        normal_bg.setColor(normal_background_color);
        normal_bg.setCornerRadius(dp(8));
        normal_bg.setStroke(dp(1), normal_border_color);
        edit_text.setBackground(normal_bg);

        edit_text.setOnFocusChangeListener((view, has_focus) ->
        {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(8));
            if (has_focus)
            {
                bg.setColor(focused_background_color);
                bg.setStroke(dp(2), focused_border_color);
            }
            else
            {
                bg.setColor(normal_background_color);
                bg.setStroke(dp(1), normal_border_color);
            }
            view.setBackground(bg);
        });
    }

    private int dp(int value)
    {
        return (int)(value * getResources().getDisplayMetrics().density);
    }
}
