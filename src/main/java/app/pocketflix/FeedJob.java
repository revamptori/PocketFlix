package app.pocketflix;

import android.app.job.*;
import android.content.*;

public final class FeedJob extends JobService {

  private static final int ID = 153;

  static void schedule(Context c) {
    JobScheduler j = c.getSystemService(JobScheduler.class);
    if (j.getPendingJob(ID) == null) j.schedule(
      new JobInfo.Builder(ID, new ComponentName(c, FeedJob.class))
        .setPeriodic(6 * 3600000L)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        .build()
    );
  }

  static void cancel(Context c) {
    c.getSystemService(JobScheduler.class).cancel(ID);
  }

  private volatile int generation;

  public boolean onStartJob(final JobParameters p) {
    final int gen = ++generation;
    new Thread(
      new Runnable() {
        public void run() {
          boolean failed = false;
          for (
            int i = 0;
            i < Releases.COLLECTIONS.length && gen == generation;
            i++
          ) try {
            Releases.refresh(FeedJob.this, i);
          } catch (Exception e) {
            failed = true;
          }
          if (gen == generation) jobFinished(p, failed);
        }
      },
      "RSS metadata"
    ).start();
    return true;
  }

  public boolean onStopJob(JobParameters p) {
    generation++;
    return true;
  }
}
