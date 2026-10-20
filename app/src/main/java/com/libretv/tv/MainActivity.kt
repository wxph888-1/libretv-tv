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
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
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
                    if(!v) return;
                    v.style.setProperty('position','fixed','important');
                    v.style.setProperty('top','0','important');
                    v.style.setProperty('left','0','important');
                    v.style.setProperty('width','100vw','important');
                    v.style.setProperty('height','100vh','important');
                    v.style.setProperty('z-index', (window.__revealed ? '1' : '2147483647'),'important');
                    v.style.setProperty('object-fit','cover','important');
                    v.style.setProperty('background','#000','important');
                    // 所有祖先容器强制黑底，消除白边
                    var p=v.parentElement;
                    while(p && p!==document.body){ p.style.setProperty('background','#000','important'); p.style.setProperty('background-color','#000','important'); p=p.parentElement; }
                    document.documentElement.style.setProperty('background','#000','important');
                    document.body.style.setProperty('background','#000','important');
                    // 隐藏顶部标题栏
                    document.querySelectorAll('header,nav,[class*="sticky"]').forEach(function(h){
                      h.style.setProperty('display', window.__revealed ? '' : 'none','important');
                    });
                  }
                  function scan(){
                    document.querySelectorAll('video').forEach(function(v){ fit(v); });
                  }
                  new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});
                  setInterval(scan, 1000);
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

                // 点播遥控器：确认=暂停/播放，返回=唤起选集/控制栏
                val vodCtrlJs = """
                (function(){
                  if(window.__vodCtrl) return; window.__vodCtrl = true;
                  window.__revealed=false;
                  function vid(){return document.querySelector('video');}
                  function showMsg(t){
                    var m=document.getElementById('__msg');
                    if(!m){m=document.createElement('div');m.id='__msg';
                      m.style.cssText='position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:1000005;background:rgba(0,0,0,0.72);color:#fff;padding:16px 28px;border-radius:12px;font-size:26px;transition:opacity .3s;';
                      document.body.appendChild(m);}
                    m.textContent=t; m.style.opacity='1';
                    clearTimeout(m.__t); m.__t=setTimeout(function(){m.style.opacity='0';},1200);
                  }
                  window.__doReveal=function(on){
                    window.__revealed=on;
                    window.__epIdx=-1;
                    document.querySelectorAll('video').forEach(function(v){
                      v.style.zIndex = on ? '1' : '999999';
                      v.style.opacity = on ? '0.25' : '1';
                    });
                    if(on){
                      showMsg('请选择集数');
                      setTimeout(function(){
                        var eps=[...document.querySelectorAll('button,a,div,span,li')].filter(function(e){
                          if(!e.offsetParent) return false;
                          var t=(e.textContent||'').trim();
                          return t.length<=8 && e.children.length<=2 && /^第?\s*\d+\s*集?$|^EP?\d+|^正片$|^\d{1,3}$/.test(t);
                        });
                        window.__eps=eps;
                        if(eps.length){
                          // 默认锁定到当前正在播放的集
                          var cur=eps.findIndex(function(e){
                            var cls=(e.className||'')+' '+(e.parentElement?e.parentElement.className:'');
                            return /active|playing|current|selected|text-primary|bg-primary/i.test(cls) || /播放中|正在播放/.test(e.textContent||'');
                          });
                          window.__epIdx = cur>=0 ? cur : 0;
                          paintEp();
                        }
                      }, 300);
                    } else {
                      clearEpPaint();
                    }
                  };
                  function clearEpPaint(){
                    (window.__eps||[]).forEach(function(e){e.style.outline='';e.style.background='';});
                  }
                  function paintEp(){
                    clearEpPaint();
                    var eps=window.__eps||[];
                    eps.forEach(function(e,i){
                      if(i===window.__epIdx){ e.style.outline='3px solid #3b5bff'; e.style.outlineOffset='2px'; e.style.background='#3b5bff'; e.scrollIntoView({block:'nearest',behavior:'smooth'}); }
                    });
                  }
                  function fmt(t){ if(!isFinite(t)) return '00:00'; var m=Math.floor(t/60), s=Math.floor(t%60); return (m<10?'0':'')+m+':'+(s<10?'0':'')+s; }
                  function ctrlBar(){
                    var c=document.getElementById('__ctrl');
                    if(!c){ c=document.createElement('div'); c.id='__ctrl';
                      c.style.cssText='position:fixed;left:0;right:0;bottom:0;height:120px;z-index:2147483646;background:linear-gradient(transparent,rgba(0,0,0,0.85));padding:0 30px 18px;box-sizing:border-box;display:flex;flex-direction:column;justify-content:flex-end;transition:opacity .3s;';
                      c.innerHTML='<div style="display:flex;justify-content:space-between;color:#fff;font-size:22px;margin-bottom:10px;"><span id="__ctime">00:00</span><span id="__cspeed" style="color:#5ec4ff;">1.0x</span><span id="__cdur">00:00</span></div><div style="height:6px;background:rgba(255,255,255,0.25);border-radius:3px;overflow:hidden;"><div id="__cprog" style="height:100%;width:0%;background:#3b82f6;"></div></div>';
                      document.body.appendChild(c); }
                    return c;
                  }
                  function showCtrl(msg){
                    var v=vid(); if(!v) return;
                    var c=ctrlBar(); c.style.opacity='1';
                    document.getElementById('__ctime').textContent=fmt(v.currentTime);
                    document.getElementById('__cdur').textContent=fmt(v.duration);
                    document.getElementById('__cspeed').textContent=(v.playbackRate||1).toFixed(1)+'x'+(msg?'  '+msg:'');
                    document.getElementById('__cprog').style.width=(v.duration? (v.currentTime/v.duration*100):0)+'%';
                    clearTimeout(c.__t); c.__t=setTimeout(function(){c.style.opacity='0';},2200);
                  }
                  var __rates=[1,1.25,1.5,2,0.5]; var __ri=0;
                  document.addEventListener('keydown',function(e){
                    var v=vid();
                    if(location.pathname.indexOf('live')>=0) return;
                    var k=e.key||'';
                    // 选集面板打开时：方向键选集，确认切集
                    if(window.__revealed && window.__eps && window.__eps.length){
                      if(k==='ArrowUp'||k==='ArrowLeft'){ e.preventDefault(); e.stopPropagation(); window.__epIdx=(window.__epIdx-1+window.__eps.length)%window.__eps.length; paintEp(); return; }
                      if(k==='ArrowDown'||k==='ArrowRight'){ e.preventDefault(); e.stopPropagation(); window.__epIdx=(window.__epIdx+1)%window.__eps.length; paintEp(); return; }
                      if(k==='Enter'||k===' '){ e.preventDefault(); e.stopPropagation(); var t=window.__eps[window.__epIdx]; t.click(); showMsg('播放: '+(t.textContent||'').trim()); setTimeout(function(){window.__doReveal(false);},400); return; }
                    }
                    if(!v) return;
                    if(k==='Enter'||k===' '){
                      e.preventDefault(); e.stopPropagation();
                      if(v.paused){v.play(); showMsg('▶ 播放');} else {v.pause(); showMsg('⏸ 已暂停');}
                    } else if(k==='ArrowRight'){
                      e.preventDefault(); e.stopPropagation(); v.currentTime=Math.min((v.duration||0),v.currentTime+10); showCtrl('快进 +10s');
                    } else if(k==='ArrowLeft'){
                      e.preventDefault(); e.stopPropagation(); v.currentTime=Math.max(0,v.currentTime-10); showCtrl('快退 -10s');
                    } else if(k==='ArrowUp'){
                      e.preventDefault(); e.stopPropagation(); __ri=(__ri+1)%__rates.length; v.playbackRate=__rates[__ri]; showCtrl('倍速');
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
        // 点播播放中按返回：先唤起选集/控制栏，不退出；已唤起则正常后退
        web.evaluateJavascript("""
          (function(){
            var v=document.querySelector('video');
            if(!v || location.pathname.indexOf('live')>=0) return 'normal';
            if(window.__revealed){ window.__doReveal(false); return 'normal'; }
            window.__doReveal(true); return 'intercept';
          })()
        """) { res ->
            if (res.trim() != "\"intercept\"") {
                if (web.canGoBack()) web.goBack() else super.onBackPressed()
            }
        }
    }
}
