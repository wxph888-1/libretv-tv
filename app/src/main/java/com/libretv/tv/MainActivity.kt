package com.libretv.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.CustomViewCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var web: WebView
    private val PASSWORD = "libretv"

    // 视频全屏状态
    private var customView: View? = null
    private var customCallback: CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.loadsImagesAutomatically = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val js = """
                (function(){
                  if(window.__autoLoginTimer) return;
                  var tries = 0;
                  window.__autoLoginTimer = setInterval(function(){
                    tries++;
                    var p = document.querySelector('input[type=password]');
                    if(!p || tries > 30){ clearInterval(window.__autoLoginTimer); return; }
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    setter.call(p, '$PASSWORD');
                    p.dispatchEvent(new Event('input', {bubbles:true}));
                    p.dispatchEvent(new Event('change', {bubbles:true}));
                    setTimeout(function(){
                      var btn = document.querySelector('button[type=submit]');
                      if(!btn){ var btns = document.querySelectorAll('button'); for(var i=0;i<btns.length;i++){ if(btns[i].offsetParent!==null){ btn=btns[i]; break; } } }
                      if(btn){ btn.click(); }
                      clearInterval(window.__autoLoginTimer);
                    }, 400);
                  }, 400);
                })();
                """
                view?.evaluateJavascript(js, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.i("LTV", "[${cm.messageLevel()}] ${cm.message()}")
                return true
            }

            // 视频进入全屏
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden(); return
                }
                customView = view
                customCallback = callback
                web.visibility = View.GONE
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                (window.decorView as ViewGroup).addView(view, params)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }

            // 视频退出全屏
            override fun onHideCustomView() {
                if (customView == null) return
                (window.decorView as ViewGroup).removeView(customView)
                customView = null
                web.visibility = View.VISIBLE
                customCallback?.onCustomViewHidden()
                customCallback = null
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }

        web.loadUrl("http://192.168.50.210:8080")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 全屏播放时按返回先退出全屏
        if (customView != null) {
            web.webChromeClient?.onHideCustomView()
            return
        }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
