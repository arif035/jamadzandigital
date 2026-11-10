package id.masjid.jamadzan;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** Server kecil di dalam TV: HP (satu Wi-Fi) membuka halaman remote.html lewat QR lalu mengubah konten. */
public class RemoteServer extends Thread {
 final Context ctx; final String token; final File dir;
 ServerSocket ss; public int port = 0; String eff = "{}";

 RemoteServer(Context c, String t) {
  ctx = c; token = t; dir = new File(c.getFilesDir(), "remote"); dir.mkdirs(); setDaemon(true);
 }

 boolean open() {
  for (int p = 8080; p < 8095; p++) {
   try { ss = new ServerSocket(p); port = p; return true; } catch (IOException e) { }
  }
  return false;
 }

 void shutdown() { try { if (ss != null) ss.close(); } catch (Exception e) { } }

 public void run() {
  if (ss == null) return;
  while (true) {
   try {
    final Socket s = ss.accept();
    new Thread(new Runnable() {
     public void run() {
      try { handle(s); } catch (Exception e) { }
      try { s.close(); } catch (Exception e) { }
     }
    }).start();
   } catch (IOException e) { return; }
  }
 }

 // ---------- status ----------
 synchronized JSONObject st() {
  try {
   File f = new File(dir, "state.json");
   if (f.exists()) return new JSONObject(new String(readAll(new FileInputStream(f)), "UTF-8"));
  } catch (Exception e) { }
  try {
   JSONObject o = new JSONObject();
   o.put("v", 0); o.put("cfg", new JSONObject()); o.put("media", new JSONArray());
   return o;
  } catch (Exception e) { return new JSONObject(); }
 }

 synchronized void save(JSONObject o) {
  try { FileOutputStream f = new FileOutputStream(new File(dir, "state.json")); f.write(o.toString().getBytes("UTF-8")); f.close(); } catch (Exception e) { }
 }

 synchronized String stateJson() {
  try { JSONObject o = st(); o.put("eff", new JSONObject(eff)); return o.toString(); } catch (Exception e) { return "{}"; }
 }

 synchronized void setEff(String j) { eff = j; }

