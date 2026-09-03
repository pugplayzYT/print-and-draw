# Print & Draw Scripting Language

The scripting language is a tiny drawing-only language built into Print & Draw. It cannot access files, the network, Android APIs, a shell, or arbitrary Kotlin/Java code. Its only output is moving the virtual pen and adding line segments to the canvas.

## Quick start

```text
pen up
pen speed 1000
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
Controls scripted position updates per second.

```text
pen speed 1
pen speed 20
pen speed 1000
```

`speed N` is also accepted. The engine clamps speed to `0.1` through `1000`. At `1000`, drawing is effectively instant. At `1`, each `pen position` update is roughly one second apart.

## Variables

Create a variable with `let`:

```text
let x = 100
let radius = 150
```

Change an existing or new variable with `set`:

```text
set x = x + 10
```

Variables store numbers.

## Built-in variables

- `w` — canvas width in pixels
- `h` — canvas height in pixels
- `pi` — π

Example:

```text
let cx = w / 2
let cy = h / 2
pen position cx, cy
```

## Arithmetic

Supported operators:

- `+` addition
- `-` subtraction
- `*` multiplication
- `/` division
- `%` remainder
- unary `+` and `-`
- parentheses `( )`

Example:

```text
let x = (w / 2) + 40
let y = h * 0.25
```

## Math functions

- `sin(value)`
- `cos(value)`
- `tan(value)`
- `sqrt(value)`
- `abs(value)`

Trig functions use **degrees**, not radians.

```text
let x = cos(90) * 100
let y = sin(90) * 100
```

## Repeat loops

```text
let x = 50
repeat 10 {
    pen position x, 100
    set x = x + 20
}
```

The repeat count is an expression and is rounded to an integer. A repeat count is limited to 100,000.

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

If no comparison is used, zero means false and any non-zero number means true.

There is currently no `else` block.

## Circle example

```text
pen up
pen speed 1000
let cx = w / 2
let cy = h / 2
let r = 120
let a = 0
pen position cx + cos(a) * r, cy + sin(a) * r
pen down
repeat 361 {
    pen position cx + cos(a) * r, cy + sin(a) * r
    set a = a + 1
}
pen up
```

## Script colour and width

A script uses the **currently selected app colour and block thickness when you press Run**. The language does not currently have `pen colour` or `pen width` commands.

## Run, Stop and Clear Canvas

- **Run** starts the script.
- **Stop** cancels a running script.
- **Clear canvas** on the main screen stops any running script and deletes every drawn segment, including segments created by scripts.

## Limits

To stop accidental runaway scripts:

- maximum executed drawing steps: 100,000
- maximum repeat count: 100,000
- pen speed: 0.1 to 1000
- positions are clamped to the canvas

The language has no file access, network access, imports, strings, classes, user-defined functions, `while`, `else`, reflection, shell commands, or arbitrary code execution.
