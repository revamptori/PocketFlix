package app.pocketflix;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Subscriptions show releases; the only transfer entry point is the confirmation dialog. */
public final class FeedScreen extends Activity {

  final Handler h = new Handler();
  int source = -1;
  boolean busy = false;
  TextView status, transfer;
  Button refresh, cancel, search, prev, next;
  LinearLayout pages;
  EditText queryBox;
  Spinner sortPicker;
  boolean recent;
  final List<String> cursors = new ArrayList<>();
  JSONObject catalogPage;
  String term="";
  int order, page;
  Spinner picker;
  ListView list;
  JSONArray rows = new JSONArray();

  int dp(int n) {
    return Math.round(getResources().getDisplayMetrics().density * n);
  }

  TextView text(LinearLayout l, String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setPadding(0, dp(4), 0, dp(4));
    l.addView(v);
    return v;
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(1);
    root.setPadding(dp(16), dp(12), dp(16), 0);
    root.setBackgroundColor(0xff17141f);
    setContentView(root);
    text(root, "POCKETFLIX  /  DISCOVERY", 12).setTextColor(0xffc6afff);
    text(root, "Release feeds", 29).setTypeface(null, Typeface.BOLD);
    text(
      root,
      "Browse the full collection or search by title. Nothing downloads until you choose it.",
      14
    );
    picker = new Spinner(this);
    picker.setAdapter(
      new ArrayAdapter<Catalog.Feed>(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        Releases.FEEDS
      )
    );
    root.addView(picker);
    LinearLayout bar = new LinearLayout(this);
    root.addView(bar);
    refresh = new Button(this);
    refresh.setText("Refresh feed");
    bar.addView(refresh);
    refresh.setOnClickListener(
      new View.OnClickListener() {
        public void onClick(View v) {
          if (!recent) { recent=true; catalogPage=null; cursors.clear(); showRows(); load(); } else load();
        }
      }
    );
    CheckBox updates = new CheckBox(this);
    updates.setText("Background updates");
    updates.setChecked(Releases.prefs(this).getBoolean("updates", true));
    bar.addView(updates);
    updates.setOnCheckedChangeListener(
      new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton b, boolean on) {
          Releases.prefs(FeedScreen.this)
            .edit()
            .putBoolean("updates", on)
            .apply();
          if (on) FeedJob.schedule(FeedScreen.this);
          else FeedJob.cancel(FeedScreen.this);
        }
      }
    );
    LinearLayout searchBar=new LinearLayout(this);root.addView(searchBar);
    queryBox=new EditText(this);queryBox.setSingleLine();queryBox.setHint("Search catalogue by title");
    searchBar.addView(queryBox,new LinearLayout.LayoutParams(0,-2,1));
    search=new Button(this);search.setText("Search");searchBar.addView(search);
    search.setOnClickListener(new View.OnClickListener(){public void onClick(View v){submitSearch();}});
    queryBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    queryBox.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView v,int action,android.view.KeyEvent e){if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)return false;submitSearch();return true;}});
    sortPicker=new Spinner(this);sortPicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,Catalog.ORDERS));root.addView(sortPicker);
    pages=new LinearLayout(this);root.addView(pages);
    prev=new Button(this);next=new Button(this);prev.setText("Previous");next.setText("Next");pages.addView(prev);pages.addView(next);
    prev.setOnClickListener(new View.OnClickListener(){public void onClick(View v){searchPage(term,order,page-1);}});
    next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){searchPage(term,order,page+1);}});
    pages.setVisibility(View.GONE);
    status = text(root, "", 13);
    transfer = text(root, "", 13);
    cancel = new Button(this);
    cancel.setText("Cancel transfer");
    root.addView(cancel);
    cancel.setOnClickListener(
      new View.OnClickListener() {
        public void onClick(View v) {
          startService(
            new Intent(FeedScreen.this, Transfer.class).setAction("cancel")
          );
        }
      }
    );
    list = new ListView(this);
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    list.setOnItemClickListener(
      new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> a, View v, int i, long id) {
          if (!busy && !Transfer.active) preview(rows.optJSONObject(i));
          else if (Transfer.active) msg(
            "Finish or cancel the active download first"
          );
        }
      }
    );
    queryBox.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
      public void afterTextChanged(android.text.Editable s) {}
      public void onTextChanged(CharSequence s,int start,int before,int count) {
        if(source>=0 && !busy) showRows();
      }
    });
    picker.setOnItemSelectedListener(
      new AdapterView.OnItemSelectedListener() {
        public void onNothingSelected(AdapterView<?> a) {}

        public void onItemSelected(AdapterView<?> a, View v, int i, long id) {
          if (i==source) return;
          source = i;
          recent=false; catalogPage=null; cursors.clear();term="";order=0;
          showRows();searchPage(queryBox.getText().toString().trim(),sortPicker.getSelectedItemPosition(),1);
        }
      }
    );
  }

  private void msg(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  void showRows() {
    try {
      rows = catalogPage!=null ? catalogPage.getJSONArray("rows") : !recent ? new JSONArray() : new JSONArray(
        Releases.prefs(this).getString("rows" + source, "[]")
      );
    } catch (JSONException e) {
      rows = new JSONArray();
    }
    String query=queryBox.getText().toString().trim();
    JSONArray visible=new JSONArray();
    for(int i=0;i<rows.length();i++) {
      JSONObject item=rows.optJSONObject(i);
      if(item!=null && Catalog.matchesTitle(item.optString("title"),query)) visible.put(item);
    }
    rows=visible; // Click indices must refer to the filtered records, not the original page.
    List<String> titles = new ArrayList<>();
    for (int i = 0; i < rows.length(); i++) titles.add(
      rows.optJSONObject(i).optString("title") + (rows.optJSONObject(i).optString("year").isEmpty()?"":" ("+rows.optJSONObject(i).optString("year")+")")
    );
    list.setAdapter(
      new ArrayAdapter<String>(
        this,
        android.R.layout.simple_list_item_1,
        titles
      )
    );
    pages.setVisibility(catalogPage==null?View.GONE:View.VISIBLE);
    refresh.setText(recent?"Refresh recent RSS":"Recent RSS only");
    if(!query.equals(term) || (recent && !query.isEmpty())) {
      pages.setVisibility(View.GONE);
      status.setText(rows.length()+" on this page. Tap Search for the full catalogue.");return;
    }
    if(catalogPage!=null&&!query.isEmpty()) {
      prev.setEnabled(!busy && page>1); next.setEnabled(!busy && !catalogPage.optString("next").isEmpty());
      status.setText("Page "+page+" · "+rows.length()+" title matches for “"+query+"”");return;
    }
    if(catalogPage!=null){long total=catalogPage.optLong("total");prev.setEnabled(!busy&&page>1);next.setEnabled(!busy&&!catalogPage.optString("next").isEmpty());
      status.setText(total==0?"No matching titles. Try another feed or search.":"Page "+page+" · "+total+" matches · "+Catalog.ORDERS[order]);return;}
    if(!recent){status.setText("Enter a title or search with a blank field to browse all.");return;}
    long when = Releases.prefs(this).getLong("time" + source, 0);
    status.setText(
      when == 0
        ? "No cached releases"
        : rows.length() +
            " recent RSS entries (not the full catalogue) · updated " +
            java.text.DateFormat.getDateTimeInstance().format(new Date(when))
    );
  }

  void working(boolean b) {
    busy = b;
    queryBox.setEnabled(!b); sortPicker.setEnabled(!b);search.setEnabled(!b); if(b){prev.setEnabled(false);next.setEnabled(false);}
    refresh.setEnabled(!b);
    picker.setEnabled(!b);
    list.setEnabled(!b);
  }

  interface Operation {
    Runnable perform() throws Exception;
  }

  void runTask(String s, final Operation op) {
    if (busy) return;
    working(true);
    status.setText(s);
    new Thread(
      new Runnable() {
        public void run() {
          try {
            final Runnable done = op.perform();
            h.post(
              new Runnable() {
                public void run() {
                  if (isFinishing() || isDestroyed()) return;
                  working(false);
                  showRows();
                  done.run();
                }
              }
            );
          } catch (final Exception e) {
            h.post(
              new Runnable() {
                public void run() {
                  if (isFinishing() || isDestroyed()) return;
                  working(false);
                  showRows();
                  status.setText(
                    "Unavailable: " + e.getMessage() + ". Try again later."
                  );
                }
              }
            );
          }
        }
      },
      "Release reader"
    ).start();
  }

  void submitSearch(){
    getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(queryBox.getWindowToken(),0);
    if(busy||source<0)return;
    recent=false;catalogPage=null;cursors.clear();showRows();
    searchPage(queryBox.getText().toString().trim(),sortPicker.getSelectedItemPosition(),1);
  }

  void searchPage(final String query,final int sort,final int number){
    final int feed=source;
    final String cursor=number==1?"":number<=cursors.size()?cursors.get(number-1):catalogPage.optString("next");
    if(number>1&&cursor.isEmpty())return;
    runTask("Searching archive metadata…",new Operation(){public Runnable perform()throws Exception{
      final JSONObject result=Releases.search(feed,query,sort,cursor);
      return new Runnable(){public void run(){
        // Only advance the navigation state after a successful response.
        while(cursors.size()>=number)cursors.remove(cursors.size()-1);
        cursors.add(cursor);term=query;order=sort;page=number;catalogPage=result;recent=false;showRows();
      }};
    }});
  }

  void load() {
    final int s = source;
    runTask(
      "Checking RSS metadata…",
      new Operation() {
        public Runnable perform() throws Exception {
          Releases.refresh(FeedScreen.this, s);
          return new Runnable() {
            public void run() {}
          };
        }
      }
    );
  }

  void preview(final JSONObject row) {
    if (row == null) return;
    runTask(
      "Reading torrent file list (no video download)…",
      new Operation() {
        public Runnable perform() throws Exception {
          final byte[] data = Releases.metadata(row.getString("id"));
          final TorrentManifest torrent = new TorrentManifest(data);
          final String hash = Releases.hash(data);
          File[] old = getCacheDir().listFiles();
          if (old != null) for (File f : old)
            if (
              f.getName().matches("[a-f0-9]{64}\\.torrent") &&
              f.lastModified() < System.currentTimeMillis() - 86400000L
            ) f.delete();
          File cache = new File(getCacheDir(), hash + ".torrent");
          try (FileOutputStream out = new FileOutputStream(cache)) {
            out.write(data);
          }
          return new Runnable() {
            public void run() {
              choose(row.optString("title"), row.optString("id"), hash, torrent);
            }
          };
        }
      }
    );
  }

  void choose(String title, final String item, final String hash, TorrentManifest torrent) {
    final List<TorrentManifest.Entry> videos = new ArrayList<>();
    for (TorrentManifest.Entry e : torrent.files)
      if (e.video() && e.size > 0) videos.add(e);
    if (videos.isEmpty()) {
      msg("This release has no supported video files");
      return;
    }
    String[] labels = new String[videos.size()];
    final boolean[] chosen = new boolean[labels.length];
    for (int n = 0; n < labels.length; n++) {
      TorrentManifest.Entry e = videos.get(n);
      labels[n] =
        e.path +
        " · " +
        android.text.format.Formatter.formatShortFileSize(this, e.size);
    }
    final AlertDialog d = new AlertDialog.Builder(this)
      .setTitle(title)
      .setMultiChoiceItems(
        labels,
        chosen,
        new DialogInterface.OnMultiChoiceClickListener() {
          public void onClick(DialogInterface d, int which, boolean on) {
            chosen[which] = on;
          }
        }
      )
      .setNegativeButton("Cancel", null)
      .setNeutralButton(Releases.FEEDS[source]==Catalog.Feed.ANIME?"All episodes":"Select all", null)
      .setPositiveButton("Review", null)
      .create();
    d.show();
    d.getButton(-3).setOnClickListener(
      new View.OnClickListener() {
        public void onClick(View v) {
          for (int n = 0; n < chosen.length; n++) {
            chosen[n] = true;
            d.getListView().setItemChecked(n, true);
          }
        }
      }
    );
    d.getButton(-1).setOnClickListener(
      new View.OnClickListener() {
        public void onClick(View v) {
          ArrayList<Integer> ids = new ArrayList<>();
          long total = 0;
          for (int n = 0; n < chosen.length; n++) if (chosen[n]) {
            TorrentManifest.Entry e = videos.get(n);
            if (e.size > Long.MAX_VALUE - total) {
              msg("Selection too large");
              return;
            }
            ids.add(e.index);
            total += e.size;
          }
          if (ids.isEmpty()) {
            msg("Choose one or more videos first");
            return;
          }
          final int[] indices = new int[ids.size()];
          for (int n = 0; n < indices.length; n++) indices[n] = ids.get(n);
          d.dismiss();
          final CheckBox anime = new CheckBox(FeedScreen.this);
          anime.setText("Add to Anime collection");
          anime.setChecked(Releases.FEEDS[source]==Catalog.Feed.ANIME);
          LinearLayout options = new LinearLayout(FeedScreen.this);
          options.setOrientation(1);
          final CheckBox torrentMode = new CheckBox(FeedScreen.this);
          torrentMode.setText("Use BitTorrent instead of direct HTTPS");
          options.addView(torrentMode);
          options.addView(anime);
          new AlertDialog.Builder(FeedScreen.this)
            .setTitle("Download " + indices.length + " video(s)?")
            .setMessage(
              android.text.format.Formatter.formatShortFileSize(
                FeedScreen.this,
                total
              ) +
                " selected. Direct download uses Internet Archive without peers. Optional BitTorrent contacts peers who can see your IP and may receive pieces while downloading. Only download files you have permission to share."
            )
            .setView(options)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
              "Download",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                  if (Transfer.active) {
                    msg("Another download is active");
                    return;
                  }
                  startForegroundService(
                    new Intent(FeedScreen.this, Transfer.class)
                      .putExtra("hash", hash)
                      .putExtra("indices", indices)
                      .putExtra("item", item)
                      .putExtra("direct", !torrentMode.isChecked())
                      .putExtra("anime", anime.isChecked())
                  );
                }
              }
            )
            .show();
        }
      }
    );
  }

  final Runnable tick = new Runnable() {
    public void run() {
      transfer.setText(Transfer.status(FeedScreen.this));
      cancel.setVisibility(Transfer.active ? View.VISIBLE : View.GONE);
      h.postDelayed(this, 1000);
    }
  };

  public void onResume() {
    super.onResume();
    h.post(tick);
  }

  public void onPause() {
    h.removeCallbacks(tick);
    super.onPause();
  }
}
