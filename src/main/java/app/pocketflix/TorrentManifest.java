package app.pocketflix;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only BitTorrent v1 metainfo. File indices are aria2's original 1-based indices. */
final class TorrentManifest {

  static final class Entry {

    final int index;
    final String path;
    final long size;

    Entry(int i, String p, long n) {
      index = i;
      path = p;
      size = n;
    }

    boolean video() {
      return path
        .toLowerCase(Locale.ROOT)
        .matches(".+\\.(mp4|m4v|webm|mkv|mov|avi|mpeg|mpg|3gp|ts|ogv)");
    }

    String basename() {
      return path.substring(path.lastIndexOf('/') + 1);
    }
  }

  final List<Entry> files = new ArrayList<>();
  private final byte[] data;
  private int pos, nodes;

  private IOException invalid() {
    return new IOException("Invalid or unsupported torrent metadata");
  }

  private Object value(int depth) throws IOException {
    if (depth > 32 || ++nodes > 100000 || pos >= data.length) throw invalid();
    int c = data[pos++];
    if (c == 'l' || c == 'd') {
      List<Object> l = new ArrayList<>();
      Map<String, Object> m = new HashMap<>();
      while (pos < data.length && data[pos] != 'e') {
        Object v = value(depth + 1);
        if (c == 'l') l.add(v);
        else {
          String key = text(v);
          if (m.put(key, value(depth + 1)) != null) throw invalid();
        }
      }
      if (pos == data.length) throw invalid();
      pos++;
      return c == 'l' ? l : m;
    }
    if (c == 'i') {
      int start = pos;
      while (pos < data.length && data[pos] != 'e' && pos - start < 21) pos++;
      if (pos >= data.length || data[pos++] != 'e') throw invalid();
      return Long.valueOf(
        new String(data, start, pos - start - 1, StandardCharsets.US_ASCII)
      );
    }
    if (c < '0' || c > '9') throw invalid();
    int size = c - '0';
    while (pos < data.length && data[pos] != ':') {
      int d = data[pos++] - '0';
      if (d < 0 || d > 9 || size > data.length) throw invalid();
      size = size * 10 + d;
    }
    if (pos >= data.length || size > data.length - ++pos) throw invalid();
    byte[] b = Arrays.copyOfRange(data, pos, pos + size);
    pos += size;
    return b;
  }

  private String text(Object b) throws IOException {
    if (!(b instanceof byte[])) throw invalid();
    return StandardCharsets.UTF_8.newDecoder()
      .decode(ByteBuffer.wrap((byte[]) b))
      .toString();
  }

  private String component(Object b) throws IOException {
    String s = text(b);
    if (
      s.isEmpty() ||
      s.equals(".") ||
      s.equals("..") ||
      s.length() > 240 ||
      s.matches("(?s).*[/\\\\\\p{Cntrl}].*")
    ) throw invalid();
    return s;
  }

  private String path(Map<?, ?> m, String key) throws IOException {
    if (!(m.get(key) instanceof List)) throw invalid();
    StringBuilder b = new StringBuilder();
    for (Object o : (List<?>) m.get(key)) {
      if (b.length() > 0) b.append('/');
      b.append(component(o));
      if (b.length() > 2048) throw invalid();
    }
    if (b.length() == 0) throw invalid();
    return b.toString();
  }

  private void add(
    String path,
    Map<?, ?> f,
    Set<String> seen
  ) throws IOException {
    if (
      !seen.add(path) ||
      f.containsKey("symlink path") ||
      (f.containsKey("attr") && text(f.get("attr")).contains("l"))
    ) throw invalid();
    long n = (Long) f.get("length");
    if (n < 0 || files.size() >= 4096) throw invalid();
    files.add(new Entry(files.size() + 1, path, n));
  }

  TorrentManifest(byte[] bytes) throws IOException {
    data = bytes;
    if (data.length > 8 * 1024 * 1024) throw invalid();
    try {
      Map<?, ?> torrent = (Map<?, ?>) value(0),
        info = (Map<?, ?>) torrent.get("info");
      if (
        pos != data.length || !(info.get("pieces") instanceof byte[])
      ) throw invalid();
      String root = component(info.get("name"));
      if (
        info.containsKey("name.utf-8") &&
        !root.equals(component(info.get("name.utf-8")))
      ) throw invalid();
      Set<String> seen = new HashSet<>();
      if (info.containsKey("files")) for (Object o : (List<?>) info.get(
        "files"
      )) {
        Map<?, ?> f = (Map<?, ?>) o;
        String p = path(f, "path");
        if (
          f.containsKey("path.utf-8") && !p.equals(path(f, "path.utf-8"))
        ) throw invalid();
        add(root + "/" + p, f, seen);
      }
      else add(root, info, seen);
    } catch (RuntimeException e) {
      throw invalid();
    }
  }

  List<Entry> selected(int[] indices) throws IOException {
    if (indices == null || indices.length == 0) throw new IOException(
      "Choose at least one video"
    );
    List<Entry> result = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (int i : indices) {
      if (
        i < 1 || i > files.size() || !seen.add(i) || !files.get(i - 1).video()
      ) throw invalid();
      result.add(files.get(i - 1));
    }
    return result;
  }
}
