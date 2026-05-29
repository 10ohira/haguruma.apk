# MCO tool — GameGuardian → standalone APK

This repo holds the **legacy** GameGuardian-based MilkChoco
(`com.gameparadiso.milkchoco`) practice tool, plus notes on the standalone
replacement that needs **no GameGuardian**.

## Legacy (GameGuardian) — what's here

- `script_for_gg_fixed.lua.txt` — GameGuardian Lua. Does the real memory
  read/write (shoot, reload, damage, respawn, speed, blackhole, aimbot, aim
  assist, enemy scan, capture/teleport). It reads commands from
  `/sdcard/Download/MCO_GG_command.json`.
- `MCO-Remote.apk` — a floating-menu UI app that *only* writes that JSON file.

So the old setup needs **two things plus root**: GameGuardian (running the Lua
to touch memory) and the remote APK (the UI). Root is required either way —
reading another app's memory is impossible inside the Android sandbox without
it.

## Standalone (no GameGuardian) — the replacement

The same features now run from **a single installable APK** with no
GameGuardian and no terminal. It is built from the **`pixel` repo's
`mobile/`** project, which already injects a Frida agent (a superset of the Lua
features) into MilkChoco and serves the full control panel on
`http://127.0.0.1:27345`.

What changed to make it install-and-go (in the `pixel` repo, branch
`claude/lua-tool-standalone-Erpd2`):

- The APK now **bundles** `dist/agent.js` + the `frida-inject` binary for every
  ABI as assets.
- On launch, `Injector.kt` does what the old `launch.sh` did by hand: requests
  root (`su`), drops the agent + ABI-matched `frida-inject` into
  `/data/local/tmp/pixel`, launches/attaches MilkChoco, injects, and loads the
  panel.

### Use it

1. Build `pixel/mobile` (CI on push produces `pixel-mobile.apk`, or
   `npm install && npm run build` then build `mobile/app` in Android Studio).
2. Install `pixel-mobile.apk` on the **rooted** phone.
3. Open it and grant the root (`su`) prompt — the panel appears once injection
   finishes. GameGuardian and `MCO-Remote.apk` are no longer needed.

> Root is still mandatory (same as GameGuardian). A non-rooted phone cannot
> inject into another app — that's the Android sandbox, not something code can
> bypass. Research / education only.
