package app.pocketflix;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public class Dashboard extends Activity {

  int bg = 0xff101018,
    panel = 0xff201e2c,
    white = 0xfff7f5ff,
    dim = 0xffb5afc4,
    violet = 0xffb7a1ff;
  LinearLayout root, body;
  TextView status, addr, metrics, progress;
  Button toggle, copy;
  Library lib;
  Handler h = new Handler();
  SharedPreferences prefs;
  boolean animeOnly;
  int tab;
  static volatile int revision;
  int drawnRevision;
  static volatile String importing = "";
  static final java.util.concurrent.ExecutorService queue =
    java.util.concurrent.Executors.newSingleThreadExecutor();
  long bytes;
  int movies;

  int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    lib = Library.get(this);
    prefs = getSharedPreferences("config", 0);
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    if (Releases.prefs(this).getBoolean("updates", true)) FeedJob.schedule(
      this
    );
    draw();
    if (
      Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0
    ) requestPermissions(
      new String[] { "android.permission.POST_NOTIFICATIONS" },
      9
    );
  }

  Runnable tick = new Runnable() {
    public void run() {
      update();
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

  GradientDrawable shape(int color) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(15));
    return g;
  }

  LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    return l;
  }

  TextView text(LinearLayout l, String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    l.addView(t);
    return t;
  }

  Button btn(LinearLayout l, String s, final Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(white);
    b.setBackgroundTintList(
      android.content.res.ColorStateList.valueOf(0xff433758)
    );
    l.addView(b);
    b.setOnClickListener(
      new View.OnClickListener() {
        public void onClick(View v) {
          r.run();
        }
      }
    );
    return b;
  }

  LinearLayout card() {
    LinearLayout l = col();
    l.setBackground(shape(panel));
    l.setPadding(dp(16), dp(12), dp(16), dp(12));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(10);
    body.addView(l, p);
    return l;
  }

  void draw() {
    drawnRevision = revision;
    root = col();
    root.setBackgroundColor(bg);
    root.setPadding(dp(16), dp(12), dp(16), dp(8));
    setContentView(root);
    text(root, "◈  PocketFlix", 22, white).setTypeface(null, Typeface.BOLD);
    ScrollView sc = new ScrollView(this);
    root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    body = col();
    sc.addView(body);
    text(body, "SERVER STUDIO    /    LOCAL COLLECTION", 11, violet);
    text(
      body,
      new String[] { "Overview", "Movie library", "Settings" }[tab],
      34,
      white
    ).setTypeface(null, Typeface.BOLD);
    try {
      JSONArray a = lib.list(true);
      bytes = 0;
      movies = a.length();
      for (int j = 0; j < movies; j++) bytes += a
        .getJSONObject(j)
        .optLong("size");
      if (tab == 0) {
        LinearLayout l = card();
        text(l, "BROADCAST CONTROL", 12, violet);
        status = text(l, "", 20, white);
        toggle = btn(
          l,
          "",
          new Runnable() {
            public void run() {
              if (Host.server == null) startForegroundService(
                new Intent(Dashboard.this, Host.class)
              );
              else stopService(new Intent(Dashboard.this, Host.class));
            }
          }
        );
        LinearLayout n = card();
        text(n, "LOCAL NETWORK", 12, violet);
        addr = text(n, "", 18, white);
        addr.setTextIsSelectable(true);
        copy = btn(
          n,
          "Copy website URL",
          new Runnable() {
            public void run() {
              String ip = Lan.ip();
              if (ip != null) {
                getSystemService(
                  android.content.ClipboardManager.class
                ).setPrimaryClip(ClipData.newPlainText("PocketFlix", url(ip)));
                toast("URL copied");
              }
            }
          }
        );
        metrics = text(card(), "", 16, white);
        upload();
      } else if (tab == 1) {
        btn(
          body,
          "Browse release feeds",
          new Runnable() {
            public void run() {
              startActivity(new Intent(Dashboard.this, FeedScreen.class));
            }
          }
        );
        upload();
        CheckBox filter = new CheckBox(this);
        filter.setText("Anime collection only");
        filter.setChecked(animeOnly);
        body.addView(filter);
        filter.setOnCheckedChangeListener(
          new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
              animeOnly = on;
              draw();
            }
          }
        );
        int shown = 0;
        if (movies == 0) text(
          card(),
          "Your collection is empty. Select videos to make them available to nearby browsers.",
          16,
          dim
        );
        for (int j = 0; j < movies; j++) {
          final JSONObject o = a.getJSONObject(j);
          if (animeOnly && o.optInt("anime") == 0) continue;
          shown++;
          LinearLayout l = card();
          text(
            l,
            o.optString("category") + "   /   " + size(o.optLong("size")),
            12,
            violet
          );
          text(l, o.optString("title"), 22, white);
          btn(
            l,
            "Manage movie",
            new Runnable() {
              public void run() {
                manage(o);
              }
            }
          );
        }
        if (animeOnly && shown == 0 && movies > 0) text(
          card(),
          "No anime yet. Mark a title in Manage movie or select Anime when downloading.",
          16,
          dim
        );
      } else settings();
    } catch (Exception e) {
      toast(e.toString());
    }
    LinearLayout nav = new LinearLayout(this);
    root.addView(nav);
    for (int j = 0; j < 3; j++) {
      final int n = j;
      Button b = btn(
        nav,
        new String[] { "Overview", "Library", "Settings" }[j],
        new Runnable() {
          public void run() {
            tab = n;
            draw();
          }
        }
      );
      b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
      b.setTextColor(j == tab ? violet : dim);
    }
    update();
  }

  String size(long b) {
    return android.text.format.Formatter.formatShortFileSize(this, b);
  }

  String url(String ip) {
    return "http://" + ip + ":" + prefs.getInt("port", 8080);
  }

  void update() {
    if (tab != 2 && drawnRevision != revision) {
      draw();
      return;
    }
    if (tab != 2 && progress != null) progress.setText(importing);
    if (tab != 0 || toggle == null) return;
    MoviesHttp s = Host.server;
    String ip = Lan.ip();
    toggle.setText(s == null ? "▶   Start server" : "■   Stop server");
    status.setText(
      s == null
        ? Host.error.isEmpty()
          ? "●  Offline"
          : Host.error
        : "●  Server online"
    );
    addr.setText(ip == null ? "Waiting for Wi-Fi or Ethernet" : url(ip));
    copy.setEnabled(ip != null);
    metrics.setText(
      movies +
        " movies   ·   " +
        size(bytes) +
        " indexed\n" +
        (s == null ? 0 : s.clients()) +
        " streaming devices   ·   " +
        (s == null
          ? "Not broadcasting"
          : android.text.format.DateUtils.formatElapsedTime(
              (SystemClock.elapsedRealtime() - Host.since) / 1000
            ) + " uptime")
    );
  }

  void upload() {
    btn(
      body,
      "＋   Upload movies",
      new Runnable() {
        public void run() {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
          startActivityForResult(i, 1);
        }
      }
    );
    progress = text(body, importing, 13, dim);
  }

  public void onActivityResult(int req, int res, Intent i) {
    super.onActivityResult(req, res, i);
    if (req != 1 || res != RESULT_OK || i == null) return;
    final ArrayList<Uri> items = new ArrayList<>();
    if (i.getClipData() != null) {
      for (int j = 0; j < i.getClipData().getItemCount(); j++) items.add(
        i.getClipData().getItemAt(j).getUri()
      );
    } else if (i.getData() != null) items.add(i.getData());
    queue.execute(
      new Runnable() {
        public void run() {
          int j = 0;
          String err = "";
          for (Uri u : items) {
            importing = "Indexing " + ++j + " of " + items.size() + "…";
            try {
              final String prefix = importing;
              lib.add(
                u,
                new Library.Progress() {
                  public void copied(long written, long total) {
                    importing =
                      prefix +
                      "   " +
                      size(written) +
                      (total > 0 ? " / " + size(total) : "");
                  }
                }
              );
              revision++;
            } catch (Exception e) {
              err = "Could not index: " + e.getMessage();
            }
          }
          importing = err.isEmpty() ? "Library updated" : err;
          runOnUiThread(
            new Runnable() {
              public void run() {
                if (!isDestroyed()) draw();
              }
            }
          );
        }
      }
    );
  }

  EditText field(LinearLayout l, String label, String value) {
    text(l, label, 13, violet);
    EditText e = new EditText(this);
    e.setSingleLine();
    e.setText(value);
    l.addView(e);
    return e;
  }

  void manage(JSONObject o) {
    LinearLayout l = col();
    EditText t = field(l, "Title", o.optString("title")),
      g = field(l, "Category", o.optString("category")),
      d = field(l, "Description", o.optString("description"));
    final CheckBox anime = new CheckBox(this);
    anime.setText("Anime collection");
    anime.setChecked(o.optInt("anime") != 0);
    l.addView(anime);
    new AlertDialog.Builder(Dashboard.this)
      .setTitle("Movie details")
      .setView(l)
      .setPositiveButton(
        "Save",
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface x, int y) {
            String title = t.getText().toString().trim();
            if (title.isEmpty()) return;
            ContentValues v = new ContentValues();
            v.put("title", title);
            v.put("category", g.getText().toString());
            v.put("description", d.getText().toString());
            v.put("anime", anime.isChecked() ? 1 : 0);
            lib
              .getWritableDatabase()
              .update("movies", v, "id=?", new String[] { o.optString("id") });
            draw();
          }
        }
      )
      .setNeutralButton(
        "Remove",
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface x, int y) {
            new AlertDialog.Builder(Dashboard.this)
              .setMessage(
                "Remove this movie from PocketFlix? The original document will not be deleted."
              )
              .setPositiveButton(
                "Remove",
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface a, int b) {
                    lib.remove(o);
                    draw();
                  }
                }
              )
              .setNegativeButton("Cancel", null)
              .show();
          }
        }
      )
      .setNegativeButton("Cancel", null)
      .show();
  }

  void settings() {
    LinearLayout l = card();
    EditText p = field(
      l,
      "Listening port",
      String.valueOf(prefs.getInt("port", 8080))
    );
    p.setInputType(2);
    EditText k = field(l, "New password (blank keeps current)", "");
    k.setInputType(129);
    k.setFilters(new android.text.InputFilter[] {
      new android.text.InputFilter.LengthFilter(128),
    });
    CheckBox clear = new CheckBox(this);
    clear.setText("Disable password protection");
    l.addView(clear);
    text(
      l,
      prefs.getString("secret", "").isEmpty()
        ? "Access: trusted LAN"
        : "Access: password protected",
      14,
      dim
    );
    btn(
      l,
      "Save settings",
      new Runnable() {
        public void run() {
          try {
            if (Host.server != null) {
              toast("Stop the server before changing settings");
              return;
            }
            int n = Integer.parseInt(p.getText().toString());
            if (n < 1024 || n > 65535) throw new Exception(
              "Use port 1024–65535"
            );
            SharedPreferences.Editor e = prefs.edit().putInt("port", n);
            if (clear.isChecked()) e.remove("secret");
            else if (k.length() > 0) e.putString(
              "secret",
              MoviesHttp.hash(k.getText().toString())
            );
            e.apply();
            toast("Settings saved");
            draw();
          } catch (Exception e) {
            toast(e.getMessage());
          }
        }
      }
    );
    text(
      card(),
      "Use trusted Wi-Fi only. HTTP does not encrypt passwords or movies. Do not forward this port on your router.\n\nHosting uses battery. Android or manufacturer power controls can stop the service; reopen PocketFlix to restart it.",
      15,
      dim
    );
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }
}
