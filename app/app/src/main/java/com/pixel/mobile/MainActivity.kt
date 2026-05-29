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
            "<html><body style='background:#f4ede0;color:#5d564b;" +
            "font:16px \"IBM Plex Sans\",system-ui,sans-serif;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0;text-align:center'>" +
            "<div style='max-width:80%'>$body</div></body></html>"

        private val WAITING_HTML =
            page("はぐるまエージェントを待機中…<br><br><small>注入が完了すると操作パネルが表示されます。</small>")

        // Branded local login -> connect shell. Plain HTML/CSS/JS styled with the
        // desktop はぐるま "workshop" theme (charcoal on cream, square corners,
        // hairline borders, orange accent, gear logo). Talks to the native bridge
        // (window.PixelNative) for real operator auth + root injection.
        private val SHELL_HTML = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<link rel="preconnect" href="https://fonts.googleapis.com" crossorigin>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700;800&family=IBM+Plex+Mono:wght@400;500;600&family=Noto+Sans+JP:wght@500;700&display=swap" media="print" onload="this.media='all'">
<style>
  :root{
    --bg:#f4ede0; --bg2:#fbf6ec; --t1:#1f1a14; --t2:#5d564b; --t3:#8a8275;
    --accent:#c45a2c; --accent-deep:#9c3f17; --gold:#e8a838; --err:#b14638;
    --line:#d8cbb0; --surf:#fbf6ec;
  }
  *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  html,body{margin:0;height:100%}
  body{
    background:
      linear-gradient(transparent 23px, rgba(31,26,20,.045) 24px),
      linear-gradient(90deg, transparent 23px, rgba(31,26,20,.045) 24px),
      var(--bg);
    background-size:24px 24px,24px 24px,auto;
    color:var(--t1);font:500 15px/1.5 'IBM Plex Sans',system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
    display:flex;align-items:center;justify-content:center;padding:24px}
  .card{width:100%;max-width:360px;text-align:center}
  .logo{width:104px;height:104px;margin:0 auto 6px;
    animation:spin 24s linear infinite}
  @keyframes spin{to{transform:rotate(360deg)}}
  .word{font-family:'Noto Sans JP','IBM Plex Sans',sans-serif;font-weight:700;
    letter-spacing:.08em;font-size:34px;margin:4px 0 4px;color:var(--t1)}
  .meta{font-family:'IBM Plex Mono',monospace;color:var(--t3);font-size:11px;
    letter-spacing:.22em;text-transform:uppercase;margin-bottom:26px}
  input{width:100%;margin:6px 0;padding:13px 14px;text-align:center;color:var(--t1);
    background:var(--surf);border:1px solid var(--line);border-radius:0;font-size:15px;outline:none;
    font-family:'IBM Plex Mono',monospace}
  input:focus{border-color:var(--accent);box-shadow:0 0 0 3px rgba(196,90,44,.12)}
  input::placeholder{color:var(--t3)}
  button{width:100%;margin-top:14px;padding:14px;border:1px solid var(--accent-deep);border-radius:0;cursor:pointer;
    font-weight:700;font-size:13px;letter-spacing:.10em;text-transform:uppercase;color:#fbf6ec;
    background:var(--accent);transition:background .15s,transform .05s}
  button:hover{background:var(--accent-deep)}
  button:active{transform:translateY(1px)}
  button:disabled{opacity:.55;cursor:default}
  .msg{min-height:18px;margin-top:12px;font-size:13px;color:var(--err)}
  .sub{color:var(--t2);font-size:13.5px;margin:2px 0 22px;line-height:1.6}
  .hide{display:none}
  .b-title{font-weight:700;color:var(--err);font-size:15px}
</style></head>
<body>
  <div class="card">
    <svg class="logo" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="big" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#4f86e3"/><stop offset="1" stop-color="#2a5db8"/>
        </linearGradient>
        <linearGradient id="small" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#f0b94a"/><stop offset="1" stop-color="#c88a1a"/>
        </linearGradient>
      </defs>
      <g transform="translate(72 78)" fill="url(#big)">
        <circle cx="0" cy="0" r="48"/>
        <g>
          <rect x="-7" y="-66" width="14" height="22" rx="3"/><rect x="-7" y="44" width="14" height="22" rx="3"/>
          <rect x="-66" y="-7" width="22" height="14" rx="3"/><rect x="44" y="-7" width="22" height="14" rx="3"/>
          <g transform="rotate(45)">
            <rect x="-7" y="-66" width="14" height="22" rx="3"/><rect x="-7" y="44" width="14" height="22" rx="3"/>
            <rect x="-66" y="-7" width="22" height="14" rx="3"/><rect x="44" y="-7" width="22" height="14" rx="3"/>
          </g>
        </g>
      </g>
      <circle cx="72" cy="78" r="22" fill="#fbf6ec"/>
      <g transform="translate(140 130)" fill="url(#small)">
        <circle cx="0" cy="0" r="28"/>
        <g>
          <rect x="-4" y="-38" width="8" height="13" rx="2"/><rect x="-4" y="25" width="8" height="13" rx="2"/>
          <rect x="-38" y="-4" width="13" height="8" rx="2"/><rect x="25" y="-4" width="13" height="8" rx="2"/>
          <g transform="rotate(45)">
            <rect x="-4" y="-38" width="8" height="13" rx="2"/><rect x="-4" y="25" width="8" height="13" rx="2"/>
            <rect x="-38" y="-4" width="13" height="8" rx="2"/><rect x="25" y="-4" width="13" height="8" rx="2"/>
          </g>
        </g>
      </g>
      <circle cx="140" cy="130" r="11" fill="#fbf6ec"/>
    </svg>
    <div class="word">はぐるま</div>
    <div class="meta">v1.56.0 &middot; Workshop &middot; MilkChoco</div>

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
