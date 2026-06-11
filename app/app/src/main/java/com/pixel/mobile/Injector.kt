package com.pixel.mobile

import android.content.Context
import android.os.Build
import java.io.File

/**
 * On-device replacement for mobile/launch.sh. Extracts the bundled agent.js +
 * ABI-matched frida-inject from the APK assets, then (as root, via `su`) drops
 * them in /data/local/tmp/pixel, ensures MilkChoco is running, and injects the
 * agent. The agent then serves the control panel on http://127.0.0.1:27345,
 * which MainActivity loads. No GameGuardian, no adb, no manual launch.sh.
 *
 * Root is mandatory: injecting into another app is impossible inside the
 * Android sandbox without it (same constraint GameGuardian has).
 */
object Injector {
    const val PKG = "com.gameparadiso.milkchoco"
    private const val VER = "17.9.10"
    private const val WORK = "/data/local/tmp/pixel"

    enum class Result { INJECTED, ALREADY_RUNNING, NO_ROOT, GAME_NOT_FOUND, ASSET_MISSING, ERROR }

    private fun abi(): String {
        for (a in Build.SUPPORTED_ABIS) {
            when {
                a.startsWith("arm64") -> return "arm64"
                a.startsWith("armeabi") -> return "arm"
                a == "x86_64" -> return "x86_64"
                a == "x86" -> return "x86"
            }
        }
        return "arm64"
    }

    private fun copyAsset(ctx: Context, name: String, dest: File): Boolean = try {
        ctx.assets.open(name).use { input -> dest.outputStream().use { input.copyTo(it) } }
        true
    } catch (e: Exception) {
        false
    }

    fun run(ctx: Context, log: (String) -> Unit): Result {
        val injectName = "frida-inject-$VER-android-${abi()}"
        val agent = File(ctx.filesDir, "agent.js")
        val inject = File(ctx.filesDir, "frida-inject")

        if (!copyAsset(ctx, "agent.js", agent)) return Result.ASSET_MISSING
        if (!copyAsset(ctx, injectName, inject)) return Result.ASSET_MISSING

        // Mirror launch.sh: copy to /data/local/tmp (root-exec friendly SELinux
        // context), skip if already injected, spawn the game if needed, then
        // inject detached so `su` returns while the agent keeps running.
        val script = """
            setenforce 0 2>/dev/null || true
            PKG="${PKG}"
            WORK="${WORK}"
            mkdir -p "\$WORK" || exit 1
            cp "${agent.absolutePath}" "\$WORK/agent.js" || exit 1
            cp "${inject.absolutePath}" "\$WORK/frida-inject" || exit 1
            chmod 755 "\$WORK/frida-inject" 2>/dev/null || true
            if pidof frida-inject >/dev/null 2>&1; then echo PIXEL_ALREADY; exit 0; fi
            PID=\$(pidof "\$PKG" 2>/dev/null || true)
            # Attach without killing — force-stopping closes the user's
            # current MilkChoco session for marginal Xigncode-bypass gain
            # (the in-agent hook still neuters future initialize / getCookie2
            # calls). If the game isn't running yet we launch it; otherwise
            # we attach to the existing PID.
            if [ -z "$PID" ]; then
              monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
                am start -n "$PKG/com.unity3d.player.UnityPlayerActivity" >/dev/null 2>&1 || true
              i=0
              while [ -z "$PID" ] && [ $i -lt 30 ]; do
                sleep 1; i=$((i+1)); PID=${s}(pidof "$PKG" 2>/dev/null || true)
              done
            fi
            [ -n "$PID" ] || { echo PIXEL_NOGAME; exit 0; }
            # Attach in the NATIVE realm (the default — no --realm flag). This
            # agent hooks Java (the Xigncode bypass via Java.perform), and Frida
            # can only reach the Java VM from the native realm; the emulated
            # realm (ARM-on-x86 NativeBridge) would silently skip the anti-cheat
            # bypass. Attach to the resolved PID (-p) rather than by name (-n),
            # which is ambiguous and can miss when the cmdline != package name.
            nohup "$WORK/frida-inject" -p "$PID" -s "$WORK/agent.js" \
              --runtime=qjs \
              >"$WORK/inject.log" 2>&1 &
            IPID=${s}!
            # Don't report success blindly: if frida-inject dies right after
            # launch (ptrace blocked by SELinux, ABI mismatch, agent syntax
            # error) the panel never comes up. Give it a moment, then confirm
            # it's still attached; otherwise surface the log so the UI can show
            # a real failure instead of spinning on a panel that never loads.
            sleep 2
            if kill -0 "$IPID" 2>/dev/null || pidof frida-inject >/dev/null 2>&1; then
              echo PIXEL_INJECTED
            else
              echo PIXEL_FAIL
              tail -n 20 "$WORK/inject.log" 2>/dev/null || true
            fi
        """.trimIndent()

        return try {
            val p = ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
            p.outputStream.use { it.write(script.toByteArray()) }
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            log(out)
            when {
                out.contains("PIXEL_ALREADY") -> Result.ALREADY_RUNNING
                out.contains("PIXEL_INJECTED") -> Result.INJECTED
                out.contains("PIXEL_NOGAME") -> Result.GAME_NOT_FOUND
                out.contains("PIXEL_FAIL") -> Result.ERROR
                else -> Result.ERROR
            }
        } catch (e: Exception) {
            log("su failed: ${e.message}")
            Result.NO_ROOT
        }
    }
}
