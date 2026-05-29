package com.pixel.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Single-APK launcher with full pixel.exe parity.
 *
 * Flow: branded LOGIN (real backend auth via Bridge) -> one-button CONNECT
 * (root injection via Injector) -> the full pixel.exe control panel served by
 * the injected agent at http://127.0.0.1:27345. No GameGuardian, no PC.
 *
 * The login/connect shell is a local inlined page (it must exist before the
 * agent is injected, since the panel only goes up after injection).
 */
class MainActivity : AppCompatActivity() {

    private val panelUrl = "http://127.0.0.1:27345/"
    lateinit var web: WebView
        private set
    private val ui = Handler(Looper.getMainLooper())
    private var panelMode = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        web.addJavascriptInterface(Bridge(this), "PixelNative")
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                v: WebView?, req: android.webkit.WebResourceRequest?,
                err: android.webkit.WebResourceError?
            ) {
                // Only meaningful once we've navigated to the agent panel, and
                // only for the main document (a stray subresource error must not
                // kick a reload loop): the agent may not be up for a moment
                // right after injection — retry shortly.
                if (panelMode && req?.isForMainFrame == true) {
                    v?.loadData(WAITING_HTML, "text/html", "utf-8")
                    ui.postDelayed({ web.loadUrl(panelUrl) }, 2500)
                }
            }
        }
        setContentView(web)
        web.loadDataWithBaseURL("https://pixel-shell.local/", SHELL_HTML, "text/html", "utf-8", null)
    }

    /** Called from Bridge to deliver async results back into the shell JS. */
    fun evalJs(js: String) = ui.post { web.evaluateJavascript(js, null) }

    /** Called from Bridge when the user taps Connect (after a successful login). */
    fun startConnect(admin: Boolean) {
        Thread {
            val result = Injector.run(this) { line -> android.util.Log.i("Pixel", line) }
            ui.post {
                when (result) {
                    Injector.Result.INJECTED, Injector.Result.ALREADY_RUNNING -> {
                        panelMode = true
                        web.loadUrl(panelUrl + "?admin=" + if (admin) "1" else "0")
                    }
                    Injector.Result.NO_ROOT ->
                        connectError("Root required", "This tool reads the game's memory, which Android only allows with root. Grant the root (su) prompt, or use a rooted device / Magisk.")
                    Injector.Result.ASSET_MISSING ->
                        connectError("Build incomplete", "The agent or frida-inject binary was not bundled into this APK. Rebuild after npm run build.")
                    Injector.Result.GAME_NOT_FOUND ->
                        connectError("MilkChoco not running", "Could not start " + Injector.PKG + ". Open MilkChoco first, then tap Connect again.")
                    Injector.Result.ERROR ->
                        connectError("Injection failed", "frida-inject could not attach. Some ROMs block ptrace under SELinux. See /data/local/tmp/pixel/inject.log.")
                }
            }
        }.start()
    }

    private fun connectError(title: String, msg: String) {
        val t = title.replace("'", "\\'")
        val m = msg.replace("'", "\\'")
        evalJs("window.pixelConnectError('$t','$m')")
    }

    override fun onBackPressed() {
        if (panelMode && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        private fun page(body: String) =
            "<html><body style='background:#08080b;color:#b8b8c5;" +
            "font:16px system-ui;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0;text-align:center'>" +
            "<div style='max-width:80%'>$body</div></body></html>"

        private val WAITING_HTML =
            page("Waiting for the Pixel agent…<br><br><small>The control panel appears once injection finishes.</small>")

        // Branded local login -> connect shell. Plain HTML/CSS/JS (no build step,
        // no external fonts) styled with the desktop pixel theme. Talks to the
        // native bridge (window.PixelNative) for real auth + root injection.
        private val SHELL_HTML = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<style>
  :root{
    --bg:#08080b; --t1:#f4f4f8; --t2:#b8b8c5; --t3:#7d7d8c;
    --accent:#7c9eff; --accent2:#b794f6; --accent3:#38bdf8; --err:#f87171;
    --line:rgba(255,255,255,.10); --surf:rgba(255,255,255,.04);
  }
  *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  html,body{margin:0;height:100%}
  body{background:radial-gradient(120% 80% at 50% -10%,#12121a 0%,var(--bg) 60%);
    color:var(--t1);font:500 15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
    display:flex;align-items:center;justify-content:center;padding:24px}
  .card{width:100%;max-width:360px;text-align:center}
  .logo{width:104px;height:104px;margin:0 auto 4px;filter:drop-shadow(0 8px 36px rgba(124,158,255,.45));
    animation:float 5s ease-in-out infinite}
  @keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-7px)}}
  .word{font-weight:800;letter-spacing:.18em;font-size:30px;margin:6px 0 2px;
    background:linear-gradient(92deg,var(--accent3),var(--accent),var(--accent2));
    -webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
  .meta{color:var(--t3);font-size:12px;letter-spacing:.12em;text-transform:uppercase;margin-bottom:26px}
  input{width:100%;margin:7px 0;padding:13px 14px;text-align:center;color:var(--t1);
    background:var(--surf);border:1px solid var(--line);border-radius:12px;font-size:15px;outline:none}
  input:focus{border-color:var(--accent)}
  input::placeholder{color:var(--t3)}
  button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;cursor:pointer;
    font-weight:700;font-size:14px;letter-spacing:.06em;text-transform:uppercase;color:#0b0d12;
    background:linear-gradient(92deg,var(--accent),var(--accent2));
    box-shadow:0 8px 24px rgba(124,158,255,.30);transition:opacity .2s,transform .05s}
  button:active{transform:translateY(1px)}
  button:disabled{opacity:.55;cursor:default}
  .msg{min-height:18px;margin-top:12px;font-size:13px;color:var(--err)}
  .sub{color:var(--t2);font-size:13.5px;margin:2px 0 22px;line-height:1.6}
  .hide{display:none}
  .b-title{font-weight:700;color:var(--err);font-size:15px}
