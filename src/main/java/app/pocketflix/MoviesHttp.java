package app.pocketflix;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

final class MoviesHttp extends NanoHTTPD implements MediaResponse.Counter {

  final Context c;
  final Library lib;
  final int port;
  final Map<String, Long> sessions = new HashMap<>();
  final Map<String, Integer> streams = new HashMap<>();
  final String secret;
  long nextLogin;

  MoviesHttp(Context c, int p, String secret) {
    super("0.0.0.0", p);
    this.c = c;
    lib = Library.get(c);
    port = p;
    this.secret = secret;
    setAsyncRunner(
      new DefaultAsyncRunner() {
        @Override
        public void exec(ClientHandler handler) {
          if (getRunning().size() >= 32) handler.close();
          else super.exec(handler);
        }
      }
    );
  }

  static String verifier(String pass, String salt) throws Exception {
    return Base64.encodeToString(
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(
          new PBEKeySpec(
            pass.toCharArray(),
            salt.getBytes("UTF-8"),
            100000,
            256
          )
        )
        .getEncoded(),
      2
    );
  }

  static String hash(String pass) throws Exception {
    String salt = UUID.randomUUID().toString();
    return salt + ":" + verifier(pass, salt);
  }

  protected ClientHandler createClientHandler(Socket s, InputStream i) {
    if (!Lan.allow(s)) try {
      s.close();
    } catch (IOException ignored) {}
    return super.createClientHandler(s, i);
  }

  public synchronized void count(String ip, int d) {
    int n = (streams.containsKey(ip) ? streams.get(ip) : 0) + d;
    if (n < 1) streams.remove(ip);
    else streams.put(ip, n);
  }

  final Map<String, Long> watching = new HashMap<>();

  synchronized void heartbeat(String ip) {
    if (watching.size() < 1024 || watching.containsKey(ip)) watching.put(
      ip,
      SystemClock.elapsedRealtime()
    );
  }

  synchronized int clients() {
    Set<String> peers = new HashSet<>(streams.keySet());
    long now = SystemClock.elapsedRealtime();
    Iterator<Map.Entry<String, Long>> it = watching.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Long> e = it.next();
      if (now - e.getValue() > 30000) it.remove();
      else peers.add(e.getKey());
    }
    return peers.size();
  }

  Response msg(Status s, String body) {
    return newFixedLengthResponse(s, "text/plain", body);
  }

  synchronized boolean authorized(String cookie) {
    if (secret.isEmpty()) return true;
    if (cookie != null) for (String item : cookie.split(";")) {
      String key = item.trim();
      if (
        key.startsWith("pf=") &&
        sessions.containsKey(key.substring(3)) &&
        sessions.get(key.substring(3)) > SystemClock.elapsedRealtime()
      ) return true;
    }
    return false;
  }

  synchronized Response login(IHTTPSession s) throws Exception {
    long now = SystemClock.elapsedRealtime();
    if (now < nextLogin) return msg(
      Status.TOO_MANY_REQUESTS,
      "Try again shortly"
    );
    nextLogin = now + 800;
    String raw = s.getHeaders().get("x-pocketflix-key");
    String[] v = secret.split(":");
    if (
      raw == null ||
      raw.length() > 1024 ||
      v.length != 2 ||
      !MessageDigest.isEqual(
        v[1].getBytes("UTF-8"),
        verifier(new String(Base64.decode(raw, 0), "UTF-8"), v[0]).getBytes(
          "UTF-8"
        )
      )
    ) return msg(Status.UNAUTHORIZED, "Incorrect password");
    if (sessions.size() >= 256) sessions.clear();
    String token = UUID.randomUUID().toString();
    sessions.put(token, now + 28800000);
    Response r = msg(Status.OK, "OK");
    r.addHeader(
      "Set-Cookie",
      "pf=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800"
    );
    return r;
  }

  public Response serve(IHTTPSession s) {
    Response r;
    try {
      r = route(s);
    } catch (Exception e) {
      r = msg(
        Status.NOT_FOUND,
        "Media unavailable. Check the original file on the host phone."
      );
    }
    r.addHeader("X-Content-Type-Options", "nosniff");
    r.addHeader("Cache-Control", "no-store");
    r.addHeader("Referrer-Policy", "no-referrer");
    r.addHeader("X-Frame-Options", "DENY");
    r.closeConnection(true);
    return r;
  }

  Response route(IHTTPSession s) throws Exception {
    String host = s.getHeaders().get("host");
    boolean ok = false;
    for (java.net.InterfaceAddress a : Lan.links()) {
      String ip = a.getAddress().getHostAddress();
      if (
        (ip + ":" + port).equals(host) || (port == 80 && ip.equals(host))
      ) ok = true;
    }
    if (!ok) return msg(
      Status.FORBIDDEN,
      "Open the LAN URL shown on the phone"
    );
    String u = s.getUri();
    if (u.equals("/session") && s.getMethod() == Method.POST) return login(s);
    if (s.getMethod() != Method.GET && s.getMethod() != Method.HEAD) return msg(
      Status.METHOD_NOT_ALLOWED,
      "Read only"
    );
    if (!authorized(s.getHeaders().get("cookie"))) {
      if (!u.equals("/")) return msg(Status.UNAUTHORIZED, "Password required");
      return asset("login.html");
    }
    if (u.equals("/watching")) {
      heartbeat(s.getRemoteIpAddress());
      return msg(Status.NO_CONTENT, "");
    }
    if (u.equals("/")) return asset("index.html");
    if (u.equals("/library")) return newFixedLengthResponse(
      Status.OK,
      "application/json",
      lib.list(false).toString()
    );
    if (!u.matches("/(media|poster)/[a-f0-9-]{36}")) return msg(
      Status.NOT_FOUND,
      "Not found"
    );
    String id = u.substring(u.lastIndexOf('/') + 1);
    try (
      android.database.Cursor q = lib
        .getReadableDatabase()
        .rawQuery("SELECT uri,mime,size FROM movies WHERE id=?", new String[] {
          id,
        })
    ) {
      if (!q.moveToFirst()) return msg(Status.NOT_FOUND, "Not found");
      if (u.startsWith("/poster/")) {
        File f = lib.file(id, ".jpg");
        return newFixedLengthResponse(
          Status.OK,
          "image/jpeg",
          new FileInputStream(f),
          f.length()
        );
      }
      File f = lib.file(id, ".media");
      FileInputStream in = f.exists()
        ? new FileInputStream(f)
        : new ParcelFileDescriptor.AutoCloseInputStream(
            c
              .getContentResolver()
              .openFileDescriptor(Uri.parse(q.getString(0)), "r")
          );
      return MediaResponse.open(
        in,
        q.getString(1),
        s.getHeaders().get("range"),
        s.getRemoteIpAddress(),
        this
      );
    }
  }

  Response asset(String n) throws Exception {
    InputStream in = c.getAssets().open(n);
    return newFixedLengthResponse(
      Status.OK,
      "text/html; charset=utf-8",
      in,
      in.available()
    );
  }
}
