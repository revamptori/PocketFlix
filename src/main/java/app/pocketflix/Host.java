package app.pocketflix;

import android.app.*;
import android.content.*;
import android.os.*;

public class Host extends Service {

  static volatile MoviesHttp server;
  static volatile String error = "";
  static volatile long since;
  PowerManager.WakeLock lock;
  Handler h = new Handler();
  String previous = "";

  public IBinder onBind(Intent i) {
    return null;
  }

  Notification note(String text) {
    PendingIntent open = PendingIntent.getActivity(
      this,
      0,
      new Intent(this, Dashboard.class),
      PendingIntent.FLAG_IMMUTABLE
    );
    PendingIntent stop = PendingIntent.getService(
      this,
      1,
      new Intent(this, Host.class).setAction("stop"),
      PendingIntent.FLAG_IMMUTABLE
    );
    return new Notification.Builder(this, "host")
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("PocketFlix server")
      .setContentText(text)
      .setContentIntent(open)
      .setOngoing(true)
      .addAction(0, "Open Dashboard", open)
      .addAction(0, "Stop Server", stop)
      .build();
  }

  Runnable network = new Runnable() {
    public void run() {
      if (server == null) return;
      String ip = Lan.ip();
      String addr =
        ip == null
          ? "Waiting for a local network"
          : "http://" + ip + ":" + server.port;
      if (!addr.equals(previous)) {
        previous = addr;
        getSystemService(NotificationManager.class).notify(7, note(addr));
      }
      h.postDelayed(this, 3000);
    }
  };

  public int onStartCommand(Intent i, int flags, int id) {
    if (i != null && "stop".equals(i.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (server != null) return START_STICKY;
    getSystemService(NotificationManager.class).createNotificationChannel(
      new NotificationChannel(
        "host",
        "Movie server",
        NotificationManager.IMPORTANCE_LOW
      )
    );
    startForeground(7, note("Starting local server…"));
    try {
      SharedPreferences p = getSharedPreferences("config", 0);
      MoviesHttp s = new MoviesHttp(
        this,
        p.getInt("port", 8080),
        p.getString("secret", "")
      );
      s.start(15000, false);
      server = s;
      since = SystemClock.elapsedRealtime();
      error = "";
      lock = getSystemService(PowerManager.class).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "PocketFlix:hosting"
      );
      lock.acquire();
      network.run();
      return START_STICKY;
    } catch (Exception e) {
      error =
        "Could not start server: " +
        e.getMessage() +
        ". Try another port in Settings.";
      stopSelf();
      return START_NOT_STICKY;
    }
  }

  public void onDestroy() {
    h.removeCallbacks(network);
    MoviesHttp s = server;
    server = null;
    if (s != null) {
      s.stop();
    }
    if (lock != null && lock.isHeld()) lock.release();
    stopForeground(true);
    super.onDestroy();
  }
}
