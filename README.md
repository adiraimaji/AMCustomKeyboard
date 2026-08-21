# ⌨️ AMCustomKeyboard

### 🤖 An Android Keyboard (Input Method) App

**A powerful fork of Unexpected Keyboard (built from its downloaded source code) with a live transliteration engine, a guided Keymap Builder, independent key labels, advanced layout customization, and toggle-style keymaps.**

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://github.com/adiraimaji/AMCustomKeyboard)
[![License](https://img.shields.io/badge/license-GPLv3-blue.svg)](https://github.com/adiraimaji/AMCustomKeyboard/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/adiraimaji/AMCustomKeyboard?label=release&color=success)](../../releases/latest)
[![Downloads](https://img.shields.io/github/downloads/adiraimaji/AMCustomKeyboard/total.svg?color=orange)](../../releases)

### 📥 [**Download the latest APK**](../../releases/latest)

Grab the newest build from the **[Releases page](../../releases)** — no Play Store required.
Just download the `.apk`, allow installs from unknown sources, and enable it as your system keyboard in **Settings → System → Languages & Input**.

---

## 📱 Screenshots

<div align="center">
<table>
<tr>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_1.jpeg" width="220"/></td>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_2.jpeg" width="220"/></td>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_3.jpeg" width="220"/></td>
</tr>
<tr>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_4.jpeg" width="220"/></td>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_5.jpeg" width="220"/></td>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_6.jpeg" width="220"/></td>
</tr>
<tr>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_7.jpeg" width="220"/></td>
<td><img src="https://raw.githubusercontent.com/adiraimaji/AMCustomKeyboard/main/Screenshots/Screenshot_8.jpeg" width="220"/></td>
<td></td>
</tr>
</table>
</div>

---

## Table of Contents

1. [Overview](#overview)
2. [Credits](#credits)
3. [Core Concept: Display vs. Output vs. Final Result](#core-concept-display-vs-output-vs-final-result)
4. [Independent Labels](#independent-labels)
5. [Keymaps (Transliteration Engine)](#keymaps-transliteration-engine)
6. [Keymap JSON Format](#keymap-json-format)
7. [Toggle-Style Keymaps](#toggle-style-keymaps)
8. [Keymap Behavior Rules](#keymap-behavior-rules)
9. [The `swipekeymap` Attribute](#the-swipekeymap-attribute)
10. [Shift Output Rules](#shift-output-rules)
11. [Label Attribute Rules](#label-attribute-rules)
12. [Space Bar Layout Indicator](#space-bar-layout-indicator)
13. [Settings: Layout, Keymap, and Default Layout](#settings-layout-keymap-and-default-layout)
14. [The Layout Dialog &amp; Keyboard Attributes Card](#the-layout-dialog--keyboard-attributes-card)
15. [The Keymap Dialog](#the-keymap-dialog)
16. [Keymap Builder](#keymap-builder)
17. [Referential Integrity: Rename &amp; Delete Safety](#referential-integrity-rename--delete-safety)
18. [Dictionary Suggestions with Keymaps](#dictionary-suggestions-with-keymaps)
19. [Tasker Automation](#tasker-automation)
20. [Engine Architecture](#engine-architecture)
21. [Full XML Example](#full-xml-example)
22. [Quick-Reference Rules Summary](#quick-reference-rules-summary)
23. [Checking for Updates](#checking-for-updates)

---

## Overview

AMCustomKeyboard is an **Android input method (IME) app** — it runs as your system keyboard, just like Gboard or SwiftKey, and can be enabled from **Settings → System → Languages & Input → Virtual Keyboard**.

It extends the standard keyboard model with three independent layers:

- **What the key shows** (label)
- **What the key sends when tapped or swiped** (output)
- **What that output becomes after transliteration** (final result, via the Keymap Engine)

Because these three layers are decoupled, a single key can display an icon, output a Latin letter, and ultimately produce a completely different script — all without conflicting with each other.

On top of the transliteration engine itself, AMCustomKeyboard ships:

- A full in-app **Keymap Builder** (guided, no-XML editor for keymaps)
- **Keyboard Attributes** editing in the Layout dialog
- **Grouped-only keymap JSON** (output → keys)
- **Toggle-style keymaps** for reversible mappings (e.g. `a ↔ அ`, `su ↔ சு`)
- A **Default layout** setting to choose which layout loads when the keyboard opens
- **Tasker Automation** — typed triggers that hand text off to a Tasker task and insert its result back into the field, with no XML/scripting beyond a small JSON config

---

## Credits

AMCustomKeyboard is built upon the excellent [**Unexpected Keyboard**](https://github.com/Julow/Unexpected-Keyboard) project. This fork preserves the original gesture-driven keyboard while extending it with several major features: **Keymaps**, the **Keymap Builder**, **Independent Labels**, **Keyboard Attributes** editing, **Toggle-style keymaps**, and **Default layout selection**.

---

## Core Concept: Display vs. Output vs. Final Result

```text
Display        Output        Final Result

🍎   ───────►   a   ───────►   அ
               │
               └── Keymap Engine
```

The keyboard can **show one thing**, **send another**, and **finally convert it into something completely different**. Understanding this three-layer separation is the key to understanding everything else in this document.

---

## Independent Labels

Labels control only what is *displayed* on a key — they never affect what the key actually sends.

```xml
<key c="a" cL="🍎"/>
```

| Keyboard Shows | Keyboard Sends |
| -------------- | -------------- |
| 🍎             | a              |

Shift labels behave the same way, independently of their lowercase counterparts:

```xml
<key c="a" cL="🍎" C="@" CL="✈️"/>
```

**Rule:** Labels never change output. Output attributes and label attributes are always evaluated separately.

---

## Keymaps (Transliteration Engine)

A keymap is a JSON mapping used to transliterate typed output into another script, using **longest-match, live replacement** as the user types.

Example:

```json
{
  "keymap_name": "Tamil",
  "a": "அ",
  "aa": "ஆ",
  "ka": "க"
}
```

For example, typing the sequence:

```text
k → ka
```

is automatically converted to:

```text
க
```

The engine always resolves to the **longest valid mapping** available at each step, not just the most recent character — waiting for more input before finalizing a shorter match whenever a longer key could still complete it.

A keymap is a **named, reusable resource** stored independently of any single layout. It's linked to a layout by referencing its name in that layout's `keymap` attribute:

```xml
<keyboard name="QWERTY (US)" script="latin" keymap="Tamil">
```

Any number of layouts can reference the same keymap, and a keymap can be created, edited, renamed, or removed at any time from **Settings → Layout, Keymap, and Default Layout**, independently of which layouts use it.

---

## Keymap JSON Format

AMCustomKeyboard uses a single keymap JSON structure:

```json
{
  "keymap_name": "<name>",
  "<output1>": "<keys1>",
  "<output2>": "<keys2>",
  ...
}
```

- Each JSON key is an **output string** (what appears on screen after transliteration).
- Each value is a **comma-separated list of key sequences** that should produce that output.
- The `keymap_name` field is mandatory and must be non-empty.

All keymaps are stored and interpreted in this grouped **output → keys** format.

---

## Toggle-Style Keymaps

AMCustomKeyboard supports **toggle-style behavior** for keymaps, allowing you to type back and forth between related forms without using backspace.

### How it works

- The engine tracks the **current word** as a sequence of committed characters.
- When you type additional characters, it tries to:
  - Extend the current word into a **longer matching output**, or
  - If no longer match exists, **toggle back** to a shorter valid mapping that fits the new input.
- This allows **reversible mappings** like:
  - `a` → `அ`typing `aa` → `ஆ`typing `a` again → back to `அ`
  - `su` → `சு`
    typing `u` → `சூ`
    typing `u` again → back to `சு`

### Example keymap

```json
{
  "keymap_name": "TamilToggle",
  "அ": "a",
  "ஆ": "aa,A",
  "உ": "u",
  "ஊ": "u,U",
  "ச்": "s",
  "ச": "sa",
  "சு": "su",
  "சூ": "suu,sU"
}
```

Behavior examples:

- Type `a` → `அ`Type another `a` → `ஆ`Type another `a` → back to `அ` (cycle continues as long as mappings allow).
- Type `s` → `ச்`
  Type `a` → `ச`
  Type `u` → `சு`
  Type `u` → `சூ`
  Type `u` again → back to `சு`
  Type `U` while on `சு` → stays `சு` (no valid longer mapping for `sUu`), but `sU` alone gives `சூ`.

This design helps correct mistyped sequences immediately without pressing backspace.

---

## Keymap Behavior Rules

These rules define exactly *when* and *how* the keymap engine is applied.

### Rule 1 — Only primary tap output is transliterated

When a layout specifies a `keymap`, **only the primary tap output** (`c` for lowercase, `C` for shift/uppercase) is passed through the keymap engine by default.

### Rule 2 — Swipe outputs bypass the keymap unless `swipekeymap` is enabled

**Swipe outputs are never transliterated by default**, regardless of whether the layout uses a keymap. This applies to all swipe directions (`nw`, `n`, `ne`, `e`, `se`, `s`, `sw`, `w`) and their uppercase variants. This behavior can be changed per-layout — see [The `swipekeymap` Attribute](#the-swipekeymap-attribute).

### Worked Example

Given this layout:

```xml
<keyboard keymap="greek">
  <row>
    <key
      c="a"
      C="A"
      e="b"
      E="B"
      ne="1"/>
  </row>
</keyboard>
```

And this keymap:

```json
{
  "keymap_name": "greek",
  "a": "α",
  "A": "Α",
  "b": "β"
}
```

The resulting behavior (with `swipekeymap` **not** set) is:

| Action             | XML Attribute Used | Keymap Applied? | Final Output |
| ------------------ | ------------------ | :-------------: | :----------: |
| Tap                | `c="a"`          |     ✅ Yes     |    `α`    |
| Shift + Tap        | `C="A"`          |     ✅ Yes     |    `Α`    |
| Swipe East         | `e="b"`          |      ❌ No      |    `b`    |
| Shift + Swipe East | `E="B"`          |      ❌ No      |    `B`    |
| Swipe North-East   | `ne="1"`         |      ❌ No      |    `1`    |

> **Important:** By default, swipe keys always send the exact value defined in the layout, unconditionally bypassing the keymap engine. This is intentional, so gesture shortcuts and symbol swipes stay predictable even on a transliterating layout — unless you explicitly opt in with `swipekeymap="true"`.

---

## The `swipekeymap` Attribute

By default, only center-tap output (`c` / `C`) is transliterated — swipe output always bypasses the keymap, even when one is set. The `swipekeymap` attribute lets a layout opt into transliterating swipe output too:

```xml
<keyboard name="QWERTY (US)" script="latin" keymap="tamil" swipekeymap="true">
```

| `keymap` attribute | `swipekeymap` attribute | Behavior                                                                |
| -------------------- | ------------------------- | ----------------------------------------------------------------------- |
| Absent               | *(any)*                 | No transliteration at all —`swipekeymap` has no effect.              |
| Present              | Absent or`"false"`      | Only center taps (`c` / `C`) are transliterated. **Default.** |
| Present              | `"true"`                | Center taps**and** all 8 directional swipes are transliterated.   |

`swipekeymap` is **inert** on any layout that has no `keymap` attribute — it does nothing on its own.

This can be set directly in XML, or via the **Swipekeymap** checkbox in the [Keyboard Attributes card](#the-layout-dialog--keyboard-attributes-card) — the checkbox is automatically disabled whenever no keymap is selected, since it would have no effect.

---

## Shift Output Rules

Valid shift/uppercase output attributes: `C`, `NW`, `N`, `NE`, `E`, `SE`, `S`, `SW`, `W`

**Rule:** If an uppercase output exists, its corresponding lowercase output **must** be defined first.

✅ **Correct:**

```xml
<key c="a" C="@"/>
```

❌ **Incorrect** (uppercase defined without a lowercase base):

```xml
<key C="@"/>
```

---

## Label Attribute Rules

- If a label attribute is omitted, the key **displays its output value** instead.
- Labels are purely visual and never affect what is typed.
- Every output attribute (`c`, `C`, `n`, `N`, etc.) has a matching label attribute (`cL`, `CL`, `nL`, `NL`, etc.).

---

## Space Bar Layout Indicator

The space bar displays the **name of the currently active layout** (its `name="..."` attribute) instead of a plain space glyph, so it's always clear which layout — and, indirectly, which keymap — is active without needing to open Settings. This works automatically for both built-in layouts and custom layouts with a `name` attribute, whether or not `bottom_row` is auto-generated or the space key is defined manually in a custom row.

---

## Settings: Layout, Keymap, and Default Layout

The **Layout** settings category (now titled **Layout, Keymap, and Default Layout**) shows:

- A list of **Layouts**
- A list of **Keymaps**
- A **Default layout** dropdown

Example UI concept:

```text
Layout 1: QWERTY (US)
Layout 2: My Custom Layout
[+ Add an alternate layout]

Keymap 1: Tamil
Keymap 2: Greek
[+ Add new Keymap JSON]
[+ Keymap Builder]

Default layout:
  [ Last used layout        ▼ ]
```

### Default layout options

- **Last used layout** (default)The keyboard remembers the last layout you used. When you close and reopen the keyboard, it restores that layout.
- **Specific layout (e.g. “QWERTY (US)”)**
  The keyboard always loads that layout whenever it is opened, regardless of what was used last time.

This setting applies globally to the keyboard: when the IME starts (or when the subtype changes), it checks this preference and loads the chosen layout as the initial active layout.

### Other behaviors

- **Layout rows and Keymap rows are always grouped separately** — every Layout row appears before every Keymap row, each with its own independent numbering.
- **"Add an alternate layout"** opens the built-in layout picker (system / named / custom).
- **"Add new Keymap JSON"** opens the [Keymap dialog](#the-keymap-dialog) with a blank starter JSON.
- **"Keymap Builder"** launches the full [guided builder](#keymap-builder).
- Tapping an existing **Keymap N: name** row reopens it for editing/removal.
- Tapping an existing **Layout N: name** row (for a custom layout) reopens the [Layout dialog](#the-layout-dialog--keyboard-attributes-card).
- Any keymap or layout that exists in storage but has no row yet is automatically synced when the Settings screen is opened or resumed.

---

## The Layout Dialog & Keyboard Attributes Card

Editing a custom layout (via "Add an alternate layout → Custom", or tapping an existing custom Layout row) opens a line-numbered XML editor with a **Keyboard Attributes** card above it:

```text
┌─ Keyboard Attributes ──────────────┐
│ Name         [ QWERTY (US)      ]  │
│ Keymap       [ Tamil            ▼]  │
│ Swipekeymap    ☐                   │
└─────────────────────────────────────┘

[line-numbered XML editor]

[Remove layout]        [Cancel]  [OK]
```

- **Name** — a plain text field bound to the layout's `name="..."` attribute.
- **Keymap** — a dropdown listing every keymap currently saved in Settings, plus a **(No keymap)** option. Selecting an entry writes (or removes) the `keymap="..."` attribute on the `<keyboard>` tag automatically.
- **Swipekeymap** — a checkbox bound to the `swipekeymap="true"` attribute. Disabled automatically whenever no keymap is selected.

All three controls are **fully bidirectional** with the raw XML text: picking a keymap from the dropdown rewrites the XML, and manually typing `keymap="..."` (or `name="..."`, or `swipekeymap="..."`) directly into the XML box updates the Name field / dropdown / checkbox to match.

The input box is height-capped and internally scrollable, so very large layout XML never pushes the buttons off-screen.

---

## The Keymap Dialog

Tapping **"Add new Keymap JSON"**, or an existing **Keymap N: name** row, opens a line-numbered JSON editor:

```text
[Keymap]

[line-numbered JSON editor]

⚠ error text (if any)          [Keymap Builder]

[Remove Keymap]        [Cancel]  [OK]
```

- **Inline validation** — problems are shown as a small red warning line just above the button row:
  - Missing or empty `keymap_name`.
  - **Duplicate keys** — the JSON parser used for validation preserves every key occurrence, so duplicates are always caught and reported.
- **OK is blocked** while any error is present.
- **"Keymap Builder"** carries the dialog's *current, possibly-invalid* text directly into the guided builder.
- **Overwrite protection** — saving under a name that already belongs to a *different* stored keymap prompts for confirmation before replacing it.
- The input box is height-capped and scrollable for large keymaps.

Use this dialog to **copy the raw JSON** of a keymap when needed.

---

## Keymap Builder

A dedicated, guided screen for constructing or editing a keymap without hand-writing JSON, opened via the **"Keymap Builder"** button in Settings or from within the [Keymap dialog](#the-keymap-dialog).

### Layout

```text
Keymap Builder

Keymap name         [ ___________ ]

Search  [___________________] ( ) Output  ( ) Keys

    Output                    Keys
 1  [ அ                    ]  [ a        ]  ✕
 2  [ ஆ                    ]  [ aa,A     ]  ✕
 3  [                       ]  [          ]  ✕   ← always-present trailing empty row

┌────────────────────────────────────────────┐
│ [ output ] [ keys ] [+]                     │  ← quick add, fixed above Create
│ instructions text  ☐ Dup only  [⇩]          │  ← Import button
└────────────────────────────────────────────┘

[            Create Keymap            ]
```

### Row behavior

- Row 0 is fixed and non-removable: a **"keymap_name"** label on the left, an editable name field on the right.
- Every mapping row pairs an **Output** field (left, wider, multiline) with a **Keys** field (right, comma-separated).
- Typing into the current last row's Output field automatically appends a fresh empty row below it.
- A comma inside a key itself can be entered as `\,` (backslash-escaped).
- Pasting multiline text into any row's Output field prompts: **split into one new row per line** or **paste as a single multiline field**.

### Quick Add

A fixed section directly above **Create Keymap** lets you fill the current trailing empty row without scrolling: enter an output and comma-separated keys, tap **+**, and the row is filled (auto-spawning the next empty row).

### Duplicate Detection & "Dup only" Filter

- Every keystroke in any Keys field re-scans **all** rows for keys used more than once.
- The **"Dup only"** checkbox is enabled only while duplicates exist, and automatically disables when all conflicts are resolved.
- Checking it **hides** every row without a duplicate — row numbers never shift.
- The filter updates **live** as you edit.
- Attempting **Create Keymap** while duplicates exist shows a dialog listing every duplicated key and blocks saving until resolved.

### Search / Filter (Exact Match, No Trimming)

- A search box next to a two-way **Output / Keys** choice filters the visible rows.
- Accepts **comma-separated terms**; a row matches if it contains **any** one of the specified terms (OR), checked only against the selected field (Output or Keys).
- **Search terms are matched exactly as entered**:
  - Leading and trailing spaces are significant and are **not** trimmed.
  - `"n"`, `" n"`, and `"n "` are different search terms.
- For example, searching `a, n` matches rows that contain:
  - `a`, or
  - the exact term `" n"` (including the leading space),
    but **not** rows that only contain `"n"` without the leading space.
- This exact-match, no-trim behavior applies to both **Keys** and **Output** searches.
- Combines with **"Dup only"** using AND — with both active, only rows that are both a duplicate *and* match the search are shown.
- The current trailing empty row is always exempt from every filter.

### Raw JSON Import

- The **Import** button (⇩) lets you paste or type grouped-format JSON directly into the builder.
- Imported JSON replaces the builder's current rows.
- The JSON output keys become the builder's Output fields, while each comma-separated value becomes the corresponding Keys field.
- The same escaping convention is used throughout the builder (`\,` for a literal comma inside a key).

### Editing an Existing Keymap

Opening the builder for an already-saved keymap pre-fills every field:

- The name field is set from the stored `keymap_name`.
- Keys sharing the same output value are automatically **grouped back into a single row**.
- If the name is changed before saving, the old stored entry is removed and every layout that referenced the old name is automatically updated to the new one — see [Referential Integrity](#referential-integrity-rename--delete-safety).

### Saving

- **Create Keymap** validates for duplicate keys first, then checks whether the name already belongs to a different stored keymap — if so, a confirmation dialog is shown before overwriting.
- On save, the generated JSON uses the **grouped output → keys format**, with one output per JSON key and its input sequences grouped in the corresponding comma-separated value.

---

## Referential Integrity: Rename & Delete Safety

Keymaps are referenced by name from layouts, so renaming or deleting one is kept in sync everywhere it's used, automatically:

- **Live effect, no caching** — the transliteration engine always re-reads a keymap fresh from storage the moment a layout referencing it becomes active.
- **Renaming** (via the Keymap Builder) automatically rewrites the `keymap="..."` attribute on every stored custom layout that referenced the old name.
- **Deleting** a keymap that's still referenced by one or more layouts shows a confirmation dialog naming how many layouts use it. Confirming the deletion both removes the keymap **and** strips the `keymap`/`swipekeymap` attributes from every layout that referenced it.
- Settings always resyncs its Keymap list against what's actually in storage whenever the screen is opened or resumed.

---

## Dictionary Suggestions with Keymaps

Word suggestions stay correctly in sync with keymap transliteration:

- The "currently typed word" tracker is driven by the keymap engine's own committed output (e.g. the Tamil text actually on screen), not by the raw Latin keys typed.
- Suggestion queries are always made against the same script the dictionary itself is indexed in.
- Combining marks (such as Tamil pulli/virama and vowel signs) are treated as part of the current word rather than as word separators, so suggestions continue to work correctly across a full multi-character syllable.
- Toggle-style behavior (e.g. `a ↔ அ`, `su ↔ சு`) works seamlessly with suggestions, since the engine's view of the current word is always the final transliterated text.

---

## Tasker Automation

AMCustomKeyboard can hand typed text off to [Tasker](https://tasker.joaoapps.com/) and insert the task's returned result directly into the field you're typing in — no scripting beyond a small JSON config. It's configured from **Settings → Tasker Automation**, which contains:

- **Automation Builder** — a guided form for the same config (see [Automation Builder](#automation-builder) below) — fixed fields for the 3 `amck_` settings, and dynamically-growing rows for keywords and expand patterns. No JSON syntax to get right.
- **Tasker Automation** — opens the JSON config editor (same line-numbered dialog used for keymaps and layouts), with inline validation and a live task count in its summary (e.g. "3 task(s) configured" / "Not configured").
- **How to use Tasker Automation** — opens an in-app guide (`TaskerAutomationGuideActivity`) walking through triggers, expand patterns, the config format, and how to build the matching Tasker task.
- **Get AM Tasker Plugins** — links to the companion [AMTaskerPluginsReleases](https://github.com/adiraimaji/AMTaskerPluginsReleases) repo, with Tasker plugins (send/return text, request IDs, and more) for building the Tasker side of a task.

Because this talks to the Tasker app, it requires the `net.dinglisch.android.tasker.PERMISSION_RUN_TASKS` permission (declared in the manifest) and Tasker's own **"Allow External Access"** setting.

### Config JSON format

There is a single, app-wide config (not a list, unlike layouts/keymaps):

```json
{
  "amck_replace": "##",
  "amck_append": "@@",
  "amck_timeout": "15000",
  "runtask1": "Task 1",
  "runtask2": "Task 2",
  "amck_patterns": [
    { "prefix": "..", "suffix": " ", "task": "Expand Task 1" },
    { "prefix": "==", "regex": "\d+.+\d", "suffix": "\n", "task": "doMath" }
  ]
}
```

| Key               | Required? | Meaning                                                                                                                                 |
| ----------------- | :-------: | --------------------------------------------------------------------------------------------------------------------------------------- |
| `amck_replace`  | Optional | Trigger prefix for**replace-whole-field** commands. Default `"##"`.                                                             |
| `amck_append`   | Optional | Trigger prefix for**replace-trigger-only** commands. Default `"@@"`.                                                            |
| `amck_timeout`  | Optional | Milliseconds to wait for a task's result before giving up. Default`"15000"`, must be between 1000 and 120000.                         |
| any other key     |    —    | A**keyword** mapped to the exact **Tasker task name** to run when that keyword follows a trigger. At least one is required. |
| `amck_patterns` | Optional | Array of open-ended**expand patterns** (see below). Auto-filled with one example entry if omitted or empty.                       |

`amck_replace` and `amck_append` must be different from each other, and every keyword must be unique. If any of `amck_replace`, `amck_append`, `amck_timeout`, or `amck_patterns` is left out, the app fills in its default and re-saves the config so the stored JSON always matches what's actually in effect — and the config dialog always shows you all of these keys up front (rather than requiring you to know a default exists to override it).

### Triggers: replace vs. append

Given a task keyword `runtask1` mapped to Tasker task `"Task 1"`:

- Typing **`##runtask1`** (the replace trigger + keyword) sends the **entire current field's text** to `"Task 1"` and, on completion, replaces the **whole field** with the task's returned output.
- Typing **`@@runtask1`** (the append trigger + keyword) runs the same task, but replaces only the **typed `@@runtask1` text itself** with the output, leaving the rest of the field untouched.

### Expand patterns

Each entry in `amck_patterns` describes an open-ended expander, independent of the fixed keyword list above: typing `[prefix]`, then any non-empty text, then `[suffix]` (e.g. `..5+1 ` for prefix `".."` / suffix `" "`) runs `[task]` with that in-between text as `%keyword`, and replaces just that `prefix+content+suffix` span with the result — the same field-editing behavior as an `amck_append` trigger, never the whole field.

An entry can optionally add `"regex"` to also constrain *what* the in-between content is allowed to look like. When present, the content must match `regex` **in full** (as if wrapped in `^...$`) — a match somewhere inside it isn't enough. If it doesn't fully match, this entry simply doesn't fire: nothing is deleted, no task runs, and the check just runs again on the next keystroke, so the user can keep typing until it does match (or gives up). For example:

```json
{ "prefix": "==", "regex": "\d+.+\d", "suffix": "\n", "task": "doMath" }
```

- `==59+3` + Enter → content is `59+3`, which fully matches `\d+.+\d` (digit(s), then anything, then a digit) → fires `doMath`.
- `==1+6 ` + Enter → content is `1+6 ` (trailing space) → does **not** fully match `\d+.+\d` (doesn't end in a digit) → does not fire, `==1+6 ` (with the newline) stays in the field untouched.

Content between `[prefix]` and `[suffix]` never contains a newline (a newline always ends the search for that entry), so an ordinary regex like `.+` naturally can't accidentally span multiple lines — you don't need to special-case newlines yourself. This also means a "match any single line of text" pattern like `{ "prefix": "^", "regex": ".+", "suffix": "(" }` works as expected: `^some text here(` fires, but a newline anywhere before the `(` prevents that particular `^` from being used as the start of the match. An entry with no `"regex"` (or an empty one) keeps the original behavior: any non-empty content matches. An invalid regex is rejected with an error when you save the config, the same as any other malformed field.

### One-shot undo

Right after a trigger's replacement text lands, a single backspace with nothing typed in between swaps it back for the original trigger+keyword text (e.g. back to `@@runtask1`) instead of deleting a character. Pressing backspace again afterward behaves like an ordinary backspace. Typing anything else instead of that one immediate backspace forfeits the undo, and the result is left in place as plain text.

### Errors

If a task times out, fails, or Tasker can't be reached at all (not installed, or "Allow External Access" disabled), the field is left as-is and a short error is shown rather than silently swallowing the failed command.

### Automation Builder

`TaskerAutomationBuilderActivity`, opened from **Settings → Tasker Automation → Automation Builder**, is a guided alternative to hand-writing the JSON above. It edits the exact same stored config (either screen's changes show up in the other) as a form instead of text:

- A fixed card for `amck_replace`, `amck_append`, and `amck_timeout` — always exactly these 3 fields.
- A dynamically-growing list of **keyword → Tasker task name** rows (the `"runtask1": "Task 1"` style entries used with the triggers above) — tap **+ Add keyword** for another row, or the ✕ on a row to remove it.
- A dynamically-growing list of **`amck_patterns` cards**, each with its own `prefix` / `regex` (optional) / `suffix` / `task` fields stacked vertically (4 fields side by side wouldn't fit on a phone-width screen) — tap **+ Add expand pattern** for another card.

Every field holds exactly what you type, with no trimming or escaping — a prefix/suffix that's just a space stays a space, and a literal `\n` (backslash + n) you type into a suffix field is saved as the same `\n` a directly-typed `"suffix": "\n"` in the raw JSON editor would be, not doubled into `\\n`. This works both ways: opening the builder on an existing config shows a saved `"\n"` suffix as the visible text `\n`, not an invisible newline, so what you see is always what you'd type to reproduce it.

**Save** serializes the form back to the same JSON shape and validates it with the exact same rules as the raw JSON editor (non-empty prefix/suffix/task, no duplicate keyword, no duplicate prefix+suffix pattern, valid regex, timeout range, distinct replace/append triggers) — any problem is shown inline instead of being saved. A keyword row with only one of its two fields filled in is flagged the same way, before the JSON is even built. Opening the builder pre-fills every field from whatever's currently saved; if nothing is saved yet (or what's saved doesn't currently parse), it falls back to the same built-in defaults the raw JSON editor shows for a blank config.

---

## Engine Architecture

Input flows through the following pipeline, from raw touch to final committed text:

```text
Touch
 │
 ▼
Pointers            → Multi-touch, swipe detection, long press, sliding keys,
 │                    modifier latching/locking, gesture handling
 ▼
KeyModifier         → Shift / Ctrl / Alt / Meta / Fn, compose keys, dead keys,
 │                    Hangul composition, gesture modifiers, selection mode
 ▼
KeyEventHandler     → Distinguishes center-tap output from swipe output
 │                    (isSwipe), gating whether the keymap engine applies
 │                    per the swipekeymap attribute
 ▼
KeymapEngine        → Prefix matching, longest-sequence replacement,
 │                    live conversion, toggle-style cycling,
 │                    word-tracker synchronization,
 │                    always re-reads the active keymap from storage
 ▼
InputConnection
```

**Component responsibilities:**

| Component                           | Responsibility                                                                                                                                                                                 |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Pointers.java`                   | Multi-touch tracking, swipe detection, long press, sliding keys, modifier latching/locking, gesture handling                                                                                   |
| `KeyModifier.java`                | Shift / Ctrl / Alt / Meta / Fn, compose keys, dead keys, Hangul composition, gesture modifiers, selection mode                                                                                 |
| `Keymap.java`                     | Loads a keymap's JSON, stores its mappings, provides lookups                                                                                                                                   |
| `KeymapManager.java`              | Persists all saved keymaps, resolves by name, handles add/remove/rename                                                                                                                        |
| `KeymapEngine.java`               | Prefix matching, longest-sequence replacement, live conversion, toggle-style cycling, word-tracker synchronization, swipe-gating via`swipekeymap`                                            |
| `KeymapXmlAttrUtils.java`         | Reads/writes the`name`, `keymap`, and `swipekeymap` attributes on a layout's raw XML, used by both the Layout dialog's Keyboard Attributes card and rename/delete propagation            |
| `KeymapBuilderActivity.java`      | The guided keymap editor — rows, quick add, duplicate detection, search/filter, raw JSON import                                                                                               |
| `LayoutsPreference.java`          | The Settings list combining Layout and Keymap rows, default layout selection, referential-integrity enforcement on rename/delete                                                               |
| `TaskerAutomationConfig.java`     | Parses, validates, and re-serializes the single Tasker Automation JSON (triggers, timeout, task keywords, expand patterns), auto-filling missing defaults                                      |
| `TaskerTriggerEngine.java`        | Watches typed characters for a configured trigger+keyword or expand pattern, runs the matching Tasker task, and applies its result (replace-field or replace-trigger), including one-shot undo |
| `TaskerAutomationPreference.java` | The Settings row that opens the Tasker Automation JSON config editor and reloads`TaskerTriggerEngine` on save                                                                                |

---

## Full XML Example

```xml
<keyboard name="Example" script="latin" keymap="TamilToggle" swipekeymap="false">
    <row>
        <key c="a" cL="அ"/>
        <key c="k"/>
        <key c="i"/>
    </row>
</keyboard>
```

Corresponding keymap (grouped output → keys, toggle-style):

```json
{
  "keymap_name": "TamilToggle",
  "அ": "a",
  "ஆ": "aa,A",
  "சு": "su",
  "சூ": "suu,sU"
}
```

---

## Quick-Reference Rules Summary

1. **Labels never change output** — they are purely visual.
2. **Keymaps never change rendering directly** — they act only on committed output text.
3. **Lowercase outputs define the base key**; every key needs at least one.
4. **Shift outputs are optional**, but if present, require a corresponding lowercase output.
5. **Label attributes are optional** — omitted labels fall back to displaying the output value.
6. **Only primary tap output (`c` / `C`) passes through the keymap engine by default.**
7. **Swipe outputs bypass the keymap unless `swipekeymap="true"` is set** on the layout.
8. **`swipekeymap` has no effect without a `keymap` attribute present.**
9. **The keymap engine performs longest-match, live replacement** and supports **toggle-style cycling** between related forms.
10. **Keymaps use grouped output → keys JSON**; `keymap_name` is required.
11. **Search in Keymap Builder is exact-match, no trimming**:
    - `"n"`, `" n"`, and `"n "` are different.
    - `"a, n"` matches rows containing `a` or the exact term `" n"`, not `"n"`.
12. **Keymap Builder supports Import** for grouped JSON; raw JSON can be copied from the Keymap Dialog.
13. **Default layout setting**:
    - **Last used layout** (default): restores the last active layout on reopen.
    - **Specific layout**: always loads that layout when the keyboard opens.
14. **A keymap edit or deletion takes effect immediately** — there is no stale cache.
15. **Renaming a keymap updates every layout referencing it automatically.**
16. **Deleting an in-use keymap requires confirmation**, and clears the attribute from every layout that used it.
17. **The space bar always shows the active layout's name**, not a space glyph.

---

## Checking for Updates

**Settings → About → Check for updates** checks [the GitHub releases page](../../releases/latest) for a newer version than the one currently installed, entirely in-app:

- The row's summary always shows the currently installed version (`Current version: x.y.z`).
- Tapping it checks the latest GitHub release. If it's newer, a dialog shows the release notes and a **Download now** button; if you're already up to date (or the check fails, e.g. no connection), a short message says so instead.
- **Download now** downloads the release's APK with the system `DownloadManager` (visible as a normal download notification — nothing opens in a browser) and, once it finishes, launches the system package installer on it directly, all without leaving the app.
- Installing a downloaded APK from outside the Play Store requires the **"install unknown apps"** permission for AMCustomKeyboard (Android 8+). If it isn't granted yet, tapping **Download now** shows a short explanation and a button straight to that settings screen instead of downloading a file you wouldn't yet be allowed to install.

This requires the `android.permission.REQUEST_INSTALL_PACKAGES` permission (declared in the manifest) and network access; no account or API key is needed.

---

## 🙏 Thank You

Special thanks to the creators and contributors of **Unexpected Keyboard** for creating an elegant, open-source keyboard that made AMCustomKeyboard possible.
