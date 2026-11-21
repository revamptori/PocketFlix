package app.pocketflix;
import java.io.*;

/** Keep truncated or oversized responses out of the library. Caller owns the streams. */
final class ExactCopy {
  interface Progress { void bytes(long n) throws IOException; }
  static void copy(InputStream in, OutputStream out, long expected, Progress progress) throws IOException {
    byte[] buffer=new byte[65536]; long written=0; int n;
    while((n=in.read(buffer))!=-1){
      if(n>expected-written)throw new IOException("Source sent an unexpected file size");
      out.write(buffer,0,n); written+=n; progress.bytes(written);
    }
    if(written!=expected)throw new IOException("Incomplete HTTPS download. Check your connection and retry.");
  }
}
