package app.pocketflix;

import java.net.URLDecoder;

public class CatalogCheck {
  public static void main(String[] args) throws Exception {
    if (!Catalog.matchesTitle("The Dangers-IN My Heart (2023)", "dangers in my heart")
        || Catalog.matchesTitle("Another series", "dangers in my heart")
        || !Catalog.matchesTitle("映画", "映画") || !Catalog.matchesTitle("Any", "  "))
      throw new AssertionError("Title filter mismatch");
    String first = Catalog.searchUrl(Catalog.Feed.ANIME,"some title",0,"");
    if (first.contains("cursor=") || !URLDecoder.decode(first,"UTF-8").contains("title:\"some title\"")) throw new AssertionError(first);
    String next = Catalog.searchUrl(Catalog.Feed.ANIME,"some title",0,"a+b/c==");
    if (!next.equals(first+"&cursor=a%2Bb%2Fc%3D%3D")) throw new AssertionError(next);
    System.out.println("PASS: full collection title query and opaque continuation cursor");
  }
}
