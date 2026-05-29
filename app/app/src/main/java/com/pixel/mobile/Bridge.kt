package com.pixel.mobile

import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native bridge exposed to the local login/connect shell WebView as
 * `window.PixelNative`. Two jobs:
 *   1. Real login against the existing backend (mirrors the desktop
 *      src/data/auth.ts `loginPixel` contract) over HttpURLConnection — done
 *      natively to avoid the CORS wall a 127.0.0.1-origin fetch() would hit.
 *   2. Trigger root injection (the existing Injector) once the user logs in.
 *
 * Both calls are async (they spawn a thread and call back into JS) so the
 * WebView's JS thread is never blocked on the network or on `su`.
 */
class Bridge(private val activity: MainActivity) {

    /** Async login; result delivered to JS via window.pixelLoginResult({ok,admin,error}). */
    @JavascriptInterface
    fun login(id: String, password: String) {
        Thread {
            val json = doLogin(id, password)
            activity.evalJs("window.pixelLoginResult($json)")
        }.start()
    }

    /** User tapped "Connect": run root injection, then load the panel. */
    @JavascriptInterface
    fun connect(admin: Boolean) {
        activity.startConnect(admin)
    }

    /** Returns a JSON string {"ok":bool,"admin":bool,"error":string}. */
    private fun doLogin(id: String, password: String): String {
        val base = PIXEL_WEB_URL.trimEnd('/')
        val out = JSONObject()
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$base/api/pixel/login")
            val body = JSONObject().put("id", id).put("password", password).toString()
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val data = try { if (text.isNotEmpty()) JSONObject(text) else JSONObject() } catch (e: Exception) { JSONObject() }

            if (code in 200..299 && data.optBoolean("ok", false)) {
                out.put("ok", true).put("admin", data.optBoolean("admin", false))
            } else {
                val err = data.optString("error", "").ifEmpty { "HTTP $code" }
                out.put("ok", false).put("error", err)
            }
        } catch (e: Exception) {
            out.put("ok", false).put("error", e.message ?: "network error")
        } finally {
            conn?.disconnect()
        }
        return out.toString()
    }

    companion object {
        const val PIXEL_WEB_URL = "https://pixel-code-web.vercel.app"
    }
}
