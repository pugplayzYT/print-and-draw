package com.pugplayzyt.printanddraw

const val SCRIPTING_DOCS_V2 = """# Print & Draw Scripting Language

The scripting language is a tiny drawing-only language built into Print & Draw. It cannot access files, the network, Android APIs, a shell, or arbitrary Kotlin/Java code. Its only output is moving the virtual pen and adding line segments to the canvas.

## Quick start

```text
pen up
pen speed 1000
pen width 12
pen colour 35, 120, 255
pen position 100, 100
pen down
pen position 300, 100
pen position 300, 300
pen position 100, 300
pen position 100, 100
pen up
```

## Comments

Anything after `#` on a line is ignored.

```text
# This is a comment
pen up # this is also a comment
```

## Pen commands

### `pen up`
Moves the pen without drawing.

### `pen down`
Makes later position changes draw lines.

### `pen position X, Y`
Moves the pen to a canvas coordinate. If the pen is down, a line is drawn from the previous position to the new position.

Aliases also accepted:

```text
pen pos X, Y
pos X, Y
```

Coordinates are clamped to the canvas, so scripts cannot draw outside it.

### `pen speed N`
Controls scripted position updates per second. `speed N` is also accepted. Speed is clamped to `0.1` through `1000`.

### `pen width N`
Changes the width used by future lines. `width N` is also accepted. Width can be an expression and is clamped to `1` through `500` pixels.

```text
pen width 6
pen width 40
```

### `pen colour R, G, B`
Changes the RGB colour used by future lines. American spelling and shorter aliases are also accepted: `pen color`, `colour`, and `color`.

```text
pen colour 128, 128, 128
pen colour 255, 210, 0
pen color 210, 205, 190
```

Each RGB channel can be an expression. Values are rounded and clamped to `0` through `255`.

## Road example

```text
pen speed 1000
let y = h / 2

# Wide grey road
pen up
pen width 100
pen colour 90, 90, 90
pen position 40, y
pen down
pen position w - 40, y

# Concrete sidewalks
pen up
pen width 22
pen colour 190, 185, 170
pen position 40, y - 62
pen down
pen position w - 40, y - 62

pen up
pen position 40, y + 62
pen down
pen position w - 40, y + 62

# Yellow centre line
pen up
pen width 7
pen colour 255, 205, 0
pen position 40, y
pen down
pen position w - 40, y
pen up
```

## Variables

Create a variable with `let` and change one with `set`.

```text
let x = 100
let radius = 150
set x = x + 10
```

## Built-in variables

- `w` — canvas width in pixels
- `h` — canvas height in pixels
- `pi` — π

## Arithmetic

Supported operators:

- `+` addition
- `-` subtraction
- `*` multiplication
- `/` division
- `%` remainder
- unary `+` and `-`
- parentheses `( )`

## Math functions

- `sin(value)`
- `cos(value)`
- `tan(value)`
- `sqrt(value)`
- `abs(value)`

Trig functions use **degrees**, not radians.

## Repeat loops

```text
let x = 50
repeat 10 {
    pen position x, 100
    set x = x + 20
}
```

The repeat count is rounded to an integer and limited to 100,000.

## If blocks

```text
if x < w / 2 {
    set x = x + 20
}
```

Supported comparisons:

- `>`
- `<`
- `>=`
- `<=`
- `==`
- `!=`

Zero means false and any non-zero expression means true. There is currently no `else` block.

## Initial script colour and width

When a script starts, its initial pen colour and width come from the app's currently selected colour and block thickness. `pen colour` and `pen width` can then override those values at any point.

## Run, Stop and Clear Canvas

- **Run** starts the script.
- **Stop** cancels a running script.
- **Clear canvas** stops a running script and deletes every drawn segment.

## Limits

- maximum executed drawing steps: 100,000
- maximum repeat count: 100,000
- pen speed: 0.1 to 1000
- pen width: 1 to 500 pixels
- RGB channels: 0 to 255
- positions are clamped to the canvas

The language has no file access, network access, imports, strings, classes, user-defined functions, `while`, `else`, reflection, shell commands, or arbitrary code execution.
"""
