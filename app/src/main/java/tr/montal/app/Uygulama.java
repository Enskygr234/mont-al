package tr.montal.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Uygulama çökerse hatayı kaydeder; bir sonraki açılışta ekranda gösterilir. */
public class Uygulama extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler eski = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            kaydet(this, e);
            if (eski != null) eski.uncaughtException(t, e);
        });
    }

    public static void kaydet(Context c, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String metin = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
            + Build.MANUFACTURER + " " + Build.MODEL + "\n\n" + sw;
        c.getSharedPreferences("hata", MODE_PRIVATE).edit().putString("son", metin).commit();
    }

    public static String al(Context c) {
        return c.getSharedPreferences("hata", MODE_PRIVATE).getString("son", null);
    }

    public static void temizle(Context c) {
        c.getSharedPreferences("hata", MODE_PRIVATE).edit().remove("son").commit();
    }
}
