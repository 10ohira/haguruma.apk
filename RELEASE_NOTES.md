# はぐるま v1.56.0 (Android)

The Android port of the desktop **はぐるま** tool, matching the desktop build's
**login, operation, feature surface, UI and design**. Drop the APK on a rooted
phone with MilkChoco installed, sign in with your **operator ID + password**,
tap **Connect to MilkChoco**, and the control panel loads at
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

### Injection fixes (Frida)

- **Native realm by default.** The injector previously defaulted to
  `frida-inject --realm=emulated`. But the Xigncode bypass is *Java*
  instrumentation (`Java.perform` on `XigncodeClientSystem.initialize`), and
  Frida can only reach the Java VM from the **native** realm — so the emulated
  realm silently skipped the anti-cheat bypass (the failure was swallowed by a
  `try/catch`), and a real arm/arm64 phone has no emulated realm anyway.
  Injection now runs in the native realm; `PIXEL_REALM=emulated` remains an
  explicit opt-in (with a warning that it disables the Java bypass).
- **Attach by PID.** `frida-inject` now attaches to the resolved game PID
  (`-p`) instead of by process name (`-n`), which is ambiguous and can miss
  when the cmdline differs from the package.
- **Verified injection.** The in-app injector confirms `frida-inject` is still
  alive after launch instead of blindly reporting success, and surfaces
  `inject.log` on failure (ptrace blocked, ABI mismatch, etc.) so the panel
  doesn't spin forever on an injection that never attached.
- **Loopback-only server.** The in-agent HTTP/SSE server now binds to
  `127.0.0.1`, so the cheat-control endpoints are not reachable from the LAN.

### Panel features (mobile-usable subset)

The agent (`agent/agent.ts`) and offset tables are the **same** as desktop, so
all in-game math is identical. The phone panel exposes:

- **Aimbot** — lock-style aim toward the closest in-FOV enemy
- **Aim Assist** — slows the camera toward an enemy while shooting (no lock)
- **Aim by Circle** — locks onto enemies whose head sits inside an on-screen circle
- **No Recoil** — native patch on `Spread::Recoil`
- **No Spread** — native patch on every `Spread::GetAimGapByCurState` bucket
- **Slot Kicker** — `FMatchKickUserSlot` per slot, kick-all-enemy, auto-loop, kick-by-id
- **Teleport** — per-map CTM milk / choco preset coordinates

**Kick and teleport were re-verified end-to-end** this build: the renderer's
command names and argument shapes match the agent handlers (teleport → `pos`
with `[x,y,z]`; kicks → `kick-by-slot` / `kick-all-enemy` / `kick-loop-*` /
`kick-player`), confirmed present in the bundled agent and renderer.

### How it works

- The desktop はぐるま agent is injected into MilkChoco by a bundled,
  ABI-matched `frida-inject`, attaching to the game PID in the **native realm**
  (required for the Java/Xigncode bypass).
- A **`Java.perform` Xigncode bypass** runs at the top of the agent
  (`XigncodeClientSystem.initialize` wrapped with a fake callback, `getCookie2`
  routed through, `OnHackDetected` neutered).
- An in-process HTTP/SSE server (`src/server.js`, loopback-only) shadows Frida
  `send()` / `recv()` so the WebView panel talks to the agent with no
  Node/Electron.

## Known caveats

- ESP / aim-circle **visuals** on top of the live game are not drawn (the phone
  panel is a regular WebView, not a system overlay). The underlying aim logic
  still works against the real entity list.
- Root is mandatory.
- Research / education only.
