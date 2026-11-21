package app.pocketflix;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Stable order: existing RSS cache slots 0 and 1 belong to Films and Anime. */
final class Catalog {
  enum Feed {
    FILMS("Feature films", "feature_films"), ANIME("Anime", "anime_miscellaneous"),
    SILENT("Silent films", "silent_films"), SCIENCE_FICTION("Sci-fi & horror", "SciFi_Horror"),
    COMEDY("Comedy films", "Comedy_Films"), ANIMATION("Animation & cartoons", "animationandcartoons");
    final String label, collection;
    Feed(String label, String collection) { this.label=label; this.collection=collection; }
    @Override public String toString() { return label; }
  }
  static final String[] ORDERS={"Recently archived", "Newest release year", "Oldest release year"};
  static final int PAGE_SIZE=40;
  static String searchUrl(Feed feed,String title,int order,int page)throws UnsupportedEncodingException {
    if(page<1 || order<0 || order>=ORDERS.length) throw new IllegalArgumentException("Invalid catalogue page");
    String scope=feed==Feed.ANIME?"(collection:anime_miscellaneous OR subject:\"anime\")":"collection:"+feed.collection;
    String q="mediatype:movies AND "+scope;
    if(!title.trim().isEmpty()) q+=" AND title:\""+title.trim().replace("\\","\\\\").replace("\"","\\\"")+"\"";
    if(order!=0) q+=" AND year:[1850 TO "+java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)+"]";
    String sort=order==0?"publicdate desc":order==1?"year desc":"year asc";
    return "https://archive.org/advancedsearch.php?output=json&fl[]=identifier&fl[]=title&fl[]=year&rows="+PAGE_SIZE+"&page="+page+"&sort[]="+URLEncoder.encode(sort,"UTF-8")+"&sort[]=identifier+asc&q="+URLEncoder.encode(q,"UTF-8");
  }
}
