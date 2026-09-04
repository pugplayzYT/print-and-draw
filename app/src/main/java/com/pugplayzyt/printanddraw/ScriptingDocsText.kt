package com.pugplayzyt.printanddraw

const val SCRIPTING_DOCS_V2 = """# Print & Draw Scripting Language

Print & Draw includes a tiny drawing-only scripting language. It can move a virtual pen, draw coloured line segments, use variables and conditions, clear the scene while running, and optionally generate frame-by-frame animations.

The language cannot access files, the network, Android APIs, a shell, reflection, or arbitrary Kotlin/Java code.

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

## Animation quick start

Animations are opt-in. Register the frame system, choose a frame rate, then generate a fixed number of frames.

```text
register frame system
frame rate 30

generate 180 frames {
    let x = frame * 5
    let y = h / 2

    pen up
    pen width 30
    pen colour 220, 40, 40
    pen position x, y
    pen down
    pen position x + 100, y
    pen up
}
```

At 30 FPS, 180 frames lasts about 6 seconds. The built-in `frame` value changes every frame, so `x = frame * 5` moves the object five pixels farther on every frame.

## Comments

Anything after `#` on a line is ignored.

## Pen commands

### `pen up`
Moves the pen without drawing.

### `pen down`
Makes later position changes draw lines.

### `pen position X, Y`
Moves the pen to a canvas coordinate. If the pen is down, a line is drawn from the previous position to the new position.

Aliases: `pen pos X, Y` and `pos X, Y`.

Coordinates are clamped to the canvas.

### `pen speed N`
Controls scripted position updates per second. `speed N` is also accepted. Speed is clamped to `0.1` through `1000`.

For frame animation, normally use a high pen speed such as `1000`; the frame rate controls animation timing.

### `pen width N`
Changes the width used by future lines. `width N` is also accepted. Width can be an expression and is clamped to `1` through `500` pixels.

### `pen colour R, G, B`
Changes the RGB colour used by future lines. American spelling and shorter aliases are also accepted: `pen color`, `colour`, and `color`.

Each channel can be an expression. Values are rounded and clamped to `0` through `255`.

## Scene commands

### `clear scene`
Clears the current canvas immediately and keeps the script running.

Alias: `clear canvas`.

```text
pen width 30
pen colour 255, 0, 0
pen position 40, 100
pen down
pen position 300, 100
pen up

clear scene

pen colour 0, 120, 255
pen position 40, 180
pen down
pen position 300, 180
pen up
```

It removes script-generated static drawing and anything already drawn in the current frame. It does not reset variables, pen position, pen up/down state, pen speed, pen width, or pen colour.

Inside a generated frame block, it also removes static script drawing that existed before the frame block from that point onward.

## Variables

Create a variable with `let` and change one with `set`.

```text
let x = 100
let radius = 150
set x = x + 10
```

Variables store numbers.

## Built-in variables

Always available:

- `w` — canvas width in pixels
- `h` — canvas height in pixels
- `pi` — π

Inside a generated frame block:

- `frame` — current frame number, starting at `1`
- `frames` — total number of frames
- `fps` — current frame rate

```text
let progress = frame / frames
let seconds = frame / fps
let x = progress * w
```

## Arithmetic

Supported operators: `+`, `-`, `*`, `/`, `%`, unary `+` and `-`, and parentheses.

## Math functions

- `sin(value)`
- `cos(value)`
- `tan(value)`
- `sqrt(value)`
- `abs(value)`

Trig functions use degrees.

## Repeat loops

```text
let x = 50
repeat 10 {
    pen position x, 100
    set x = x + 20
}
```

Repeat count is rounded to an integer and limited to 100,000.

## If blocks

```text
if x < w / 2 {
    set x = x + 20
}
```

Comparisons: `>`, `<`, `>=`, `<=`, `==`, `!=`.

Conditions can be combined:

```text
if frame > 70 and frame < 500 {
}

if frame < 20 or frame > 100 {
}
```

Aliases `&&` and `||` are also accepted, and parentheses can group conditions.

There is currently no `else` block.

# Frame animation

## Registering the frame system

```text
register frame system
```

Alias:

```text
register frames
```

Normal one-pass scripts do not need this.

## Frame rate

```text
frame rate 30
fps 30
```

Default: 30 FPS. Allowed range: 1 through 120 FPS.

Approximate duration is `frames / fps` seconds.

- 300 frames at 30 FPS ≈ 10 seconds
- 1,800 frames at 30 FPS ≈ 60 seconds
- 3,600 frames at 60 FPS ≈ 60 seconds

## Generating frames

Preferred:

```text
generate 300 frames {
    # code for one frame
}
```

Aliases:

```text
generate frames 300 {
}

frames 300 {
}
```

The block runs once for every frame. `frame` starts at 1 and reaches the requested frame count on the final frame.

## Frame state resets every frame

When a frame block begins, the engine saves the current variables and pen state. Every generated frame starts from that same baseline.

Use `frame` to calculate animation state:

```text
generate 300 frames {
    let x = frame * 4
    pen position x, h / 2
}
```

Do not expect changes from the previous frame to accumulate automatically.

The baseline includes variables, pen position, pen up/down state, pen speed, pen colour, and pen width.

## Static drawing plus animation

Anything drawn before a frame block is treated as static content and is redrawn with every generated frame unless a later `clear scene` removes it. This is useful for backgrounds.

```text
# Static road
pen up
pen width 80
pen colour 80, 80, 80
pen position 0, h - 100
pen down
pen position w, h - 100
pen up

register frame system
frame rate 30

generate 300 frames {
    let carX = frame * 4
    pen width 30
    pen colour 220, 40, 40
    pen position carX, h - 140
    pen down
    pen position carX + 90, h - 140
    pen up
}
```

## Timed events

Use frame conditions to start or stop effects:

```text
register frame system
frame rate 30

generate 600 frames {
    if frame > 70 and frame < 500 {
        let rainY = (frame * 12) % h
        pen width 4
        pen colour 70, 150, 255
        pen position w / 2, rainY
        pen down
        pen position w / 2 - 8, rainY + 20
        pen up
    }
}
```

For timing that should be independent of FPS:

```text
let seconds = frame / fps
if seconds > 5 and seconds < 10 {
}
```

## One-minute animation

At 30 FPS, one minute is 1,800 frames:

```text
register frame system
frame rate 30

generate 1800 frames {
    let progress = frame / frames
    let x = progress * w

    pen up
    pen width 25
    pen colour 40, 180, 70
    pen position x, h
    pen down
    pen position x, h - 100 - progress * 300
    pen up
}
```

## Frame rendering behavior

Generated frames visually replace the previous generated frame instead of permanently stacking every animation frame. Static segments drawn outside the frame block are included again on every frame unless cleared.

Status displays progress such as `Frame 37 / 300 • 30 fps`.

Frame blocks cannot be nested.

## Run, Stop and Clear Canvas

- **Run** starts the script or animation.
- **Stop** cancels it.
- **Clear canvas** in the app UI stops it and deletes all drawn segments.
- `clear scene` or the `clear canvas` scripting alias clears the scene without stopping the script.

## Limits

- maximum executed script/drawing steps: 100,000
- maximum repeat count: 100,000
- maximum generated frames in one frame block: 10,000
- frame rate: 1 to 120 FPS
- default frame rate: 30 FPS
- nested frame blocks are not allowed
- pen speed: 0.1 to 1000
- pen width: 1 to 500 pixels
- RGB channels: 0 to 255
- positions are clamped to the canvas

The 100,000-step execution limit also applies during animation generation.

## Not supported

The language currently has no file access, network access, imports, strings, classes, user-defined functions, `while`, `else`, sound commands, sprite/image loading, reflection, shell commands, or arbitrary code execution.
"""
