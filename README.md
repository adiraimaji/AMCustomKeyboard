# ⌨️ AMCustomKeyboard

**A powerful fork of Unexpected Keyboard with transliteration keymaps, independent key labels, and advanced layout customization.**

---

## Table of Contents

1. [Overview](#overview)
2. [Credits](#credits)
3. [Core Concept: Display vs. Output vs. Final Result](#core-concept-display-vs-output-vs-final-result)
4. [Independent Labels](#independent-labels)
5. [Keymaps (Transliteration Engine)](#keymaps-transliteration-engine)
6. [Keymap Behavior Rules](#keymap-behavior-rules)
7. [Key Position Reference](#key-position-reference)
8. [Shift Output Rules](#shift-output-rules)
9. [Label Attribute Rules](#label-attribute-rules)
10. [Engine Architecture](#engine-architecture)
11. [Full XML Example](#full-xml-example)
12. [Quick-Reference Rules Summary](#quick-reference-rules-summary)

---

## Overview

AMCustomKeyboard extends the standard keyboard model with three independent layers:

- **What the key shows** (label)
- **What the key sends when tapped or swiped** (output)
- **What that output becomes after transliteration** (final result, via the Keymap Engine)

Because these three layers are decoupled, a single key can display an icon, output a Latin letter, and ultimately produce a completely different script — all without conflicting with each other.

---

## Credits

AMCustomKeyboard is built upon the excellent [**Unexpected Keyboard**](https://github.com/Julow/Unexpected-Keyboard) project. This fork preserves the original gesture-driven keyboard while extending it with two major features: **Keymaps** and **Independent Labels**.

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
|-----------------|-----------------|
| 🍎 | a |

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

The engine always resolves to the **longest valid mapping** available at each step, not just the most recent character.

---

## Keymap Behavior Rules

These rules define exactly *when* the keymap engine is applied.

### Rule 1 — Only primary tap output is transliterated

When a layout specifies a `keymap`, **only the primary tap output** (`c` for lowercase, `C` for shift/uppercase) is passed through the keymap engine.

### Rule 2 — Swipe outputs always bypass the keymap

**Swipe outputs are never transliterated**, regardless of whether the layout uses a keymap. This applies to all swipe directions (`nw`, `n`, `ne`, `e`, `se`, `s`, `sw`, `w`) and their uppercase variants.

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

The resulting behavior is:

| Action | XML Attribute Used | Keymap Applied? | Final Output |
|--------|--------------------|:---:|:---:|
| Tap | `c="a"` | ✅ Yes | `α` |
| Shift + Tap | `C="A"` | ✅ Yes | `Α` |
| Swipe East | `e="b"` | ❌ No | `b` |
| Shift + Swipe East | `E="B"` | ❌ No | `B` |
| Swipe North-East | `ne="1"` | ❌ No | `1` |

> **Important:** Swipe keys always send the exact value defined in the layout. They intentionally and unconditionally bypass the keymap engine — this is by design, not a limitation.

---

## Key Position Reference

Each key has up to nine directional zones (center + eight swipe directions), each with its own **output**, **label**, and **shift variant**.

### Lowercase Output Attributes

| `nw` | `n` | `ne` |
|:---:|:---:|:---:|
| **`w`** | **`c`** | **`e`** |
| `sw` | `s` | `se` |

### Lowercase Label Attributes

| `nwL` | `nL` | `neL` |
|:---:|:---:|:---:|
| **`wL`** | **`cL`** | **`eL`** |
| `swL` | `sL` | `seL` |

### Uppercase (Shift) Output Attributes

| `NW` | `N` | `NE` |
|:---:|:---:|:---:|
| **`W`** | **`C`** | **`E`** |
| `SW` | `S` | `SE` |

### Uppercase (Shift) Label Attributes

| `NWL` | `NL` | `NEL` |
|:---:|:---:|:---:|
| **`WL`** | **`CL`** | **`EL`** |
| `SWL` | `SL` | `SEL` |

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
KeyEventHandler
 │
 ▼
KeymapEngine         → Prefix matching, longest-sequence replacement,
 │                     live conversion, word-tracker synchronization
 ▼
InputConnection
```

**Component responsibilities:**

| Component | Responsibility |
|-----------|-----------------|
| `Pointers.java` | Multi-touch tracking, swipe detection, long press, sliding keys, modifier latching/locking, gesture handling |
| `KeyModifier.java` | Shift / Ctrl / Alt / Meta / Fn, compose keys, dead keys, Hangul composition, gesture modifiers, selection mode |
| `Keymap.java` | Loads JSON, stores mappings, provides lookups |
| `KeymapEngine.java` | Prefix matching, longest-sequence replacement, live conversion, word-tracker synchronization |

---

## Full XML Example

```xml
<keyboard name="Example" script="latin" keymap="Tamil">
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
6. **Only primary tap output (`c` / `C`) passes through the keymap engine.**
7. **Swipe outputs always bypass the keymap engine**, with no exceptions.
8. **The keymap engine performs longest-match, live replacement** as the user types.

---

## 🙏 Thank You

Special thanks to the creators and contributors of **Unexpected Keyboard** for creating an elegant, open-source keyboard that made AMCustomKeyboard possible.
