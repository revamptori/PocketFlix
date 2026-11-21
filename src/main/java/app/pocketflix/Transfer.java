package app.pocketflix;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.Process;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;

/** One explicit selection per service run. RSS and LAN clients cannot enqueue transfers. */
public final class Transfer extends Service {

  static volatile boolean active;
  static final int NOTE = 19;
  private volatile Process process;
  private volatile boolean cancelled;

  public IBinder onBind(Intent i) {
    return null;
  }

  static String status(Context c) {
    if (
      !active && Releases.prefs(c).getBoolean("transferring", false)
    ) return "Previous download was interrupted. Select the release again to retry.";
    return Releases.prefs(c).getString("transfer", "No active download");
  }

  private Notification notification(String text) {
    PendingIntent open = PendingIntent.getActivity(
      this,
      19,
      new Intent(this, FeedScreen.class),
      PendingIntent.FLAG_IMMUTABLE
    );
    PendingIntent stop = PendingIntent.getService(
      this,
      19,
      new Intent(this, Transfer.class).setAction("cancel"),
      PendingIntent.FLAG_IMMUTABLE
    );
    return new Notification.Builder(this, "transfers")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("PocketFlix · Download")
      .setContentText(text)
      .setOngoing(true)
      .setContentIntent(open)
      .addAction(0, "Cancel download", stop)
      .build();
  }

