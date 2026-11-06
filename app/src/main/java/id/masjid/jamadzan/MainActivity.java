package id.masjid.jamadzan;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;

public class MainActivity extends Activity {
 WebView w; ValueCallback<Uri[]> cb;
 @Override protected void onCreate(Bundle b) {
  super.onCreate(b);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  w = new WebView(this); setContentView(w);
  WebSettings s = w.getSettings();
  s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true);
  w.setWebViewClient(new WebViewClient());
  w.setWebChromeClient(new WebChromeClient() {
   @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
    if (cb != null) cb.onReceiveValue(null);
    cb = c;
    try { startActivityForResult(p.createIntent(), 1); } catch (Exception e) { cb = null; return false; }
    return true;
   }
  });
  w.loadUrl("file:///android_asset/index.html");
 }
 @Override protected void onActivityResult(int r, int c, Intent d) {
  if (r == 1 && cb != null) { cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d)); cb = null; }
 }
 @Override public void onWindowFocusChanged(boolean f) {
  super.onWindowFocusChanged(f);
  if (f) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
 }
 @Override public void onBackPressed() { }
}
