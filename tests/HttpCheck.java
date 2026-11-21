package app.pocketflix;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback integration test of the exact response factory used with SAF descriptors. */
public final class HttpCheck
  extends NanoHTTPD
  implements MediaResponse.Counter
{

  final File file;
  final AtomicInteger active = new AtomicInteger();

  HttpCheck(File f) {
    super("127.0.0.1", 0);
    file = f;
  }

  public void count(String peer, int delta) {
    active.addAndGet(delta);
  }

  public Response serve(IHTTPSession s) {
    try {
      return MediaResponse.open(
        new FileInputStream(file),
        "video/mp4",
        s.getHeaders().get("range"),
        "test",
        this
      );
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  void check(
    String range,
    int status,
    String expected,
    String contentRange,
    String method
  ) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(
      "http://127.0.0.1:" + getListeningPort() + "/"
    ).openConnection();
    try {
      c.setRequestMethod(method);
      if (range != null) c.setRequestProperty("Range", range);
      if (
        c.getResponseCode() != status ||
        !Objects.equals(contentRange, c.getHeaderField("Content-Range"))
      ) throw new AssertionError(range + " status/headers");
      if (status < 400) {
        if (!"video/mp4".equals(c.getContentType())) throw new AssertionError(
          "MIME"
        );
        try (InputStream in = c.getInputStream()) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          int b;
          while ((b = in.read()) != -1) out.write(b);
          if (!expected.equals(out.toString("UTF-8"))) throw new AssertionError(
            range + " bytes"
          );
        }
      }
    } finally {
      c.disconnect();
    }
  }

  public static void main(String[] args) throws Exception {
    File f = File.createTempFile("pocketflix-test", ".mp4");
    try (FileOutputStream out = new FileOutputStream(f)) {
      out.write("0123456789".getBytes("UTF-8"));
    }
    final HttpCheck s = new HttpCheck(f);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    s.start();
    try {
      s.check(null, 200, "0123456789", null, "GET");
      s.check("bytes=7-", 206, "789", "bytes 7-9/10", "GET");
      s.check("bytes=1-3", 206, "123", "bytes 1-3/10", "GET");
      s.check("bytes=-2", 206, "89", "bytes 8-9/10", "GET");
      s.check("bytes=9-100", 206, "9", "bytes 9-9/10", "GET");
      s.check(null, 200, "", null, "HEAD");
      for (String r : new String[] {
        "bytes=10-",
        "bytes=5-3",
        "bytes=-0",
        "bytes=x",
        "bytes=1-2,5-6",
      })
        s.check(r, 416, null, "bytes */10", "GET");
      List<Future<?>> jobs = new ArrayList<>();
      for (int i = 0; i < 48; i++) jobs.add(
        pool.submit(
          new Callable<Void>() {
            public Void call() throws Exception {
              s.check("bytes=2-5", 206, "2345", "bytes 2-5/10", "GET");
              return null;
            }
          }
        )
      );
      for (Future<?> job : jobs) job.get();
    } finally {
      pool.shutdownNow();
      s.stop();
      f.delete();
    }
    if (s.active.get() != 0) throw new AssertionError("Descriptor leak");
    System.out.println(
      "PASS: 200, HEAD, MIME, forward/backward, suffix, clamping, 416, 48 concurrent requests and cleanup"
    );
  }
}