 String ip() {
  try {
   for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
    NetworkInterface n = e.nextElement();
    if (!n.isUp() || n.isLoopback()) continue;
    for (Enumeration<InetAddress> a = n.getInetAddresses(); a.hasMoreElements();) {
     InetAddress x = a.nextElement();
     if (x instanceof Inet4Address && !x.isLoopbackAddress()) return x.getHostAddress();
    }
   }
  } catch (Exception e) { }
  return "";
 }

 String infoJson() {
  try {
   JSONObject o = new JSONObject(); String ip = ip();
   o.put("ip", ip); o.put("port", port); o.put("token", token);
   o.put("url", "http://" + ip + ":" + port + "/?t=" + token);
   return o.toString();
  } catch (Exception e) { return "{}"; }
 }

 // ---------- HTTP ----------
 void handle(Socket s) throws Exception {
  s.setSoTimeout(60000);
  InputStream in = new BufferedInputStream(s.getInputStream(), 65536); OutputStream out = s.getOutputStream();
  String line = readLine(in); if (line == null) return;
  String[] rl = line.split(" "); if (rl.length < 2) return;
  String method = rl[0], target = rl[1];
  Map<String, String> h = new HashMap<String, String>(); String l;
  while ((l = readLine(in)) != null && l.length() > 0) {
   int i = l.indexOf(':'); if (i > 0) h.put(l.substring(0, i).trim().toLowerCase(), l.substring(i + 1).trim());
  }
  String path = target, qs = ""; int q = target.indexOf('?');
  if (q >= 0) { path = target.substring(0, q); qs = target.substring(q + 1); }
  Map<String, String> qp = new HashMap<String, String>();
  for (String kv : qs.split("&")) { int e = kv.indexOf('='); if (e > 0) qp.put(kv.substring(0, e), URLDecoder.decode(kv.substring(e + 1), "UTF-8")); }

  if (method.equals("OPTIONS")) { send(out, 204, "text/plain", new byte[0], null); return; }
  if (path.startsWith("/m/")) { media(out, path.substring(3), h.get("range")); return; }
  if (path.equals("/") || path.equals("/index.html")) { send(out, 200, "text/html; charset=utf-8", asset("remote.html"), "Cache-Control: no-store\r\n"); return; }
  if (!token.equals(qp.get("t"))) { send(out, 403, "application/json", "{\"err\":\"token\"}".getBytes("UTF-8"), null); return; }

  if (path.equals("/api/state")) { send(out, 200, "application/json; charset=utf-8", stateJson().getBytes("UTF-8"), "Cache-Control: no-store\r\n"); return; }

  if (path.equals("/api/cfg") && method.equals("POST")) {
   String body = new String(readN(in, Long.parseLong(h.get("content-length"))), "UTF-8");
   JSONObject inp = new JSONObject(body);
   synchronized (this) {
    JSONObject o = st(); JSONObject cfg = o.getJSONObject("cfg");
    if (inp.has("__clear")) { cfg = new JSONObject(); o.put("cfg", cfg); }
    else {
     Iterator<String> it = inp.keys();
     while (it.hasNext()) { String k = it.next(); Object v = inp.get(k); if (String.valueOf(v).length() == 0) cfg.remove(k); else cfg.put(k, v); }
    }
    o.put("v", o.getInt("v") + 1); save(o);
   }
   send(out, 200, "application/json; charset=utf-8", stateJson().getBytes("UTF-8"), null); return;
  }

  if (path.equals("/api/up") && method.equals("POST")) {
   String kind = qp.get("kind"); if (kind == null) kind = "media";
   String id = "f" + System.currentTimeMillis() + Integer.toHexString(new Random().nextInt(65536));
   File f = new File(dir, id); long left = Long.parseLong(h.get("content-length"));
   FileOutputStream fo = new FileOutputStream(f); byte[] buf = new byte[65536];
   while (left > 0) { int n = in.read(buf, 0, (int) Math.min((long) buf.length, left)); if (n < 0) break; fo.write(buf, 0, n); left -= n; }
   fo.close();
   if (left > 0) { f.delete(); send(out, 400, "application/json", "{\"err\":\"upload\"}".getBytes("UTF-8"), null); return; }
   synchronized (this) {
    JSONObject o = st(); JSONArray arr = o.getJSONArray("media"); JSONArray nw = new JSONArray();
    for (int i = 0; i < arr.length(); i++) {
     JSONObject m = arr.getJSONObject(i);
     if (!kind.equals("media") && kind.equals(m.getString("kind"))) new File(dir, m.getString("id")).delete(); else nw.put(m);
    }
    JSONObject m = new JSONObject();
    m.put("id", id); m.put("kind", kind);
    m.put("name", qp.get("name") == null ? "file" : qp.get("name"));
    m.put("type", qp.get("type") == null ? "application/octet-stream" : qp.get("type"));
    nw.put(m); o.put("media", nw); o.put("v", o.getInt("v") + 1); save(o);
   }
   send(out, 200, "application/json; charset=utf-8", stateJson().getBytes("UTF-8"), null); return;
  }

  if (path.equals("/api/del")) {
   String id = String.valueOf(qp.get("id")).replaceAll("[^A-Za-z0-9]", "");
   synchronized (this) {
    JSONObject o = st(); JSONArray arr = o.getJSONArray("media"); JSONArray nw = new JSONArray();
    for (int i = 0; i < arr.length(); i++) { JSONObject m = arr.getJSONObject(i); if (id.equals(m.getString("id"))) new File(dir, id).delete(); else nw.put(m); }
    o.put("media", nw); o.put("v", o.getInt("v") + 1); save(o);
   }
   send(out, 200, "application/json; charset=utf-8", stateJson().getBytes("UTF-8"), null); return;
  }
  send(out, 404, "text/plain", "tidak ditemukan".getBytes("UTF-8"), null);
 }

 void media(OutputStream o, String id, String range) throws IOException {
  id = id.replaceAll("[^A-Za-z0-9]", ""); File f = new File(dir, id);
  if (!f.exists()) { send(o, 404, "text/plain", "x".getBytes("UTF-8"), null); return; }
  String type = "application/octet-stream";
  try {
   JSONArray arr = st().getJSONArray("media");
   for (int i = 0; i < arr.length(); i++) { JSONObject m = arr.getJSONObject(i); if (id.equals(m.getString("id"))) type = m.getString("type"); }
  } catch (Exception e) { }
  long len = f.length(), a = 0, b = len - 1; boolean part = false;
  if (range != null && range.startsWith("bytes=")) {
   try {
    String[] r = range.substring(6).split("-"); a = Long.parseLong(r[0]);
    if (r.length > 1 && r[1].length() > 0) b = Long.parseLong(r[1]);
    part = true;
   } catch (Exception e) { a = 0; b = len - 1; part = false; }
  }
  if (b >= len) b = len - 1;
  if (a > b) { send(o, 416, "text/plain", new byte[0], "Content-Range: bytes */" + len + "\r\n"); return; }
  String head = "HTTP/1.1 " + (part ? "206 Partial Content" : "200 OK") + "\r\nContent-Type: " + type + "\r\nContent-Length: " + (b - a + 1)
   + "\r\nAccept-Ranges: bytes\r\n" + (part ? "Content-Range: bytes " + a + "-" + b + "/" + len + "\r\n" : "")
   + "Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";
  o.write(head.getBytes("UTF-8"));
  RandomAccessFile raf = new RandomAccessFile(f, "r"); raf.seek(a);
  byte[] buf = new byte[65536]; long left = b - a + 1;
  while (left > 0) { int n = raf.read(buf, 0, (int) Math.min((long) buf.length, left)); if (n < 0) break; o.write(buf, 0, n); left -= n; }
  raf.close(); o.flush();
 }

 void send(OutputStream o, int code, String type, byte[] body, String extra) throws IOException {
  String txt = code == 200 ? "OK" : code == 204 ? "No Content" : code == 400 ? "Bad Request" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : code == 416 ? "Range Not Satisfiable" : "Error";
  String head = "HTTP/1.1 " + code + " " + txt + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
   + "\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nConnection: close\r\n"
   + (extra == null ? "" : extra) + "\r\n";
  o.write(head.getBytes("UTF-8")); o.write(body); o.flush();
 }

 byte[] asset(String n) {
  try { return readAll(ctx.getAssets().open(n)); } catch (Exception e) { return "remote.html tidak ditemukan".getBytes(); }
 }

 static String readLine(InputStream in) throws IOException {
  StringBuilder sb = new StringBuilder(); int c;
  while ((c = in.read()) != -1) { if (c == '\n') break; if (c != '\r') sb.append((char) c); if (sb.length() > 8192) break; }
  return (c == -1 && sb.length() == 0) ? null : sb.toString();
 }

 static byte[] readN(InputStream in, long n) throws IOException {
  ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192];
  while (n > 0) { int r = in.read(buf, 0, (int) Math.min((long) buf.length, n)); if (r < 0) break; b.write(buf, 0, r); n -= r; }
  return b.toByteArray();
 }

 static byte[] readAll(InputStream in) throws IOException {
  ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int r;
  while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
  in.close(); return b.toByteArray();
 }
}
