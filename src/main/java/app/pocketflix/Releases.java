package app.pocketflix;

import android.content.*;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;

/** Feed refresh only exchanges RSS metadata. No path from this class starts a download. */
final class Releases {

  static final String[] LABELS = { "Feature films", "Anime" };
  static final String[] COLLECTIONS = {
    "feature_films",
    "anime_miscellaneous",
  };

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("subscriptions", 0);
  }

  static byte[] fetch(String address, int limit) throws Exception {
    URL url = new URL(address);
    for (int hop = 0; hop < 6; hop++) {
      if (
        !"https".equals(url.getProtocol()) ||
        !(
          url.getHost().equals("archive.org") ||
          url.getHost().endsWith(".archive.org")
        )
      ) throw new IOException("Unexpected source address");
      HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
      try {
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "PocketFlix/1.1 (feed reader)");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
          url = new URL(url, c.getHeaderField("Location"));
          continue;
        }
        if (code != 200) throw new IOException("Source returned HTTP " + code);
        try (
          InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
          byte[] b = new byte[16384];
          int n;
          while ((n = in.read(b)) != -1) {
            if (out.size() + n > limit) throw new IOException(
              "Source document too large"
            );
            out.write(b, 0, n);
          }
          return out.toByteArray();
        }
      } finally {
        c.disconnect();
      }
    }
    throw new IOException("Too many redirects");
  }

  static synchronized void refresh(Context c, int source) throws Exception {
    byte[] data = fetch(
      "https://archive.org/services/collection-rss.php?collection=" +
        COLLECTIONS[source],
      2 * 1024 * 1024
    );
    XmlPullParser p = Xml.newPullParser();
    p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
    p.setInput(new ByteArrayInputStream(data), null);
    JSONArray rows = new JSONArray();
    Set<String> seen = new HashSet<>();
    boolean rss = false;
    int item = 0;
    String title = "",
      link = "";
    for (
      int e = p.getEventType();
      e != XmlPullParser.END_DOCUMENT;
      e = p.nextToken()
    ) {
      if (e == XmlPullParser.DOCDECL) throw new IOException(
        "Unexpected document declaration"
      );
      if (e == XmlPullParser.START_TAG) {
        String tag = p.getName();
        if (p.getDepth() == 1 && tag.equals("rss")) rss = true;
        if (tag.equals("item")) {
          item = p.getDepth();
          title = "";
          link = "";
        } else if (item != 0 && p.getDepth() == item + 1) {
          if (tag.equals("title")) title = p.nextText().trim();
          else if (tag.equals("link")) link = p.nextText().trim();
        }
      }
      if (
        e == XmlPullParser.END_TAG &&
        item == p.getDepth() &&
        "item".equals(p.getName())
      ) {
        item = 0;
        String prefix = "https://archive.org/details/";
        if (link.startsWith(prefix)) {
          String id = link.substring(prefix.length());
          if (valid(id) && !title.isEmpty() && seen.add(id)) rows.put(
            new JSONObject().put("title", title).put("id", id)
          );
        }
        if (rows.length() >= 100) break;
      }
    }
    if (!rss) throw new IOException("Not an RSS feed");
    prefs(c)
      .edit()
      .putString("rows" + source, rows.toString())
      .putLong("time" + source, System.currentTimeMillis())
      .apply();
  }

  static boolean valid(String id) {
    return id != null && id.matches("[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,199}");
  }

  static byte[] metadata(String id) throws Exception {
    if (!valid(id)) throw new IOException("Invalid item");
    return fetch(
      "https://archive.org/download/" + id + "/" + id + "_archive.torrent",
      8 * 1024 * 1024
    );
  }

  static String hash(byte[] b) throws Exception {
    StringBuilder s = new StringBuilder();
    for (byte v : MessageDigest.getInstance("SHA-256").digest(b))
      s.append(String.format(Locale.ROOT, "%02x", v));
    return s.toString();
  }
}
