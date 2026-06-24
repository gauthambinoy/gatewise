# Icons

`shield.svg` is the single monochrome source glyph (a check inside a shield).

Tauri's bundler and tray need **raster** icons (PNG/ICO/ICNS), which are **not** checked
in here because they are generated artifacts. Produce them once from the SVG before a
real build:

```sh
npm run tauri icon src-tauri/icons/shield.svg
```

That command writes the standard set the config references:

```
icons/32x32.png
icons/128x128.png
icons/128x128@2x.png
icons/icon.png        # tray icon (referenced by tauri.conf.json trayIcon.iconPath -> icon.png)
icons/icon.ico        # Windows
icons/icon.icns       # macOS
```

Until those exist, `tauri dev` / `tauri build` will error on the missing icon files —
this is expected for a source-only scaffold that was not compiled in its authoring
environment.
