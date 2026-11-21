package app.pocketflix;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;

/** Test-only catalog: serves the unmodified client and production byte-stream handler. */
public final class BrowserFixture
  extends NanoHTTPD
  implements MediaResponse.Counter
{

  final File movie, page;

  BrowserFixture(File f, File p) {
    super("127.0.0.1", 0);
    movie = f;
    page = p;
  }

  public void count(String peer, int n) {}

  public Response serve(IHTTPSession s) {
    try {
      String uri = s.getUri();
      if (uri.equals("/")) return newFixedLengthResponse(
        Response.Status.OK,
        "text/html",
        new FileInputStream(page),
        page.length()
      );
      if (uri.equals("/library")) return newFixedLengthResponse(
        Response.Status.OK,
        "application/json",
        "[{\"title\":\"Test pattern\",\"id\":\"test\",\"category\":\"Test\",\"added\":" +
          System.currentTimeMillis() +
          ",\"duration\":20000,\"size\":" +
          movie.length() +
          "}]"
      );
      if (uri.equals("/media/test")) return MediaResponse.open(
        new FileInputStream(movie),
        "video/mp4",
        s.getHeaders().get("range"),
        "test",
        this
      );
      return newFixedLengthResponse(
        Response.Status.NOT_FOUND,
        "text/plain",
        ""
      );
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  public static void main(String[] args) throws Exception {
    BrowserFixture f = new BrowserFixture(new File(args[0]), new File(args[1]));
    f.start();
    System.out.println(f.getListeningPort());
    try {
      System.in.read();
    } finally {
      f.stop();
    }
  }
}
