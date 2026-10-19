package com.libretv.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var web: WebView
    private val PASSWORD = "libretv"

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

        // 持久化 cookie，记住登录
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
                Log.i("LTV", "[${cm.messageLevel()}] ${cm.message()}  @${cm.sourceId()}:${cm.lineNumber()}")
                return true
            }
        }

        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        web.loadUrl("http://192.168.50.210:8080")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
