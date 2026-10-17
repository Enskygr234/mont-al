package tr.montal.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Android'in saatte bir çalıştırdığı arka plan görevi. Telefon yeniden başlasa da devam eder. */
public class KontrolJob extends JobService {

    private static final int ID = 42;

    public static void planla(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(ID) != null) return;
        JobInfo is = new JobInfo.Builder(ID, new ComponentName(c, KontrolJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(60 * 60 * 1000L, 20 * 60 * 1000L)
            .setPersisted(true)
            .build();
        js.schedule(is);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            boolean tekrar = false;
            try {
                Kontrol.calistir(getApplicationContext());
            } catch (Exception e) {
                tekrar = true; // internet yoksa Android biraz sonra tekrar dener
            }
            jobFinished(params, tekrar);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
