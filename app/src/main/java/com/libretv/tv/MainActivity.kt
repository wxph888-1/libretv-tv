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
    private var customCallback: WebChromeClient.CustomViewCallback? = null

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

                // 进入播放页后，发现视频就自动铺满全屏
                val fitJs = """
                (function(){
                  if(window.__videoFit) return; window.__videoFit = true;
                  function fit(v){
                    if(!v || v.__fitted) return;
                    v.__fitted = true;
                    v.style.position='fixed';
                    v.style.top='0'; v.style.left='0';
                    v.style.width='100vw'; v.style.height='100vh';
                    v.style.zIndex='999999';
                    v.style.objectFit='contain';
                    v.style.background='#000';
                    try{ v.requestFullscreen && v.requestFullscreen(); }catch(e){}
                  }
                  function scan(){
                    var v = document.querySelector('video');
                    if(v) fit(v);
                  }
                  new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});
                  setInterval(scan, 1500);
                })();
                """
                view?.evaluateJavascript(fitJs, null)

                // 自动启用直播源：找"全部启用"按钮点掉，再勾选未启用的直播源
                val liveJs = """
                (function(){
                  if(window.__liveAuto) return; window.__liveAuto = true;
                  function scan(){
                    // 1) 点"全部启用"按钮
                    var btns = document.querySelectorAll('button,div,span,a');
                    for(var i=0;i<btns.length;i++){
                      var t = (btns[i].textContent||'').trim();
                      if(t==='全部启用' && btns[i].offsetParent!==null){ btns[i].click(); }
                    }
                    // 2) 找未勾选的直播源 checkbox，点它的父label/容器
                    var cbs = document.querySelectorAll('input[type=checkbox]');
                    for(var j=0;j<cbs.length;j++){
                      var cb = cbs[j];
                      if(cb.checked) continue;
                      var box = cb.closest('div,label,li');
                      var txt = box ? (box.textContent||'') : '';
                      if(txt.indexOf('央卫')>=0 || txt.indexOf('live.m3u')>=0 || txt.indexOf('直播')>=0){
                        var clickable = cb.closest('label') || box;
                        if(clickable) clickable.click();
                        else cb.click();
                      }
                    }
                  }
                  setInterval(scan, 2000);
                })();
                """
                view?.evaluateJavascript(liveJs, null)

                // 确认键呼出频道列表面板（遥控器选台）
                val chListJs = """
                (function(){
                  if(window.__chList) return; window.__chList = true;
                  var panel=null, items=[], focusIdx=-1, open=false;
                  function rows(){
                    return [...document.querySelectorAll('div.group.cursor-pointer, [class*="cursor-pointer"][class*="items-center"]')].filter(function(r){
                      var t=(r.textContent||'').trim();
                      return t.length>1 && t.length<24 && /直播$/.test(t);
                    });
                  }
                  function close(){
                    if(panel){ panel.remove(); panel=null; }
                    items=[]; focusIdx=-1; open=false;
                  }
                  function openPanel(){
                    if(open) { close(); return; }
                    var rs=rows(); if(!rs.length) return;
                    open=true;
                    panel=document.createElement('div');
                    panel.id='__chpanel';
                    panel.style.cssText='position:fixed;right:0;top:0;height:100vh;width:34vw;min-width:340px;z-index:1000002;background:rgba(8,10,14,0.92);backdrop-filter:blur(8px);border-left:1px solid rgba(255,255,255,0.1);overflow-y:auto;padding:60px 10px 20px;box-sizing:border-box;';
                    var h=document.createElement('div');
                    h.textContent='频道列表（'+rs.length+'）  按返回关闭';
                    h.style.cssText='position:sticky;top:0;color:#9fb3ff;font-size:18px;padding:6px 10px 14px;background:transparent;';
                    panel.appendChild(h);
                    items=[];
                    rs.forEach(function(r,i){
                      var name=(r.textContent||'').replace(/直播$/,'').trim();
                      var it=document.createElement('div');
                      it.textContent=(i+1)+'. '+name;
                      it.style.cssText='padding:11px 14px;color:#d8dbe0;font-size:20px;border-radius:8px;cursor:pointer;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;';
                      it.addEventListener('click',function(){ r.click(); close(); });
                      it.__row=r;
                      panel.appendChild(it);
                      items.push(it);
                    });
                    document.body.appendChild(panel);
                    focusIdx=0; paint();
                  }
                  function paint(){
                    items.forEach(function(it,i){
                      if(i===focusIdx){ it.style.background='#3b5bff'; it.style.color='#fff'; it.style.transform='translateX(-6px)'; }
                      else { it.style.background='transparent'; it.style.color='#d8dbe0'; it.style.transform='none'; }
                    });
                    if(items[focusIdx]) items[focusIdx].scrollIntoView({block:'nearest'});
                  }
                  document.addEventListener('keydown',function(e){
                    var k=e.key||'';
                    if(!open){
                      if(k==='Enter'||k===' ') {
                        if(location.pathname.indexOf('live')>=0){ e.preventDefault(); openPanel(); }
                      }
                      return;
                    }
                    if(k==='ArrowUp'){ e.preventDefault(); focusIdx=(focusIdx-1+items.length)%items.length; paint(); }
                    else if(k==='ArrowDown'){ e.preventDefault(); focusIdx=(focusIdx+1)%items.length; paint(); }
                    else if(k==='Enter'||k===' '){ e.preventDefault(); if(items[focusIdx]){ items[focusIdx].__row.click(); close(); } }
                    else if(k==='Escape'||k==='Backspace'){ e.preventDefault(); close(); }
                  }, true);
                })();
                """
                view?.evaluateJavascript(chListJs, null)

                // 点播遥控器：确认=暂停/播放，上键=呼出控制栏/选集
                val vodCtrlJs = """
                (function(){
                  if(window.__vodCtrl) return; window.__vodCtrl = true;
                  var revealed=false;
                  function vid(){return document.querySelector('video');}
                  function showMsg(t){
                    var m=document.getElementById('__msg');
                    if(!m){m=document.createElement('div');m.id='__msg';
                      m.style.cssText='position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:1000005;background:rgba(0,0,0,0.72);color:#fff;padding:16px 28px;border-radius:12px;font-size:26px;transition:opacity .3s;';
                      document.body.appendChild(m);}
                    m.textContent=t; m.style.opacity='1';
                    clearTimeout(m.__t); m.__t=setTimeout(function(){m.style.opacity='0';},1200);
                  }
                  function setReveal(on){
                    revealed=on;
                    document.querySelectorAll('video').forEach(function(v){
                      v.style.zIndex = on ? '1' : '999999';
                      v.style.opacity = on ? '0.25' : '1';
                    });
                  }
                  document.addEventListener('keydown',function(e){
                    var v=vid(); if(!v) return;
                    if(location.pathname.indexOf('live')>=0) return; // 直播页交给频道列表
                    var k=e.key||'';
                    if(k==='Enter'||k===' '){
                      e.preventDefault(); e.stopPropagation();
                      if(v.paused){v.play(); showMsg('▶ 播放');} else {v.pause(); showMsg('⏸ 已暂停');}
                    } else if(k==='ArrowUp'){
                      e.preventDefault();
                      setReveal(!revealed);
                      showMsg(revealed?'控制栏/选集已显示':'继续播放');
                    } else if(k==='ArrowDown'){
                      if(revealed){ e.preventDefault(); setReveal(false); }
                    }
                  }, true);
                })();
                """
                view?.evaluateJavascript(vodCtrlJs, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.i("LTV", "[${cm.messageLevel()}] ${cm.message()}")
                return true
            }

            // 视频进入全屏
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
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
