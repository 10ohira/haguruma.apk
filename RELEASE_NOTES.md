# Pixel v1.56.0

The first **aim-focused, anti-cheat-aware** build of the standalone Pixel APK.
Drop the APK on a rooted phone with MilkChoco installed, log in, tap **Connect
to MilkChoco**, and the aim panel loads at `http://127.0.0.1:27345`.

## Install

1. Download **`Pixel.apk`** below.
2. Install on a **rooted** phone (Magisk / KernelSU OK) that already has
   MilkChoco (`com.gameparadiso.milkchoco`).
3. Open **Pixel** → log in with your key → tap **Connect to MilkChoco** →
   approve `su`. The panel loads once injection finishes.

`pixel-mobile-<abi>.zip` (also attached) is the manual-injection fallback:
unzip on the phone and run `su sh launch.sh` — no APK needed.

## What's in the panel

The cheats list was pared down to **aim-only**, with all advanced math
exposed as plain-language knobs (Follow Speed, Target Range, Skip
Teammates / Dead Players):

- **Aimbot** — lock-style aim toward the closest in-FOV enemy
- **Aim Assist** — slows the camera toward an enemy while shooting (no lock)
- **Aim by Circle** — locks onto enemies whose head sits inside an on-screen circle
- **No Recoil** — native patch on `Spread::Recoil`
- **No Spread** — native patch on every `Spread::GetAimGapByCurState` bucket
- **Slot Kicker** — `FMatchKickUserSlot` per slot, kick-all, auto-loop
- **Teleport** — per-map CTM milk / choco preset coordinates

## What changed under the hood

### Smooth aim follow (no more snap-snap)

All three aim functions share a single helper:

```ts
factor = 1 - exp(-rate * dt)
nyaw   = yaw + (targetYaw - yaw) * factor
```

`rate` scales with the Speed slider; `dt` is the actual frame delta. The
camera always closes the angular gap over multiple frames — fast when far,
easing as it closes in. There is no setting that produces a single-frame
snap, even at Speed 100.

### Xigncode bypass moved into the agent

The desktop build (`pixel.exe`) installs the Xigncode hook as a *pre-script*
before spawning MilkChoco. The standalone APK can only `attach`, so the same
`Java.perform` block now lives at the top of `agent/agent.ts`:

- `XigncodeClientSystem.initialize` → calls the original with a fake
  `Callback` whose `OnHackDetected`, `OnLog`, and `SendPacket` are no-ops.
- `XigncodeClientSystem.getCookie2` → routes the original value through so the
  login flow stays intact.

### `--realm=emulated` by default

`launch.sh` and `Injector.kt` now pass **`--realm=emulated`** to
`frida-inject`, running the agent's JS context inside Frida's Stalker-based
emulator. Xigncode's memory and module scans no longer see the Frida runtime
even though the agent is attached.

Every Frida API the agent uses (`Java.perform`, `Module.findExportByName`,
`NativeFunction`, `Interceptor.attach`, `Memory.protect/alloc`,
`Socket.listen`, `setInterval`) is realm-compatible — audited line by line.
The launcher probes `frida-inject --help` first and silently drops the flag
on older builds that don't know it. Set `PIXEL_REALM=native` to force the old
behavior.

### Inject button no longer kills the game

The previous build force-stopped MilkChoco on every inject so the Xigncode
hook would catch the very first `initialize` call. That also killed any
in-progress match the moment the user tapped **Connect**. The cold restart is
now opt-in via `PIXEL_RESTART=1`; by default the agent just attaches to the
running game. The in-agent hook still neuters all subsequent
`initialize` / `getCookie2` / `OnHackDetected` calls.

## Known caveats

- ESP / aim-circle **visuals** on top of the live game are not drawn (the
  phone panel is a regular WebView, not a system overlay). The underlying aim
  logic still works against the real entity list.
- Root is mandatory.
- Research / education only.
