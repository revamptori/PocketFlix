package app.pocketflix;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import org.json.*;

final class Library extends SQLiteOpenHelper {

  private static Library catalog;

  static synchronized Library get(Context context) {
    if (catalog == null) catalog = new Library(context.getApplicationContext());
    return catalog;
  }

  interface Progress {
    void copied(long bytes, long total);
  }

  final Context c;

  private Library(Context c) {
    super(c, "library", null, 2);
    this.c = c.getApplicationContext();
  }

  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
      "CREATE TABLE movies(id TEXT PRIMARY KEY, uri TEXT UNIQUE,title TEXT,mime TEXT,size INTEGER,added INTEGER,category TEXT,description TEXT,duration INTEGER,anime INTEGER NOT NULL DEFAULT 0)"
    );
  }

  public void onUpgrade(SQLiteDatabase d, int a, int b) {
    if (a < 2) {
      d.execSQL(
        "ALTER TABLE movies ADD COLUMN anime INTEGER NOT NULL DEFAULT 0"
      );
      d.execSQL(
        "UPDATE movies SET anime=1 WHERE lower(trim(category)) IN ('anime','animes')"
      );
    }
  }

  JSONArray list(boolean privateFields) throws Exception {
    JSONArray a = new JSONArray();
    try (
      Cursor q = getReadableDatabase().query(
        "movies",
        null,
        null,
        null,
        null,
        null,
        "added DESC"
      )
    ) {
      while (q.moveToNext()) {
        JSONObject o = new JSONObject();
        for (String k : q.getColumnNames())
          if (privateFields || !k.equals("uri")) o.put(
            k,
            q.getString(q.getColumnIndexOrThrow(k))
          );
        a.put(o);
      }
    }
    return a;
  }

  File file(String id, String ext) {
    return new File(c.getFilesDir(), id + ext);
  }

  void add(Uri uri, Progress progress) throws Exception {
    if (!"content".equals(uri.getScheme())) throw new IOException(
      "Choose a document"
    );
    try (
      Cursor q = getReadableDatabase().rawQuery(
        "SELECT id FROM movies WHERE uri=?",
        new String[] { uri.toString() }
      )
    ) {
      if (q.moveToFirst()) return;
    }
    String name = null;
    long size = -1;
    ContentResolver r = c.getContentResolver();
    try (
      Cursor q = r.query(
        uri,
        new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE },
        null,
        null,
        null
      )
    ) {
      if (q != null && q.moveToFirst()) {
        name = q.getString(0);
        if (!q.isNull(1)) size = q.getLong(1);
      }
    }
    if (
      name == null ||
      name.length() > 255 ||
      name.matches(".*[/\\\\\\p{Cntrl}].*")
    ) throw new IOException("Invalid filename");
    String ext = name
      .substring(name.lastIndexOf('.') + 1)
      .toLowerCase(Locale.ROOT);
    String videoMime = mimeFor(ext);
    String mime = r.getType(uri);
    if (
      videoMime == null ||
      (mime != null &&
        !mime.startsWith("video/") &&
        !mime.equals("application/octet-stream") &&
        !mime.equals("application/x-matroska"))
    ) throw new IOException("Unsupported video type");
    String id = UUID.randomUUID().toString();
    boolean linked = false,
      grant = false,
      done = false;
    try {
      try {
        r.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        );
        grant = true;
        try (
          ParcelFileDescriptor p = r.openFileDescriptor(uri, "r");
          FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(p)
        ) {
          size = in.getChannel().size();
          in.getChannel().position(0);
          linked = size > 0;
        }
      } catch (Exception ignored) {}
      if (!linked) {
        try (
          InputStream in = r.openInputStream(uri);
          OutputStream out = new FileOutputStream(file(id, ".media"))
        ) {
          byte[] b = new byte[65536];
          int n;
          long written = 0,
            last = 0;
          while ((n = in.read(b)) != -1) {
            out.write(b, 0, n);
            written += n;
            long now = SystemClock.elapsedRealtime();
            if (now - last > 250) {
              progress.copied(written, size);
              last = now;
            }
          }
        }
        size = file(id, ".media").length();
      }
      if (size <= 0) throw new IOException("Empty document");
      ContentValues v = new ContentValues();
      v.put("id", id);
      v.put("uri", uri.toString());
      v.put("title", name.substring(0, Math.max(1, name.lastIndexOf('.'))));
      v.put("size", size);
      v.put("mime", videoMime);
      v.put("category", ext.toUpperCase(Locale.ROOT));
      v.put("description", "");
      v.put("added", System.currentTimeMillis());
      fillMetadata(id, linked ? uri : null, v);
      getWritableDatabase().insertOrThrow("movies", null, v);
      done = true;
    } finally {
      if (!done) {
        file(id, ".media").delete();
        file(id, ".jpg").delete();
      }
      if (grant && (!done || !linked)) release(uri);
    }
  }

  private static String mimeFor(String ext) {
    String[] ex = {
        "mp4",
        "m4v",
        "webm",
        "mkv",
        "mov",
        "avi",
        "mpeg",
        "mpg",
        "3gp",
        "ts",
        "ogv",
      },
      mt = {
        "video/mp4",
        "video/mp4",
        "video/webm",
        "video/x-matroska",
        "video/quicktime",
        "video/x-msvideo",
        "video/mpeg",
        "video/mpeg",
        "video/3gpp",
        "video/mp2t",
        "video/ogg",
      };
    int i = Arrays.asList(ex).indexOf(ext);
    return i < 0 ? null : mt[i];
  }

  private void fillMetadata(
    String id,
    Uri uri,
    ContentValues v
  ) throws Exception {
    MediaMetadataRetriever m = new MediaMetadataRetriever();
    try {
      if (uri != null) m.setDataSource(c, uri);
      else m.setDataSource(file(id, ".media").getPath());
      v.put(
        "duration",
        m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      );
      String genre = m.extractMetadata(
        MediaMetadataRetriever.METADATA_KEY_GENRE
      );
      if (genre != null) v.put("category", genre);
      Bitmap b =
        Build.VERSION.SDK_INT < 27
          ? null
          : m.getScaledFrameAtTime(
              -1,
              MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
              360,
              220
            );
      if (b != null) try (
        OutputStream o = new FileOutputStream(file(id, ".jpg"))
      ) {
        b.compress(Bitmap.CompressFormat.JPEG, 84, o);
        b.recycle();
      }
    } catch (Exception ignored) {
    } finally {
      m.release();
    }
  }

  void importTorrent(
    File dir,
    List<TorrentManifest.Entry> entries,
    String digest,
    boolean anime
  ) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    List<File> created = new ArrayList<>();
    db.beginTransaction();
    boolean committed = false;
    try {
      for (TorrentManifest.Entry entry : entries) {
        String key = "torrent:" + digest + ":" + entry.index;
        try (
          Cursor q = db.rawQuery(
            "SELECT id FROM movies WHERE uri=?",
            new String[] { key }
          )
        ) {
          if (q.moveToFirst()) continue;
        }
        File source = new File(dir, entry.path);
        if (
          !source
            .getCanonicalPath()
            .startsWith(dir.getCanonicalPath() + File.separator) ||
          !source.isFile() ||
          source.length() != entry.size
        ) throw new IOException("Incomplete transfer: " + entry.basename());
        String id = UUID.randomUUID().toString(),
          name = entry.basename();
        int dot = name.lastIndexOf('.');
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        File dest = file(id, ".media");
        if (!source.renameTo(dest)) throw new IOException(
          "Cannot store completed video"
        );
        created.add(dest);
        created.add(file(id, ".jpg"));
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("uri", key);
        v.put("title", name.substring(0, dot));
        v.put("mime", mimeFor(ext));
        v.put("size", entry.size);
        v.put("added", System.currentTimeMillis());
        v.put("category", ext.toUpperCase(Locale.ROOT));
        v.put("description", "");
        v.put("anime", anime ? 1 : 0);
        fillMetadata(id, null, v);
        db.insertOrThrow("movies", null, v);
      }
      db.setTransactionSuccessful();
      committed = true;
    } finally {
      db.endTransaction();
      if (!committed) for (File f : created) f.delete();
    }
  }

  void release(Uri u) {
    try {
      c.getContentResolver().releasePersistableUriPermission(u, 1);
    } catch (Exception ignored) {}
  }

  void remove(JSONObject o) {
    String id = o.optString("id");
    getWritableDatabase().delete("movies", "id=?", new String[] { id });
    file(id, ".jpg").delete();
    file(id, ".media").delete();
    release(Uri.parse(o.optString("uri")));
  }
}
