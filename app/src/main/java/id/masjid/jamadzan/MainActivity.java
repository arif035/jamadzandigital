package id.masjid.jamadzan;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;

public class MainActivity extends Activity {
 WebView w; ValueCallback<Uri[]> cb; RemoteServer srv;

 public class Bridge {
  @JavascriptInterface public String info() { return srv == null ? "{}" : srv.infoJson(); }
  @JavascriptInterface public String state() { return srv == null ? "{}" : srv.stateJson(); }
  @JavascriptInterface public void eff(String j) { if (srv != null) srv.setEff(j); }
 }

 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

  SharedPreferences sp = getSharedPreferences("jam", MODE_PRIVATE);
  String tk = sp.getString("tk", null);
  if (tk == null) {
   SecureRandom r = new SecureRandom();
   tk = String.format("%08x%04x", r.nextInt(), r.nextInt(65536));
   sp.edit().putString("tk", tk).apply();
  }
  srv = new RemoteServer(this, tk);
  if (srv.open()) srv.start();

  w = new WebView(this); setContentView(w);
  WebSettings s = w.getSettings();
  s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
  CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
  w.addJavascriptInterface(new Bridge(), "Android");
  w.setWebViewClient(new WebViewClient());
  w.setWebChromeClient(new WebChromeClient() {
   @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
    if (cb != null) cb.onReceiveValue(null);
    cb = c;
    try { startActivityForResult(p.createIntent(), 1); } catch (Exception e) { cb = null; return false; }
    return true;
   }
  });
  String html;
  try {
   InputStream in = getAssets().open("index.html");
   ByteArrayOutputStream o = new ByteArrayOutputStream();
   byte[] buf = new byte[8192]; int n;
   while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
   in.close();
   html = o.toString("UTF-8");
  } catch (Exception e) { html = "<h1>Gagal memuat aplikasi</h1>"; }
  w.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null);
 }
 @Override protected void onActivityResult(int r, int c, Intent d) {
  if (r == 1 && cb != null) { cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d)); cb = null; }
 }
 @Override public void onWindowFocusChanged(boolean f) {
  super.onWindowFocusChanged(f);
  if (f) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
 }
 @Override protected void onDestroy() { if (srv != null) srv.shutdown(); super.onDestroy(); }
 @Override public void onBackPressed() { }
}
