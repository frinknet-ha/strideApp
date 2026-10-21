package dev.stride.hud

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/** Independent media WebView. It has no reference to the motor controller or Stride bridge. */
@SuppressLint("SetJavaScriptEnabled")
class MediaPanel(private val activity: Activity, private val layoutChanged: (Boolean, Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var lastInput = SystemClock.elapsedRealtime()
    private var expanded = false
    private var canExpand = false
    private var currentVideo: String? = null
    private var consumeGesture = false
    private val query = EditText(activity).apply {
        setSingleLine(true)
        hint = "Search YouTube"
        setText("treadmill walking scenery")
        setTextColor(Color.WHITE)
        setBackgroundColor(0xff071226.toInt())
        textSize = 20f
        setPadding(16, 6, 16, 6)
        setHintTextColor(0xff9cadcc.toInt())
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }
    private val toolbar = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(0xff101f40.toInt())
    }
    val view = object : LinearLayout(activity) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                consumeGesture = expanded
                activity()
            }
            if (consumeGesture) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) consumeGesture = false
                return true
            }
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setBackgroundColor(Color.BLACK)
    }
    private val browser = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        setBackgroundColor(Color.BLACK)
        // UI-only events. Remote content cannot access any treadmill method.
        addJavascriptInterface(PlaybackEvents(), "MediaPlayback")
        webChromeClient = WebChromeClient() // Never grant a player fullscreen over STOP.
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(web: WebView, url: String?) {
                if (Uri.parse(url ?: "").host !in listOf("www.youtube.com", "m.youtube.com", "youtube.com")) return
                web.evaluateJavascript("""
                  (function(){
                    if(window._strideMediaLinks)return;window._strideMediaLinks=true;
                    document.addEventListener('click',function(e){
                      var a=e.target.closest&&e.target.closest('a[href]');if(!a)return;
                      var u=new URL(a.href,location.href),id=u.searchParams.get('v');
                      if(u.pathname==='/watch'&&/^[A-Za-z0-9_-]{11}$/.test(id||'')){
                        e.preventDefault();e.stopImmediatePropagation();MediaPlayback.openVideo(id);
                      }
                    },true);
                  })();
                """.trimIndent(), null)
            }
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                val host = uri.host?.lowercase() ?: return true
                if (uri.scheme != "https") return true
                if (host in listOf("www.youtube.com", "m.youtube.com", "youtube.com", "youtu.be")) {
                    val id = if (host == "youtu.be") uri.pathSegments.firstOrNull()
                        else if (uri.path == "/watch") uri.getQueryParameter("v")
                        else if (uri.pathSegments.firstOrNull() in listOf("shorts", "live")) uri.pathSegments.getOrNull(1)
                        else null
                    if (id != null && id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                        play(id)
                        return true
                    }
                    playing = false
                    restore()
                    currentVideo = null
                    return false
                }
                if (host in listOf("accounts.google.com", "consent.youtube.com", "consent.google.com")) return false
                Toast.makeText(activity, "Only YouTube pages open in this panel", Toast.LENGTH_SHORT).show()
                return true
            }
        }
    }
    private val idleCheck = object : Runnable {
        override fun run() {
            if (view.visibility == View.VISIBLE && playing && canExpand && !expanded &&
                SystemClock.elapsedRealtime() - lastInput >= 10000L) {
                expanded = true
                toolbar.visibility = View.GONE
                layoutChanged(true, true)
            }
            handler.postDelayed(this, 250)
        }
    }

    init {
        toolbar.addView(query, LinearLayout.LayoutParams(0, dp(68), 1f))
        fun button(label: String, action: () -> Unit) = Button(activity).apply {
            text = label
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xff142344.toInt())
            minWidth = dp(110)
            setOnClickListener { action() }
        }
        toolbar.addView(button("Search", ::search))
        toolbar.addView(button("Results") { search() })
        toolbar.addView(button("Close") { hide() })
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        view.addView(toolbar, LinearLayout.LayoutParams(-1, dp(72)))
        view.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        handler.post(idleCheck)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    fun show() {
        view.visibility = View.VISIBLE
        activity()
        layoutChanged(true, false)
        if (browser.url == null || browser.url == "about:blank") {
            if (query.text.toString().isBlank()) query.setText("treadmill walking scenery")
            search()
        }
    }

    fun hide() {
        if (view.visibility == View.GONE) return
        playing = false
        browser.loadUrl("about:blank") // No hidden background playback.
        currentVideo = null
        expanded = false
        toolbar.visibility = View.VISIBLE
        view.visibility = View.GONE
        layoutChanged(false, false)
    }

    fun activity() { lastInput = SystemClock.elapsedRealtime(); restore() }

    private fun restore() {
        if (!expanded) return
        expanded = false
        toolbar.visibility = View.VISIBLE
        layoutChanged(view.visibility == View.VISIBLE, false)
    }

    fun workoutState(session: Int, alert: Boolean) {
        val finished = session == Session.SUMMARY
        canExpand = session != Session.PAUSED && !finished && !alert
        if (!canExpand) restore()
        if (alert || finished) hide()
    }

    fun bounds(left: Double, top: Double, width: Double, height: Double, parentWidth: Int, parentHeight: Int) {
        if (listOf(left, top, width, height).any { !it.isFinite() }) return
        if (left < 0 || top < 0 || width <= 0 || height <= 0 || left + width > 1.01 || top + height > 0.85) return
        val safeHeight = minOf((height * parentHeight).toInt(), (parentHeight * 0.77).toInt() - (top * parentHeight).toInt())
        if (safeHeight <= 0) return
        view.layoutParams = FrameLayout.LayoutParams((width * parentWidth).toInt(), safeHeight).apply {
            leftMargin = (left * parentWidth).toInt()
            topMargin = (top * parentHeight).toInt()
        }
    }

    private fun search() {
        val words = query.text.toString().trim().take(160)
        if (words.isBlank()) return
        activity()
        playing = false
        currentVideo = null
        (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(query.windowToken, 0)
        query.clearFocus()
        browser.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(words))
    }

    private fun play(id: String) {
        playing = false
        activity()
        currentVideo = id
        val template = activity.assets.open("media/player.html").bufferedReader().use { it.readText() }
        // YouTube's documented WebView client identity is based on the app package.
        val base = "https://${activity.packageName}/"
        browser.loadDataWithBaseURL(base, template.replace("__VIDEO_ID__", id).replace("__APP_ORIGIN__", base.trimEnd('/')), "text/html", "UTF-8", null)
    }

    private inner class PlaybackEvents {
        @JavascriptInterface fun openVideo(videoId: String) {
            if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
            handler.post { if (view.visibility == View.VISIBLE) play(videoId) }
        }
        @JavascriptInterface fun state(videoId: String, state: Int) {
            handler.post {
                if (videoId != currentVideo || view.visibility != View.VISIBLE) return@post
                val nowPlaying = state == 1
                if (nowPlaying != playing) { playing = nowPlaying; activity() }
            }
        }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        browser.destroy()
    }
}
