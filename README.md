# はぐるま — standalone MilkChoco APK (rooted environment required)

A single installable APK that runs the **desktop `haguruma.exe` Frida agent**
against MilkChoco (`com.gameparadiso.milkchoco`) on a **rooted phone** — no
GameGuardian, no PC, no ADB, no terminal. It mirrors the desktop build's login
(operator ID + password), operation, UI and **Workshop** design. The phone
panel exposes the mobile-usable cheats: **aimbot, aim-assist, aim-by-circle,
no-recoil, no-spread**, plus **slot kicker** and **teleport**.

```
   Install haguruma.apk
        │
        ▼  open the app
   ┌──────────────┐   login (real auth)   ┌──────────────┐   tap Connect (root)
   │ LOGIN screen │ ───────────────────►  │CONNECT screen│ ───────────────────┐
   └──────────────┘                       └──────────────┘                    │
                                                                              ▼
   phone WebView  ◄───── http://127.0.0.1:27345 ◄──── in-process HTTP/SSE server (src/server.js)
        ▲                  aim-only control panel       shadows Frida send()/recv()
        └──────────── haguruma.exe agent (agent/agent.ts) injected by frida-inject --realm=emulated
```

## How it works

This is the desktop **`haguruma.exe`** agent, configured for phone use:

- `agent/` (the Frida agent) is injected into MilkChoco by a bundled,
  ABI-matched `frida-inject`. It does all the memory work.
- A **Java.perform Xigncode bypass** runs at the top of the agent
  (`XigncodeClientSystem.initialize` is replaced with a fake-callback wrapper,
  `getCookie2` is routed through, `OnHackDetected` is neutered).
- The injection runs with **`--realm=emulated`** by default, so the agent's
  JS context lives inside Frida's Stalker-based emulator and is invisible to
  Xigncode's anti-Frida memory/module scans. The launcher probes
  `frida-inject --help` first and silently drops the flag on older builds.
- `src/server.js` runs a tiny HTTP/SSE server **inside the game process** and
  shadows Frida's `send()`/`recv()`, so the renderer (`web-src/`) is served at
  `http://127.0.0.1:27345`. The APK loads that URL in a WebView.
- The APK (`app/`) is a thin native shell: a branded **login** screen
  (real auth against the existing backend), then a one-button **Connect** that
  injects with root and loads the panel. See `app/.../MainActivity.kt`,
  `Bridge.kt` (native login + connect), `Injector.kt` (root injection).

> ⚠️ **Root is mandatory.** Reading another app's memory is impossible inside
> the Android sandbox without it — the same requirement GameGuardian has. This
> removes the GameGuardian dependency and every manual step, not root.
> Research / education only.

## Panel contents

| Cheat | What it does | Knobs |
|---|---|---|
| **Aimbot** | Smooth-follow aim lock toward the closest in-FOV enemy | Follow Speed, Target Range (FOV), Skip Teammates / Dead, hotkey |
| **Aim Assist** | Slows the camera toward an enemy while shooting (no lock) | Follow Speed, Target Range (FOV), Skip Teammates / Dead |
| **Aim by Circle** | Locks onto the enemy whose head sits inside an on-screen circle | Radius, Follow Speed, show circle + color, Skip Teammates / Dead, hotkey |
| **No Recoil** | Native-side patch (`Spread::Recoil`) | toggle |
| **No Spread** | Native-side patch (all `Spread::GetAimGapByCurState` buckets) | toggle |
| **Slot Kicker** | `FMatchKickUserSlot` per slot button, "kick all enemies", auto-loop | per-slot buttons, loop interval |
| **Teleport** | Per-map CTM milk / choco preset coordinates | map + 4 milk + 4 choco |

All aim functions use a shared **frame-rate-independent exponential easing**
(`factor = 1 - exp(-rate * dt)`), so the camera glides toward the target the
way a human hand tracks — fast when far, easing as it closes in — at every
Speed setting (no snap, ever).

