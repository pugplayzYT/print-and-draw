# Print & Draw

A small Material 3 Android drawing app.

## Features

- Drag from one point to another to place a straight coloured block/stroke.
- HSV colour wheel.
- Adjustable stroke/block thickness.
- Undo and redo.
- Print through Android's system print service to supported physical/network printers.
- Save the canvas as a PNG in `Pictures/PrintAndDraw`.
- GitHub Actions cloud build that uploads a debug APK artifact.

## Cloud build

Open **Actions → Build Android APK** or push to `main`. The workflow uploads `print-and-draw-debug-apk` containing `app-debug.apk`.

## Requirements

- Android 10 (API 29) or newer.
