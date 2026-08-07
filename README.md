<div align="center">

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
</table>
</div>

---

## Table of Contents

1. [Overview](#overview)
2. [Credits](#credits)
3. [Core Concept: Display vs. Output vs. Final Result](#core-concept-display-vs-output-vs-final-result)
4. [Independent Labels](#independent-labels)
5. [Keymaps (Transliteration Engine)](#keymaps-transliteration-engine)
6. [Keymap Behavior Rules](#keymap-behavior-rules)
7. [The `swipekeymap` Attribute](#the-swipekeymap-attribute)
8. [Key Position Reference](#key-position-reference)
9. [Shift Output Rules](#shift-output-rules)
10. [Label Attribute Rules](#label-attribute-rules)
11. [Space Bar Layout Indicator](#space-bar-layout-indicator)
12. [Settings: Layout and Keymap](#settings-layout-and-keymap)
13. [The Layout Dialog &amp; Keyboard Attributes Card](#the-layout-dialog--keyboard-attributes-card)
14. [The Keymap Dialog](#the-keymap-dialog)
15. [Keymap Builder](#keymap-builder)
16. [Referential Integrity: Rename &amp; Delete Safety](#referential-integrity-rename--delete-safety)
17. [Dictionary Suggestions with Keymaps](#dictionary-suggestions-with-keymaps)
18. [Engine Architecture](#engine-architecture)
19. [Full XML Example](#full-xml-example)
20. [Quick-Reference Rules Summary](#quick-reference-rules-summary)

---

## Overview

AMCustomKeyboard is an **Android input method (IME) app** — it runs as your system keyboard, just like Gboard or SwiftKey, and can be enabled from **Settings → System → Languages & Input → Virtual Keyboard**.

It extends the standard keyboard model with three independent layers:

- **What the key shows** (label)
- **What the key sends when tapped or swiped** (output)
- **What that output becomes after transliteration** (final result, via the Keymap Engine)

Because these three layers are decoupled, a single key can display an icon, output a Latin letter, and ultimately produce a completely different script — all without conflicting with each other.

On top of the transliteration engine itself, AMCustomKeyboard ships a full in-app **Keymap Builder**: a guided, no-XML-required editor for creating and maintaining keymaps, plus a **Keyboard Attributes** card in the Layout editor so `keymap` and `swipekeymap` can be picked from a dropdown instead of hand-typed into XML.

---

## Credits

AMCustomKeyboard is built upon the excellent [**Unexpected Keyboard**](https://github.com/Julow/Unexpected-Keyboard) project. This fork preserves the original gesture-driven keyboard while extending it with several major features: **Keymaps**, the **Keymap Builder**, **Independent Labels**, and **Keyboard Attributes** editing.

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

The engine always resolves to the **longest valid mapping** available at each step, not just the most recent character — waiting for more input before finalizing a shorter match whenever a longer key could still complete it (e.g. `m` stays pending because `ma` and `mau` exist, only committing early if no longer key could ever follow).

A keymap is a **named, reusable resource** stored independently of any single layout. It's linked to a layout by referencing its name in that layout's `keymap` attribute:

```xml
<keyboard name="QWERTY (US)" script="latin" keymap="Tamil">
```

Any number of layouts can reference the same keymap, and a keymap can be created, edited, renamed, or removed at any time from **Settings → Layout and Keymap**, independently of which layouts use it.

---

## Keymap Behavior Rules

These rules define exactly *when* the keymap engine is applied.

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

## Key Position Reference

Each key has up to nine directional zones (center + eight swipe directions), each with its own **output**, **label**, and **shift variant**.

<div style="display:grid;grid-template-columns:auto auto;gap:24px;justify-content:start;">

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

## Settings: Layout and Keymap

The **Layout** settings category (now titled **Layout and Keymap**) shows two grouped lists in a single screen:

```
Layout 1: QWERTY (US)
Layout 2: My Custom Layout
[+ Add an alternate layout]

Keymap 1: Tamil
Keymap 2: Greek
[+ Add new Keymap JSON]
[+ Keymap Builder]
```

- **Layout rows and Keymap rows are always grouped separately** — every Layout row appears before every Keymap row, each with its own independent numbering, regardless of the order they were added in.
- **"Add an alternate layout"** opens the built-in layout picker (system / named / custom) — it no longer offers keymap creation, keeping the two concerns fully separate.
- **"Add new Keymap JSON"** opens the [Keymap dialog](#the-keymap-dialog) with a blank starter JSON.
- **"Keymap Builder"** launches the full [guided builder](#keymap-builder) instead of the raw JSON editor.
- Tapping an existing **Keymap N: name** row reopens it for editing/removal (same dialog, pre-filled).
- Tapping an existing **Layout N: name** row (for a custom layout) reopens the [Layout dialog](#the-layout-dialog--keyboard-attributes-card) for that layout's XML.
- Any keymap that exists in storage but has no row yet (e.g. created before this feature, or via another entry point) is automatically synced in the moment the Settings screen is opened or resumed — so the list always reflects exactly what's actually stored, with no manual refresh needed.

---

## The Layout Dialog & Keyboard Attributes Card

Editing a custom layout (via "Add an alternate layout → Custom", or tapping an existing custom Layout row) opens a line-numbered XML editor with a **Keyboard Attributes** card above it:

```
┌─ Keyboard Attributes ──────────────┐
│ Name         [ QWERTY (US)      ]  │
│ Keymap       [ Tamil         ▾  ]  │
│ Swipekeymap    ☐                   │
└─────────────────────────────────────┘

[line-numbered XML editor]

[Remove layout]        [Cancel]  [OK]
```

- **Name** — a plain text field bound to the layout's `name="..."` attribute.
- **Keymap** — a dropdown listing every keymap currently saved in Settings, plus a **(No keymap)** option. Selecting an entry writes (or removes) the `keymap="..."` attribute on the `<keyboard>` tag automatically.
- **Swipekeymap** — a checkbox bound to the `swipekeymap="true"` attribute. Disabled automatically whenever no keymap is selected, since the attribute would be meaningless.

All three controls are **fully bidirectional** with the raw XML text: picking a keymap from the dropdown rewrites the XML, and manually typing `keymap="..."` (or `name="..."`, or `swipekeymap="..."`) directly into the XML box updates the Name field / dropdown / checkbox to match — with no risk of one overwriting the other mid-edit.

The input box itself is height-capped and internally scrollable, so very large layout XML never pushes the buttons off-screen — the OK / Cancel / Remove row stays fixed and visible regardless of content length.

---

## The Keymap Dialog

Tapping **"Add new Keymap JSON"**, or an existing **Keymap N: name** row, opens a line-numbered JSON editor:

```
[Keymap]

[line-numbered JSON editor]

⚠ error text (if any)          [Keymap Builder]

[Remove Keymap]        [Cancel]  [OK]
```

- **Inline validation** — instead of a floating error balloon that covers the field, problems are shown as a small red warning line just above the button row. This includes:
    - Missing or empty `keymap_name`.
    - **Duplicate keys** — the JSON parser used for validation preserves every key occurrence (unlike a standard JSON parser, which silently keeps only the last value for a repeated key), so a duplicate key is always caught and reported instead of quietly discarding data.
- **OK is blocked** while any error is present — it does not close the dialog or save until the text is valid.
- **"Keymap Builder"** carries the dialog's *current, possibly-invalid* text (including any duplicate keys) directly into the guided builder, so nothing typed is ever lost while resolving a problem — duplicate keys land in their own separate rows automatically, ready to review in the builder's duplicate-solo view.
- **Overwrite protection** — saving under a name that already belongs to a *different* stored keymap prompts for confirmation before replacing it. Saving under its own unchanged name (i.e. editing in place) never prompts.
- The input box is height-capped and scrollable for large keymaps, same as the Layout dialog.

---

## Keymap Builder

A dedicated, guided screen for constructing or editing a keymap without hand-writing JSON, opened via the **"Keymap Builder"** button in Settings or from within the [Keymap dialog](#the-keymap-dialog).

### Layout

```
Keymap Builder

Keymap name         [ ___________ ]

Search  [___________________] ( ) Output  ( ) Keys

    Output                    Keys
 1  [ அ                    ]  [ a        ]  ✕
 2  [ ஆ                    ]  [ aa,A     ]  ✕
 3  [                       ]  [          ]  ✕   ← always-present trailing empty row

┌────────────────────────────────────────────┐
│ [ output ] [ keys ] [+]                     │  ← quick add, fixed above Create
│ instructions text  ☐ Dup only  [⇩] [✎]      │  ← Import / Raw JSON buttons
└────────────────────────────────────────────┘

[            Create Keymap            ]
```

### Row behavior

- Row 0 is fixed and non-removable: a **"keymap_name"** label on the left, an editable name field on the right.
- Every mapping row pairs an **Output** field (left, wider, multiline) with a **Keys** field (right, comma-separated).
- Typing into the current last row's Output field automatically appends a fresh empty row below it — no manual "add row" step needed while building linearly.
- A comma inside a key itself can be entered as `\,` (backslash-escaped) — e.g. keys field `\,,cm` produces two separate keys: a literal comma, and `cm`.
- Pasting multiline text into any row's Output field prompts: **split into one new row per line** (inserted immediately after that row, leaving every row above untouched) or **paste as a single multiline field**.

### Quick Add

A fixed section directly above **Create Keymap** — separate from the scrolling row list — lets you fill the current trailing empty row without scrolling to the bottom of a long keymap: enter an output and comma-separated keys, tap **+**, and the row is filled (auto-spawning the next empty row, same as typing directly into a row).

### Duplicate Detection & "Dup only" Filter

- Every keystroke in any Keys field re-scans **all** rows for keys used more than once.
- The **"Dup only"** checkbox is automatically enabled only while at least one duplicate exists, and automatically unchecked and disabled the moment the conflict is fully resolved.
- Checking it **hides** (not removes) every row without a duplicate — row numbers never shift, so a row referenced as "row 5" stays "row 5" whether the filter is on or off.
- The filter updates **live**: if you edit a row's keys so that it newly collides with another (visible or hidden) row, that row is immediately added to the filtered view without needing to re-toggle the checkbox.
- Attempting **Create Keymap** while duplicates exist shows a dialog listing every duplicated key and which row numbers it appears in, and blocks saving until resolved.

### Search / Filter

- A search box next to a two-way **Output / Keys** choice filters the visible rows.
- Accepts **comma-separated terms**; a row matches if it contains **any** one of them (OR), checked only against whichever field (Output or Keys) is currently selected.
- Combines with **"Dup only"** using AND — with both active, only rows that are both a duplicate *and* match the search are shown.
- The current trailing empty row is always exempt from every filter, so there's always a visible place to start a new entry.

### Raw JSON View & Import

- The **Raw JSON** button (✎) shows a read-only-style dialog of the builder's current rows in the form `{"<output>": "<comma-separated keys>", ...}` — useful for a quick sanity check or to copy out the current state.
- The **Import** button (⇩) opens the same format, but editable: paste or type a JSON object in that shape and it replaces every row in the builder — output becomes a row's Output field, its value becomes that row's Keys field verbatim, with no grouping applied (each JSON entry becomes exactly one row).
- Both dialogs reuse the same escaping convention as the rest of the app (`\,` for a literal comma inside a key), so content round-trips exactly between export and import with no mangling.

### Editing an Existing Keymap

Opening the builder for an already-saved keymap (via its Settings row, or the Keymap dialog's "Keymap Builder" button) pre-fills every field:

- The name field is set from the stored `keymap_name`.
- Keys sharing the same output value are automatically **grouped back into a single row** (e.g. `"m"` and `"M"` both mapping to `"ம்"` become one row with Keys field `m,M`), mirroring how new rows are normally built.
- If the name is changed before saving, the old stored entry is removed and every layout that referenced the old name is automatically updated to the new one — see [Referential Integrity](#referential-integrity-rename--delete-safety).

### Saving

- **Create Keymap** validates for duplicate keys first (see above), then checks whether the name already belongs to a different stored keymap — if so, a confirmation dialog is shown before overwriting.
- On save, the generated JSON is written with **one key-value pair per line**, in the exact order the rows were entered, so it stays readable if reopened later in the raw [Keymap dialog](#the-keymap-dialog) editor.

---

## Referential Integrity: Rename & Delete Safety

Keymaps are referenced by name from layouts, so renaming or deleting one is kept in sync everywhere it's used, automatically:

- **Live effect, no caching** — the transliteration engine always re-reads a keymap fresh from storage the moment a layout referencing it becomes active (on layout switch, subtype change, or resuming typing). An edit or deletion made in Settings or the Keymap Builder takes effect on the very next time that layout is used — there is no stale in-memory copy to worry about.
- **Renaming** (via the Keymap Builder) automatically rewrites the `keymap="..."` attribute on every stored custom layout that referenced the old name, so those layouts keep working under the new name with no manual XML editing required.
- **Deleting** a keymap that's still referenced by one or more layouts shows a confirmation dialog naming how many layouts use it. Confirming the deletion both removes the keymap **and** strips the `keymap`/`swipekeymap` attributes from every layout that referenced it, so no layout is ever left silently pointing at a keymap that no longer exists.
- Settings always resyncs its Keymap list against what's actually in storage whenever the screen is opened or resumed — including picking up renames made from a separate screen (like the Keymap Builder activity), so the list is never out of date.

---

## Dictionary Suggestions with Keymaps

Word suggestions stay correctly in sync with keymap transliteration: the "currently typed word" tracker is driven by the keymap engine's own committed output (e.g. the Tamil text actually on screen), not by the raw Latin keys typed — so suggestion queries are always made against the same script the dictionary itself is indexed in, rather than the untransliterated keystrokes. Combining marks (such as Tamil pulli/virama and vowel signs) are treated as part of the current word rather than as word separators, so suggestions continue to work correctly across a full multi-character syllable.

---

## Engine Architecture

Input flows through the following pipeline, from raw touch to final committed text:

```text
Touch
 │
 ▼
Pointers            → Multi-touch, swipe detection, long press, sliding keys,
 │                     modifier latching/locking, gesture handling
 ▼
KeyModifier          → Shift / Ctrl / Alt / Meta / Fn, compose keys, dead keys,
 │                     Hangul composition, gesture modifiers, selection mode
 ▼
KeyEventHandler       → Distinguishes center-tap output from swipe output
 │                       (isSwipe), gating whether the keymap engine applies
 │                       per the swipekeymap attribute
 ▼
KeymapEngine         → Prefix matching, longest-sequence replacement,
 │                     live conversion, word-tracker synchronization,
 │                     always re-reads the active keymap from storage
 ▼
InputConnection
```

**Component responsibilities:**

| Component                      | Responsibility                                                                                                                                                                      |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Pointers.java`              | Multi-touch tracking, swipe detection, long press, sliding keys, modifier latching/locking, gesture handling                                                                        |
| `KeyModifier.java`           | Shift / Ctrl / Alt / Meta / Fn, compose keys, dead keys, Hangul composition, gesture modifiers, selection mode                                                                      |
| `Keymap.java`                | Loads a keymap's JSON, stores its mappings, provides lookups                                                                                                                        |
| `KeymapManager.java`         | Persists all saved keymaps, resolves by name, handles add/remove/rename                                                                                                             |
| `KeymapEngine.java`          | Prefix matching, longest-sequence replacement, live conversion, word-tracker synchronization, swipe-gating via`swipekeymap`                                                       |
| `KeymapXmlAttrUtils.java`    | Reads/writes the`name`, `keymap`, and `swipekeymap` attributes on a layout's raw XML, used by both the Layout dialog's Keyboard Attributes card and rename/delete propagation |
| `KeymapBuilderActivity.java` | The guided keymap editor — rows, quick add, duplicate detection, search/filter, raw JSON import/export                                                                             |
| `LayoutsPreference.java`     | The Settings list combining Layout and Keymap rows, referential-integrity enforcement on rename/delete                                                                              |

---

## Full XML Example

```xml
<keyboard name="Example" script="latin" keymap="Tamil" swipekeymap="false">
    <row>
        <key c="a" cL="அ"/>
        <key c="k"/>
        <key c="i"/>
    </row>
</keyboard>
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
9. **The keymap engine performs longest-match, live replacement** as the user types.
10. **A keymap edit or deletion takes effect immediately** — there is no stale cache to refresh.
11. **Renaming a keymap updates every layout referencing it automatically.**
12. **Deleting an in-use keymap requires confirmation**, and clears the attribute from every layout that used it.
13. **The space bar always shows the active layout's name**, not a space glyph.

---

## 🙏 Thank You

Special thanks to the creators and contributors of **Unexpected Keyboard** for creating an elegant, open-source keyboard that made AMCustomKeyboard possible.