  public int onStartCommand(final Intent intent, int flags, int id) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("cancel".equals(intent.getAction())) {
      cancelled = true;
      Process p = process;
      if (p != null) p.destroyForcibly();
      if (!active) stopSelf();
      return START_NOT_STICKY;
    }
    if (active) return START_NOT_STICKY;
    getSystemService(NotificationManager.class).createNotificationChannel(
      new NotificationChannel(
        "transfers",
        "Selected downloads",
        NotificationManager.IMPORTANCE_LOW
      )
    );
    startForeground(NOTE, notification("Starting selected download"));
    active = true;
    cancelled = false;
    Releases.prefs(this).edit().putBoolean("transferring", true).apply();
    update("Preparing selected download…");
    new Thread(
      new Runnable() {
        public void run() {
          transfer(intent);
        }
      },
      "Selected BitTorrent download"
    ).start();
    return START_NOT_STICKY;
  }

  private void update(String text) {
    Releases.prefs(this).edit().putString("transfer", text).apply();
    if (active) getSystemService(NotificationManager.class).notify(
      NOTE,
      notification(text)
    );
  }

  private void check() throws IOException {
    if (cancelled) throw new IOException("Cancelled");
  }

  private void transfer(Intent i) {
    File dir = new File(getFilesDir(), "transfer-work");
    File meta = null;
    String result;
    try {
      erase(dir);
      if (!dir.mkdir()) throw new IOException("Cannot create transfer folder");
      String hash = i.getStringExtra("hash");
      if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IOException(
        "Missing selection"
      );
      meta = new File(getCacheDir(), hash + ".torrent");
      byte[] bytes = Files.readAllBytes(meta.toPath());
      if (!Releases.hash(bytes).equals(hash)) throw new IOException(
        "Metadata changed; select again"
      );
      List<TorrentManifest.Entry> entries = new TorrentManifest(bytes).selected(
        i.getIntArrayExtra("indices")
      );
      long left = dir.getUsableSpace() - 32 * 1024 * 1024;
      StringBuilder indices = new StringBuilder();
      for (TorrentManifest.Entry e : entries) {
        if (e.size > left) throw new IOException("Not enough free storage");
        left -= e.size;
        if (indices.length() > 0) indices.append(',');
        indices.append(e.index);
      }
      check();
      if (i.getBooleanExtra("direct", false)) {
        downloadDirect(dir, entries, i.getStringExtra("item"));
      } else {
      File exe = new File(
        getApplicationInfo().nativeLibraryDir,
        "libaria2c.so"
      );
      if (!exe.canExecute()) throw new IOException(
        "Torrent engine unavailable on this device"
      );
      File ca = File.createTempFile("trusted-", ".pem", getCacheDir());
      try {
        exportRoots(ca);
        check();
        ProcessBuilder builder = new ProcessBuilder(
          exe.getAbsolutePath(),
          "--no-conf",
          "--enable-rpc=false",
          "--file-allocation=none",
          "--bt-enable-lpd=false",
          "--enable-dht=false",
          "--enable-dht6=false",
          "--listen-port=49152-65535",
          "--seed-time=0",
          "--bt-remove-unselected-file=true",
          "--auto-file-renaming=false",
          "--check-integrity=true",
          "--summary-interval=5",
          "--enable-color=false",
          "--ca-certificate=" + ca.getPath(),
          "--stop-with-process=" + android.os.Process.myPid(),
          "--dir=" + dir.getPath(),
          "--select-file=" + indices,
          meta.getPath()
        );
        builder.directory(dir);
        builder.environment().put("HOME", dir.getPath());
        process = builder.redirectErrorStream(true).start();
        check();
        update("Downloading " + entries.size() + " selected video(s)…");
        String diagnostic = "";
        try (
          BufferedReader in = new BufferedReader(
            new InputStreamReader(process.getInputStream())
          )
        ) {
          String line;
          while ((line = in.readLine()) != null) {
            check();
            if(line.contains("errorCode=") || line.contains("[ERROR]") || line.contains("Exception:")) {
              String clean=line.replaceAll("https?://\\S+", "[source]").replaceAll("/data/\\S+", "[local path]").trim();
              diagnostic=clean.substring(0,Math.min(clean.length(),250));
            }
            if (line.startsWith("[#")) {
              int end = line.indexOf(" CN:");
              update(
                "Downloading · " +
                  line
                    .substring(
                      Math.min(9, line.length()),
                      end > 9 ? end : Math.min(65, line.length())
                    )
                    .trim()
              );
            }
          }
        }
        int exit = process.waitFor();
        check();
        if (exit != 0) throw new IOException(
          "Transfer stopped (aria2 code " +
            exit +
            "). " + (diagnostic.isEmpty()?"Try direct HTTPS instead.":diagnostic)
        );
      } finally {
        ca.delete();
      }
      }
      check();
      update("Indexing completed videos…");
      Library.get(this).importTorrent(
        dir,
        entries,
        hash,
        i.getBooleanExtra("anime", false)
      );
      Dashboard.revision++;
      result = "Complete · selected videos are in your library";
    } catch (Exception e) {
      result = cancelled
        ? "Download cancelled"
        : "Download failed: " + e.getMessage();
    } finally {
      Process p = process;
      if (p != null) {
        p.destroyForcibly();
        try {
          p.waitFor();
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
      }
      process = null;
      erase(dir);
      if (meta != null) meta.delete();
    }
    Releases.prefs(this).edit().putBoolean("transferring", false).apply();
    update(result);
    new Handler(Looper.getMainLooper()).post(
      new Runnable() {
        public void run() {
          active = false;
          stopForeground(true);
          stopSelf();
        }
      }
    );
  }

  private void downloadDirect(File root, List<TorrentManifest.Entry> entries, String item) throws Exception {
    if (!Releases.valid(item)) throw new IOException("Invalid archive item");
    int index = 0;
    for (TorrentManifest.Entry e : entries) {
      check(); index++;
      String label="Direct HTTPS · file "+index+" of "+entries.size();
      update(label);
      File f=new File(root,e.path);
      if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Invalid destination");
      File parent=f.getParentFile();
      if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("Cannot create download folder");
      // The first component of multi-file metainfo is the torrent root, not an Archive file path.
      String path=e.path.substring(e.path.indexOf('/')+1);
      String url="https://archive.org/download/"+item+"/"+android.net.Uri.encode(path,"/");
      javax.net.ssl.HttpsURLConnection conn=Releases.openArchive(url);
      try {
        long length=conn.getContentLengthLong();
        if(length>=0 && length!=e.size)throw new IOException("Source file size changed. Refresh the release and retry.");
        try(InputStream in=conn.getInputStream(); OutputStream out=new FileOutputStream(f)) {
          ExactCopy.copy(in,out,e.size,new ExactCopy.Progress(){
            long last;
            public void bytes(long written)throws IOException {
              check(); long now=SystemClock.elapsedRealtime();
              if(now-last>=1000){update(label+" · "+(int)(100.0*written/e.size)+"%");last=now;}
            }
          });
        }
      } finally {conn.disconnect();}
    }
  }

  private static void exportRoots(File file) throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidCAStore");
    ks.load(null);
    try (PrintWriter out = new PrintWriter(file, "US-ASCII")) {
      Enumeration<String> names = ks.aliases();
      while (names.hasMoreElements()) {
        java.security.cert.Certificate cert = ks.getCertificate(
          names.nextElement()
        );
        if (cert != null) {
          out.println("-----BEGIN CERTIFICATE-----");
          out.print(
            android.util.Base64.encodeToString(
              cert.getEncoded(),
              android.util.Base64.DEFAULT
            )
          );
          out.println("-----END CERTIFICATE-----");
        }
      }
    }
  }

  private static void erase(File f) {
    if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory()) {
      File[] children = f.listFiles();
      if (children != null) for (File child : children) erase(child);
    }
    f.delete();
  }

  public void onDestroy() {
    cancelled = true;
    Process p = process;
    if (p != null) p.destroyForcibly();
    super.onDestroy();
  }
}