</style></head>
<body>
  <div class="card">
    <svg class="logo" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="g1" x1="56.79" y1="52.38" x2="295.99" y2="291.59" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#e7f4ff"/><stop offset=".51" stop-color="#9aedff"/><stop offset="1" stop-color="#18ccff"/>
        </linearGradient>
        <linearGradient id="g2" x1="240.29" y1="140.5" x2="462" y2="362.21" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#f8eaff"/><stop offset=".51" stop-color="#eaabff"/><stop offset="1" stop-color="#de36ff"/>
        </linearGradient>
        <linearGradient id="g3" x1="98.41" y1="251.76" x2="313.13" y2="466.48" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#fff4ec"/><stop offset=".51" stop-color="#ffeda6"/><stop offset="1" stop-color="#ffcc31"/>
        </linearGradient>
      </defs>
      <rect opacity=".8" fill="url(#g1)" x="56.79" y="52.38" width="250.94" height="250.94"/>
      <rect opacity=".8" fill="url(#g2)" x="231.79" y="132" width="232.6" height="232.6"/>
      <rect opacity=".8" fill="url(#g3)" x="90.17" y="243.53" width="225.26" height="225.26"/>
    </svg>
    <div class="word">PIXEL</div>
    <div class="meta">v1.0 &middot; MilkChoco</div>

    <div id="view-login">
      <input id="id" type="text" placeholder="ID" autocomplete="username" spellcheck="false" autocapitalize="none">
      <input id="pw" type="password" placeholder="Password" autocomplete="current-password">
      <button id="loginBtn" onclick="doLogin()">Login</button>
      <div class="msg" id="loginMsg"></div>
    </div>

    <div id="view-connect" class="hide">
      <div class="sub" id="connectSub">Logged in. Connect to MilkChoco to start.</div>
      <button id="connectBtn" onclick="doConnect()">Connect to MilkChoco</button>
      <div class="msg" id="connectMsg"></div>
    </div>
  </div>

<script>
  var admin = false;
  function el(id){return document.getElementById(id)}
  function doLogin(){
    var id = el('id').value.trim(), pw = el('pw').value;
    el('loginMsg').textContent = '';
    if(!id || !pw){ el('loginMsg').textContent = 'ID / Password'; return; }
    var b = el('loginBtn'); b.disabled = true; b.textContent = 'Signing in…';
    PixelNative.login(id, pw);
  }
  window.pixelLoginResult = function(r){
    var b = el('loginBtn'); b.disabled = false; b.textContent = 'Login';
    if(r && r.ok){
      admin = !!r.admin;
      el('view-login').classList.add('hide');
      el('view-connect').classList.remove('hide');
    } else {
      el('loginMsg').textContent = (r && r.error) ? r.error : 'Login failed';
    }
  };
  function doConnect(){
    el('connectMsg').textContent = '';
    var b = el('connectBtn'); b.disabled = true; b.textContent = 'Connecting…';
    el('connectSub').textContent = 'Grant the root (su) prompt if it appears…';
    PixelNative.connect(admin);
  }
  window.pixelConnectError = function(title, msg){
    var b = el('connectBtn'); b.disabled = false; b.textContent = 'Connect to MilkChoco';
    el('connectSub').textContent = 'Logged in. Connect to MilkChoco to start.';
    el('connectMsg').innerHTML = '<span class="b-title">'+title+'</span><br>'+msg;
  };
  document.addEventListener('keydown', function(e){
    if(e.key === 'Enter' && !el('view-login').classList.contains('hide')) doLogin();
  });
</script>
</body></html>
""".trimIndent()
    }
}
