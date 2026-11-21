package app.pocketflix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Multi-file indices include non-video files. Nothing selected must never mean 'all'. */
public class TorrentCheck {
  static String str(String text){return text.getBytes(StandardCharsets.UTF_8).length+":"+text;}
  static String entry(String name){return "d6:lengthi10e4:pathl"+str(name)+"ee";}
  static byte[] torrent(String files){return ("d4:infod5:filesl"+files+"e4:name4:Show12:piece lengthi16384e6:pieces20:01234567890123456789ee").getBytes(StandardCharsets.UTF_8);}
  static void reject(TorrentManifest m,int... indices)throws Exception{try{m.selected(indices);throw new AssertionError("Unexpected selection accepted");}catch(IOException expected){}}
  public static void main(String[] args)throws Exception{TorrentManifest m=new TorrentManifest(torrent(entry("01.mp4")+entry("art.jpg")+entry("02.mkv")));if(m.files.size()!=3||!m.selected(new int[]{3}).get(0).path.equals("Show/02.mkv")||m.selected(new int[]{1,3}).size()!=2)throw new AssertionError();reject(m);reject(m,2);reject(m,1,1);reject(m,0);reject(m,4);for(String name:new String[]{"..","/outside","a/b","a\\b"})try{new TorrentManifest(torrent(entry(name)));throw new AssertionError("Unsafe path");}catch(IOException expected){}System.out.println("PASS: episode and pack selection, no implicit downloads, non-video exclusion, original file indices and path validation");}
}
