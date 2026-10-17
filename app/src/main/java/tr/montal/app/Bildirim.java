package tr.montal.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Bildirim kanalları ve gönderimi. */
public final class Bildirim {

    public static final String KANAL_SABAH = "sabah";
    public static final String KANAL_UYARI = "uyari";

    private Bildirim() { }

    public static void kanallariKur(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel sabah = new NotificationChannel(KANAL_SABAH, "Sabah tavsiyesi", NotificationManager.IMPORTANCE_DEFAULT);
        sabah.setDescription("Her sabah ne giymen gerektiğini söyler");
        NotificationChannel uyari = new NotificationChannel(KANAL_UYARI, "Hava uyarıları", NotificationManager.IMPORTANCE_HIGH);
        uyari.setDescription("Kar, fırtına, don, kuvvetli yağış ve rüzgar");
        nm.createNotificationChannel(sabah);
        nm.createNotificationChannel(uyari);
    }

    public static boolean izinVar(Context c) {
        return Build.VERSION.SDK_INT < 33
            || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void gonder(Context c, int id, String kanal, String baslik, String metin) {
        if (!izinVar(c)) return;
        kanallariKur(c);
        Intent ac = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, ac, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, kanal)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle(baslik)
            .setContentText(metin)
            .setStyle(new Notification.BigTextStyle().bigText(metin))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF2E5E8C)
            .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
