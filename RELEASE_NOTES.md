# はぐるま v1.56.0 (Android)

The Android port of the desktop **はぐるま** tool, now matching the desktop
build's **login, operation, feature surface, UI and design**. Drop the APK on a
rooted phone with MilkChoco installed, sign in with your **operator ID +
password**, tap **Connect to MilkChoco**, and the control panel loads at
`http://127.0.0.1:27345`.

## Install

1. Download **`haguruma.apk`** below.
2. Install on a **rooted** phone (Magisk / KernelSU OK) that already has
   MilkChoco (`com.gameparadiso.milkchoco`).
3. Open **はぐるま** → log in with your operator ID + password → tap
   **Connect to MilkChoco** → approve `su`. The panel loads once injection
   finishes.

`haguruma-mobile-<abi>.zip` (also attached) is the manual-injection fallback:
unzip on the phone and run `su sh launch.sh` — no APK needed.

## What changed in this build

### Desktop parity — login

Login now uses the same **operator credentials** flow as the desktop build:
the native shell posts `{ id, password }` to **`/api/haguruma/login`** on
`haguruma.vercel.app` (mirrors desktop `src/data/auth.ts`), instead of the old
`pixel-code` endpoint.

### Desktop parity — UI & design

The whole app is reskinned with the desktop **"Workshop" design system** —
charcoal-on-cream (`#f4ede0`), orange accent (`#c45a2c`), hairline borders,
square corners, IBM Plex type, and the twin-gear (歯車 → はぐるま) logo. The
native login/connect shell, the in-app panel, the app name, the launcher icon
and the package identity (`com.tenohira.haguruma`) are all rebranded はぐるま.

### Panel features (mobile-usable subset)

The agent (`agent/agent.ts`) and offset tables are the **same** as desktop, so
all in-game math is identical. The phone panel exposes the cheats that don't
need a desktop-only system overlay or global keyboard hook:

- **Aimbot** — lock-style aim toward the closest in-FOV enemy
- **Aim Assist** — slows the camera toward an enemy while shooting (no lock)
- **Aim by Circle** — locks onto enemies whose head sits inside an on-screen circle
- **No Recoil** — native patch on `Spread::Recoil`
- **No Spread** — native patch on every `Spread::GetAimGapByCurState` bucket
- **Slot Kicker** — `FMatchKickUserSlot` per slot, kick-all, auto-loop
- **Teleport** — per-map CTM milk / choco preset coordinates

### How it works

- The desktop はぐるま agent is injected into MilkChoco by a bundled,
  ABI-matched `frida-inject`, attaching to the game PID in the **native
  realm** (required: the Xigncode bypass is Java instrumentation, which Frida
  can only do from the native realm).
- A **Java.perform Xigncode bypass** runs at the top of the agent
  (`XigncodeClientSystem.initialize` wrapped with a fake callback,
  `getCookie2` routed through, `OnHackDetected` neutered).
- An in-process HTTP/SSE server (`src/server.js`) shadows Frida `send()` /
  `recv()` so the WebView panel talks to the agent with no Node/Electron.

## Known caveats

- ESP / aim-circle **visuals** on top of the live game are not drawn (the
  phone panel is a regular WebView, not a system overlay). The underlying aim
  logic still works against the real entity list.
- Root is mandatory.
- Research / education only.
