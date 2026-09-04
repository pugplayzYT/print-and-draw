# Print & Draw Scripting Language

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

Aliases:

```text
pen pos X, Y
pos X, Y
```

Coordinates are clamped to the canvas.

### `pen speed N`

Controls scripted position updates per second.

```text
pen speed 1
pen speed 20
pen speed 1000
```

`speed N` is also accepted. Speed is clamped to `0.1` through `1000`. At `1000`, position commands are effectively instant. At `1`, each `pen position` update is roughly one second apart.

For frame animation, normally use a high pen speed such as `1000`; the frame rate controls animation timing.

### `pen width N`

Changes the width used by future lines.

```text
pen width 6
pen width 40
```

`width N` is also accepted. Width can be an expression and is clamped to `1` through `500` pixels.

### `pen colour R, G, B`

Changes the RGB colour used by future lines.

```text
pen colour 128, 128, 128
pen colour 255, 210, 0
```

American spelling and shorter aliases are also accepted:

```text
pen color 255, 0, 0
colour 0, 200, 255
color 0, 200, 255
```

Each channel can be an expression. Values are rounded and clamped to `0` through `255`.

## Scene commands

### `clear scene`

Clears the current canvas immediately and then continues running the script.

```text
pen up
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

`clear canvas` is also accepted as an alias.

Clearing the scene removes script-generated static drawing and anything already drawn in the current frame. It does **not** reset variables, pen position, pen up/down state, pen speed, pen width, or pen colour.

Inside a generated frame block, `clear scene` also removes static script drawing that existed before the frame block from that point onward.

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

Always available:

- `w` — canvas width in pixels
- `h` — canvas height in pixels
- `pi` — π

Inside a generated frame block, these are also available:

- `frame` — current frame number, starting at `1`
- `frames` — total number of frames in the current generated animation
- `fps` — current frame rate

Example:

```text
let progress = frame / frames
let seconds = frame / fps
let x = progress * w
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

Conditions can be combined with logical AND and OR:

```text
if frame > 70 and frame < 500 {
    # active only in this frame range
}

if frame < 20 or frame > 100 {
    # active near either end
}
```

Symbol aliases are also accepted:

```text
if frame > 70 && frame < 500 {
}

if frame < 20 || frame > 100 {
}
```

Parentheses can group conditions:

```text
if (frame > 20 and frame < 50) or frame == 100 {
}
```

If no comparison is used, zero means false and any non-zero number means true. There is currently no `else` block.

# Frame animation

## Registering the frame system

Before setting an animation frame rate or generating frames, register the frame system:

```text
register frame system
```

The shorter alias is:

```text
register frames
```

Normal scripts do not need to register it. This keeps the original one-pass drawing system fully supported.

## Frame rate

Set the animation rate with either form:

```text
frame rate 30
fps 30
```

The default is 30 FPS. The engine clamps frame rate to `1` through `120` FPS.

Approximate duration is:

`duration in seconds = frames / fps`

Examples:

- 300 frames at 30 FPS ≈ 10 seconds
- 1,800 frames at 30 FPS ≈ 60 seconds
- 3,600 frames at 60 FPS ≈ 60 seconds

## Generating frames

Preferred syntax:

```text
generate 300 frames {
    # code for one frame
}
```

These aliases are also accepted:

```text
generate frames 300 {
}

frames 300 {
}
```

The block runs once for each frame. `frame` starts at `1` and reaches the requested frame count on the final frame.

## Frame state resets every frame

When a `generate ... frames` block begins, the engine remembers the current variables and pen state. Every generated frame starts from that same baseline.

This means changes made inside frame 1 do **not** automatically carry into frame 2.

Do this:

```text
generate 300 frames {
    let x = frame * 4
    pen position x, h / 2
}
```

Do not rely on this to accumulate movement between frames:

```text
let x = 0
generate 300 frames {
    set x = x + 4
}
```

Because every frame restarts from the baseline, `frame` should normally drive animation state.

The baseline includes:

- variables
- pen position
- pen up/down state
- pen speed
- pen colour
- pen width

## Static drawing plus animated drawing

Anything drawn before the frame block is treated as static content and is redrawn with every generated frame.

That is useful for backgrounds:

```text
# Draw the road once
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

The road remains while the car changes position every frame.

## Timed events

Use `frame` conditions to make events start or stop at a particular point in an animation.

```text
register frame system
frame rate 30

generate 600 frames {
    if frame > 70 and frame < 500 {
        # draw rain during this part of the animation
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

For time-based events that should behave the same at different frame rates, calculate seconds:

```text
let seconds = frame / fps
if seconds > 5 and seconds < 10 {
    # active from roughly 5 to 10 seconds
}
```

## One-minute animation example

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

Generated frames visually replace the previous generated frame instead of permanently stacking every animation frame on top of the canvas. Static segments drawn outside the frame block are included again on every frame unless a later `clear scene` removes them.

The script status displays progress such as `Frame 37 / 300 • 30 fps` while an animation is running.

Frame blocks cannot be nested inside other frame blocks.

## Circle example

```text
pen up
pen speed 1000
pen width 8
pen colour 80, 160, 255
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

## Initial script colour and width

When a script starts, its initial pen colour and width come from the app's currently selected colour and block thickness. `pen colour` and `pen width` can override those values at any point.

## Run, Stop and Clear Canvas

- **Run** starts the script or animation.
- **Stop** cancels a running script or animation.
- **Clear canvas** in the app UI stops a running script and deletes every drawn segment.
- `clear scene` or its `clear canvas` scripting alias clears the canvas without stopping the script.

## Limits

To stop accidental runaway scripts:

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

The 100,000-step execution limit also applies while generating animation frames, so a very large frame count combined with a very large number of commands per frame can hit the step limit before the frame limit.

## Not supported

The language currently has no file access, network access, imports, strings, classes, user-defined functions, `while`, `else`, sound commands, sprite/image loading, reflection, shell commands, or arbitrary code execution.
