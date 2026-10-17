package tr.montal.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Open-Meteo'dan hava tahmini ve şehir araması (ücretsiz, anahtar gerektirmez). */
public class Hava {

    // Şu an
    public double sicaklik, hissedilen, ruzgar;
    public int kod;
    public boolean gunduz;
    public long simdiZaman;

    // Saatlik (unix saniye)
    public long[] sZaman;
    public double[] sSicaklik, sHissedilen, sYagisIhtimal, sYagis, sKar, sRuzgarHamle;
    public int[] sKod;

    // Günlük
    public long[] gZaman;
    public int[] gKod;
    public double[] gEnYuksek, gEnDusuk, gHissEnDusuk, gYagisIhtimal, gUv;

    public int utcFark; // saniye

    // ---------------------------------------------------------------- İndirme

    public static String tahminGetir(double lat, double lon) throws IOException {
        String url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day"
                + "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,"
                + "snowfall,weather_code,wind_gusts_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_min,"
                + "precipitation_probability_max,uv_index_max"
                + "&timezone=auto&timeformat=unixtime&forecast_days=7&wind_speed_unit=kmh",
            lat, lon);
        return oku(url);
    }

    public static Hava coz(String json) throws Exception {
        JSONObject j = new JSONObject(json);
        Hava h = new Hava();
        h.utcFark = j.optInt("utc_offset_seconds", 3 * 3600);

        JSONObject c = j.getJSONObject("current");
        h.simdiZaman = c.optLong("time");
        h.sicaklik = c.optDouble("temperature_2m", Double.NaN);
        h.hissedilen = c.optDouble("apparent_temperature", h.sicaklik);
        h.ruzgar = c.optDouble("wind_speed_10m", 0);
        h.kod = c.optInt("weather_code", 0);
        h.gunduz = c.optInt("is_day", 1) == 1;

        JSONObject s = j.getJSONObject("hourly");
        h.sZaman = uzunlar(s.getJSONArray("time"));
        h.sSicaklik = sayilar(s.optJSONArray("temperature_2m"), h.sZaman.length);
        h.sHissedilen = sayilar(s.optJSONArray("apparent_temperature"), h.sZaman.length);
        h.sYagisIhtimal = sayilar(s.optJSONArray("precipitation_probability"), h.sZaman.length);
        h.sYagis = sayilar(s.optJSONArray("precipitation"), h.sZaman.length);
        h.sKar = sayilar(s.optJSONArray("snowfall"), h.sZaman.length);
        h.sRuzgarHamle = sayilar(s.optJSONArray("wind_gusts_10m"), h.sZaman.length);
        h.sKod = tamlar(s.optJSONArray("weather_code"), h.sZaman.length);

        JSONObject g = j.getJSONObject("daily");
        h.gZaman = uzunlar(g.getJSONArray("time"));
        int n = h.gZaman.length;
        h.gKod = tamlar(g.optJSONArray("weather_code"), n);
        h.gEnYuksek = sayilar(g.optJSONArray("temperature_2m_max"), n);
        h.gEnDusuk = sayilar(g.optJSONArray("temperature_2m_min"), n);
        h.gHissEnDusuk = sayilar(g.optJSONArray("apparent_temperature_min"), n);
        h.gYagisIhtimal = sayilar(g.optJSONArray("precipitation_probability_max"), n);
        h.gUv = sayilar(g.optJSONArray("uv_index_max"), n);
        return h;
    }

    /** Verilen zamana denk gelen günün UV değeri. */
    public double uvGun(long zaman) {
        for (int i = gZaman.length - 1; i >= 0; i--) {
            if (zaman >= gZaman[i]) return gUv[i];
        }
        return Double.NaN;
    }

    // ---------------------------------------------------------------- Şehir arama

    public static class Sehir {
        public String ad, bolge;
        public double lat, lon;

        @Override public String toString() {
            return bolge == null || bolge.isEmpty() || bolge.equals(ad) ? ad : ad + ", " + bolge;
        }
    }

    public static List<Sehir> sehirAra(String sorgu) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=tr&format=json&name="
            + URLEncoder.encode(sorgu.trim(), "UTF-8");
        JSONObject j = new JSONObject(oku(url));
        List<Sehir> liste = new ArrayList<>();
        JSONArray r = j.optJSONArray("results");
        if (r == null) return liste;
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            Sehir s = new Sehir();
            s.ad = o.optString("name");
            String b = o.optString("admin1", "");
            String u = o.optString("country", "");
            s.bolge = b.isEmpty() ? u : (u.isEmpty() || u.equals("Türkiye") ? b : b + ", " + u);
            s.lat = o.optDouble("latitude");
            s.lon = o.optDouble("longitude");
            liste.add(s);
        }
        return liste;
    }

    // ---------------------------------------------------------------- Hava kodları

    public static String durum(int kod) {
        switch (kod) {
            case 0: return "Açık";
            case 1: return "Az bulutlu";
            case 2: return "Parçalı bulutlu";
            case 3: return "Kapalı";
            case 45: case 48: return "Sisli";
            case 51: case 53: case 55: return "Çisenti";
            case 56: case 57: return "Donan çisenti";
            case 61: return "Hafif yağmur";
            case 63: return "Yağmurlu";
            case 65: return "Kuvvetli yağmur";
            case 66: case 67: return "Donan yağmur";
            case 71: return "Hafif kar";
            case 73: return "Karlı";
            case 75: return "Yoğun kar";
            case 77: return "Kar taneleri";
            case 80: case 81: return "Sağanak";
            case 82: return "Şiddetli sağanak";
            case 85: case 86: return "Kar sağanağı";
            case 95: return "Gök gürültülü fırtına";
            case 96: case 99: return "Dolu ve fırtına";
            default: return "";
        }
    }

    public static String simge(int kod, boolean gunduz) {
        if (kod == 0) return gunduz ? "☀️" : "🌙";
        if (kod == 1) return gunduz ? "🌤️" : "🌙";
        if (kod == 2) return "⛅";
        if (kod == 3) return "☁️";
        if (kod == 45 || kod == 48) return "🌫️";
        if (kod >= 51 && kod <= 57) return "🌦️";
        if (kod >= 61 && kod <= 67) return "🌧️";
        if (kod >= 71 && kod <= 77) return "❄️";
        if (kod >= 80 && kod <= 82) return "🌦️";
        if (kod == 85 || kod == 86) return "🌨️";
        if (kod >= 95) return "⛈️";
        return "🌡️";
    }

    // ---------------------------------------------------------------- Yardımcılar

    static String oku(String adres) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(adres).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "MontAl/1.0 (Android)");
        try {
            int kod = c.getResponseCode();
            if (kod != 200) throw new IOException("HTTP " + kod);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] tampon = new byte[8192];
                int n;
                while ((n = in.read(tampon)) > 0) b.write(tampon, 0, n);
                return b.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static long[] uzunlar(JSONArray a) {
        long[] out = new long[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.optLong(i);
        return out;
    }

    private static double[] sayilar(JSONArray a, int n) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (a == null || a.isNull(i)) ? Double.NaN : a.optDouble(i, Double.NaN);
        return out;
    }

    private static int[] tamlar(JSONArray a, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = (a == null) ? 0 : a.optInt(i, 0);
        return out;
    }
}