Other agent-side cheats (ESP, blackhole, fly, move-speed, changer, utilities,
resource hack, etc.) are intact in the agent code, just not exposed in this
build's panel. Saved keybinds / configs for them still survive in
`localStorage` if a future build re-enables them.

## Use it

1. Get `haguruma.apk` from a GitHub release (preferred) or the `apk` artifact on
   GitHub Actions.
2. Install it on the **rooted** phone (MilkChoco already installed).
3. Open **はぐるま** → log in with your key → tap **Connect to MilkChoco** →
   grant the root (`su`) prompt. The aim panel loads once injection finishes.

Optional environment knobs (set before launching the in-app injector, or when
running `launch.sh` from a shell):

| Variable | Effect |
|---|---|
| `PIXEL_REALM=native` | Disable the emulated realm if it misbehaves on your device. |
| `PIXEL_RESTART=1` | Force-stop MilkChoco before attaching for the cleanest Xigncode bypass. Off by default so the inject button doesn't kill an in-progress match. |

## Build

CI does it all — push and the `apk` job builds `haguruma.apk` (`npm run build`
produces `dist/agent.js` + `bin/frida-inject-*`, which the Gradle
`copyPixelAssets` task bundles into the APK; then `gradle assembleDebug`).
Tag with `v*` (e.g. `v1.56.0`) or include `[release]` in the commit message
to also publish a release with `RELEASE_NOTES.md` as the body.

Locally (agent only; the APK needs the Android SDK and is best built in CI):

```bash
npm install && npm run build      # -> dist/agent.js + per-ABI bin/frida-inject
```

A `dist/haguruma-mobile-<abi>.zip` + `launch.sh` manual-injection bundle is also
produced for use without the APK (drop on phone, `su sh launch.sh`).

## Layout

```
agent/                  haguruma.exe Frida agent (+ offsets, types)
  agent.ts              Xigncode bypass (Java.perform) + aim/cheat handlers
  offsets.ts            libMyGame.so symbol table + xa-patch magic values
web-src/                renderer (aim-only panel + 4-language UI)
src/server.js           in-agent HTTP/SSE bridge (shadows send/recv)
build.cjs, scripts/     build pipeline -> dist/agent.js + per-ABI bundles
launch.sh               manual on-device root injector (fallback, no APK)
app/                    the installable APK (login + connect shell -> panel)
  app/src/main/java/com/pixel/mobile/
      MainActivity.kt   branded login -> connect shell, then loads the panel
      Bridge.kt         native real-auth POST + connect trigger
      Injector.kt       root su -> push agent + frida-inject --realm=emulated
  app/src/main/res/     adaptive launcher icon (twin-gear はぐるま logo), strings
legacy/                 the old GameGuardian tool (Lua + MCO-Remote.apk), kept for reference
RELEASE_NOTES.md        body for the next tagged GitHub release
```

## Honest status

Verified by the build pipeline: agent + renderer bundling, offset injection,
generated-script syntax, `launch.sh` shell syntax, Kotlin string-template
escaping, aim-easing math.

**Not** verifiable from a CI/sandbox and needing one real pass on a rooted
device:

- On-device runtime (Frida `Socket`, `frida-inject` under root, SELinux
  ptrace, `--realm=emulated` on the device's ABI).
- The in-app `su` injection round trip and the real login.
- The Gradle APK build itself (standard recipe, runs in CI — there is no
  Android SDK in the dev sandbox).

One parity caveat by design: on the desktop, **ESP / aim-circle visuals** are
painted by a separate always-on-top overlay window over the game. The phone
panel is a normal WebView, so those on-game visuals aren't drawn (a future
`SYSTEM_ALERT_WINDOW` overlay could add them). The underlying logic still runs
in the agent — every aim function works against the live entity list; only the
"show circle" indicator over the live game is absent.
