package tr.montal.app;

import android.content.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Arka planda saatte bir çalışır: tahmini yeniler, gerekiyorsa bildirim atar. */
public final class Kontrol {

    private Kontrol() { }

    public static void calistir(Context c) throws Exception {
        Ayarlar a = new Ayarlar(c);
        String json = Hava.tahminGetir(a.lat(), a.lon());
        a.tahmin(json);
        Hava h = Hava.coz(json);
        long simdi = System.currentTimeMillis() / 1000;

        // --- Sabah tavsiyesi: ayarlanan saatten sonraki 3 saat içinde, günde bir kez
        if (a.sabahAcik()) {
            long yerel = Math.floorMod(simdi + h.utcFark, 86400L);
            int dk = (int) (yerel / 60);
            int hedef = a.sabahSaat() * 60 + a.sabahDakika();
            String bugun = Tavsiye.tarih(h, simdi);
            if (dk >= hedef && dk < hedef + 180 && !bugun.equals(a.sonSabah())) {
                Tavsiye.Gunluk g = Tavsiye.gunluk(h, simdi);
                Bildirim.gonder(c, 1, Bildirim.KANAL_SABAH, g.baslik + " " + a.sehirAd() + " " + Math.round(h.sicaklik) + "°", g.ozet());
                a.sonSabah(bugun);
            }
        }

        // --- Uyarılar: aynı uyarı aynı gün için bir kez
        if (a.uyariAcik()) {
            Set<String> gonderilen = a.gonderilenler();
            String bugun = Tavsiye.tarih(h, simdi);
            List<Tavsiye.Uyari> liste = Tavsiye.uyarilar(h, simdi);
            for (Tavsiye.Uyari u : liste) {
                if (gonderilen.contains(u.anahtar)) continue;
                Bildirim.gonder(c, 100 + Math.abs(u.anahtar.hashCode() % 1000), Bildirim.KANAL_UYARI, u.baslik, u.metin);
                gonderilen.add(u.anahtar);
            }
            // Eski günlerin kayıtlarını temizle
            Set<String> kalan = new HashSet<>();
            for (String k : gonderilen) {
                int i = k.indexOf('-');
                if (i > 0 && k.substring(i + 1).compareTo(bugun) >= 0) kalan.add(k);
            }
            a.gonderilenler(kalan);
        }
    }
}
