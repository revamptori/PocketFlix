package app.pocketflix;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.*;

/** Takes ownership of the descriptor, including on a rejected range. */
final class MediaResponse {

  interface Counter {
    void count(String peer, int delta);
  }

  static Response open(
    FileInputStream in,
    String mime,
    String range,
    final String peer,
    final Counter counter
  ) throws IOException {
    boolean owned = false;
    try {
      long size = in.getChannel().size(),
        start = 0,
        end = size - 1;

      if (range != null) {
        try {
          if (!range.matches("bytes=(\\d+-\\d*|-\\d+)")) throw new Exception();
          String[] parts = range.substring(6).split("-", -1);
          if (parts[0].isEmpty()) start = Math.max(
            0,
            size - Long.parseLong(parts[1])
          );
          else {
            start = Long.parseLong(parts[0]);
            if (!parts[1].isEmpty()) end = Math.min(
              end,
              Long.parseLong(parts[1])
            );
          }
          if (start > end) throw new Exception();
        } catch (Exception e) {
          Response r = newFixedLengthResponse(
            Status.RANGE_NOT_SATISFIABLE,
            "text/plain",
            "Invalid range"
          );
          r.addHeader("Content-Range", "bytes */" + size);
          return r;
        }
      }
      in.getChannel().position(start);

      counter.count(peer, 1);
      InputStream tracked = new FilterInputStream(in) {
        boolean closed;

        public void close() throws IOException {
          if (!closed) {
            closed = true;
            try {
              super.close();
            } finally {
              counter.count(peer, -1);
            }
          }
        }
      };
      Response r = newFixedLengthResponse(
        range == null ? Status.OK : Status.PARTIAL_CONTENT,
        mime,
        tracked,
        end - start + 1
      );
      owned = true;
      r.addHeader("Accept-Ranges", "bytes");
      if (range != null) r.addHeader(
        "Content-Range",
        "bytes " + start + "-" + end + "/" + size
      );
      return r;
    } finally {
      if (!owned) in.close();
    }
  }
}
