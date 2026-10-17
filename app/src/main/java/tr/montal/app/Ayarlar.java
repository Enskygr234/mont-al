package tr.montal.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Telefonda saklanan ayarlar ve son indirilen tahmin. */
public final class Ayarlar {

    private final SharedPreferences p;

    public Ayarlar(Context c) {
        p = c.getSharedPreferences("montal", Context.MODE_PRIVATE);
    }

    // Şehir (varsayılan Ankara)
    public String sehirAd() { return p.getString("sehir", "Ankara"); }
    public double lat() { return Double.longBitsToDouble(p.getLong("lat", Double.doubleToLongBits(39.9334))); }
    public double lon() { return Double.longBitsToDouble(p.getLong("lon", Double.doubleToLongBits(32.8597))); }

    public void sehirKaydet(String ad, double lat, double lon) {
        p.edit()
            .putString("sehir", ad)
            .putLong("lat", Double.doubleToLongBits(lat))
            .putLong("lon", Double.doubleToLongBits(lon))
            .remove("tahmin")
            .apply();
    }

    // Sabah bildirimi
    public boolean sabahAcik() { return p.getBoolean("sabahAcik", true); }
    public void sabahAcik(boolean b) { p.edit().putBoolean("sabahAcik", b).apply(); }
    public int sabahSaat() { return p.getInt("sabahSaat", 7); }
    public int sabahDakika() { return p.getInt("sabahDakika", 0); }
    public void sabahZaman(int saat, int dk) { p.edit().putInt("sabahSaat", saat).putInt("sabahDakika", dk).apply(); }
    public String sonSabah() { return p.getString("sonSabah", ""); }
    public void sonSabah(String tarih) { p.edit().putString("sonSabah", tarih).apply(); }

    // Uyarılar
    public boolean uyariAcik() { return p.getBoolean("uyariAcik", true); }
    public void uyariAcik(boolean b) { p.edit().putBoolean("uyariAcik", b).apply(); }

    public Set<String> gonderilenler() { return new HashSet<>(p.getStringSet("gonderilen", new HashSet<>())); }
    public void gonderilenler(Set<String> s) { p.edit().putStringSet("gonderilen", new HashSet<>(s)).apply(); }

    // Önbellek: internet yokken son tahmini göstermek için
    public String tahmin() { return p.getString("tahmin", null); }
    public long tahminZamani() { return p.getLong("tahminZamani", 0); }
    public void tahmin(String json) {
        p.edit().putString("tahmin", json).putLong("tahminZamani", System.currentTimeMillis()).apply();
    }
}
