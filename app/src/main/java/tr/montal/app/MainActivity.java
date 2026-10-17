package tr.montal.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final Locale TR = new Locale("tr", "TR");

    // Sıcaklık bantlarına göre arka plan: soğuktan sıcağa
    private static final int[] RENKLER = {
        0xFF1E3A66, // çok soğuk: gece mavisi
        0xFF2E5E8C, // soğuk: mont mavisi
        0xFF2F6F6A, // serin: çam yeşili
        0xFF4F7A2E, // ılık: çimen
        0xFFB86E12, // sıcak: kehribar
        0xFFA63A24, // çok sıcak: kiremit
    };

    private final ExecutorService arka = Executors.newSingleThreadExecutor();
    private Ayarlar ayar;
    private ScrollView kok;
    private LinearLayout icerik;
    private boolean yukleniyor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ayar = new Ayarlar(this);
        Bildirim.kanallariKur(this);
        KontrolJob.planla(this);

        kok = new ScrollView(this);
        kok.setFillViewport(true);
        icerik = new LinearLayout(this);
        icerik.setOrientation(LinearLayout.VERTICAL);
        icerik.setPadding(dp(20), dp(28), dp(20), dp(32));
        kok.addView(icerik);
        setContentView(kok);
        renkUygula(RENKLER[1]);

        if (Build.VERSION.SDK_INT >= 33 && !Bildirim.izinVar(this)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String onbellek = ayar.tahmin();
        if (onbellek != null) {
            try { ciz(Hava.coz(onbellek)); } catch (Exception ignored) { }
        } else {
            bosEkran("Hava durumu yükleniyor…");
        }
        yenile(false);
    }

    // ---------------------------------------------------------------- Veri

    private void yenile(boolean elle) {
        if (yukleniyor) return;
        // 10 dakikadan yeni veri varsa tekrar indirme
        if (!elle && ayar.tahmin() != null && System.currentTimeMillis() - ayar.tahminZamani() < 10 * 60 * 1000L) return;
        yukleniyor = true;
        arka.execute(() -> {
            try {
                String json = Hava.tahminGetir(ayar.lat(), ayar.lon());
                Hava h = Hava.coz(json);
                ayar.tahmin(json);
                runOnUiThread(() -> { yukleniyor = false; ciz(h); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    yukleniyor = false;
                    if (ayar.tahmin() == null) {
                        bosEkran("Hava durumu alınamadı. İnternet bağlantını kontrol edip yenile.");
                    } else if (elle) {
                        Toast.makeText(this, "İnternet yok, son bilgiler gösteriliyor", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- Ekran

    private void bosEkran(String mesaj) {
        icerik.removeAllViews();
        icerik.addView(ustSatir());
        TextView t = yazi(mesaj, 18, false);
        t.setPadding(0, dp(48), 0, 0);
        icerik.addView(t);
        TextView yeniden = dugme("Yeniden dene");
        yeniden.setOnClickListener(v -> yenile(true));
        LinearLayout.LayoutParams lp = sarmal();
        lp.topMargin = dp(20);
        icerik.addView(yeniden, lp);
    }

    private void ciz(Hava h) {
        long simdi = System.currentTimeMillis() / 1000;
        Tavsiye.Gunluk g = Tavsiye.gunluk(h, simdi);
        List<Tavsiye.Uyari> uyarilar = Tavsiye.uyarilar(h, simdi);
        renkUygula(RENKLER[g.bant]);

        icerik.removeAllViews();
        icerik.addView(ustSatir());

        // --- Tavsiye: ekranın yıldızı
        TextView gun = yazi(g.gun, 17, false);
        gun.setAlpha(0.8f);
        gun.setPadding(0, dp(28), 0, dp(2));
        icerik.addView(gun);

        TextView baslik = yazi(g.baslik, 44, true);
        baslik.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        baslik.setLineSpacing(0, 0.95f);
        icerik.addView(baslik);

        for (String e : g.ekler) {
            TextView t = yazi(e, 19, false);
            t.setPadding(0, dp(8), 0, 0);
            icerik.addView(t);
        }

        // --- Uyarılar
        for (Tavsiye.Uyari u : uyarilar) {
            LinearLayout kart = kart(0xFFFFD54A, 1f);
            TextView ub = yazi(u.baslik, 17, true);
            ub.setTextColor(0xFF3A2C00);
            TextView um = yazi(u.metin, 15, false);
            um.setTextColor(0xFF3A2C00);
            um.setPadding(0, dp(4), 0, 0);
            kart.addView(ub);
            kart.addView(um);
            LinearLayout.LayoutParams lp = sarmal();
            lp.topMargin = dp(16);
            icerik.addView(kart, lp);
        }

        // --- Şu an
        LinearLayout simdiKart = kart(Color.WHITE, 0.14f);
        LinearLayout satir = new LinearLayout(this);
        satir.setGravity(Gravity.CENTER_VERTICAL);
        TextView derece = yazi(Math.round(h.sicaklik) + "°", 64, true);
        derece.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        satir.addView(derece);
        LinearLayout yan = new LinearLayout(this);
        yan.setOrientation(LinearLayout.VERTICAL);
        yan.setPadding(dp(16), 0, 0, 0);
        yan.addView(yazi(Hava.simge(h.kod, h.gunduz) + "  " + Hava.durum(h.kod), 18, true));
        TextView detay = yazi("Hissedilen " + Math.round(h.hissedilen) + "°, rüzgar " + Math.round(h.ruzgar) + " km/s", 15, false);
        detay.setAlpha(0.85f);
        yan.addView(detay);
        satir.addView(yan);
        simdiKart.addView(satir);
        LinearLayout.LayoutParams sk = sarmal();
        sk.topMargin = dp(24);
        icerik.addView(simdiKart, sk);

        // --- Saat saat
        icerik.addView(bolumBaslik("Saat saat"));
        HorizontalScrollView yatay = new HorizontalScrollView(this);
        yatay.setHorizontalScrollBarEnabled(false);
        LinearLayout saatler = new LinearLayout(this);
        int eklenen = 0;
        for (int i = 0; i < h.sZaman.length && eklenen < 24; i++) {
            if (h.sZaman[i] + 3600 <= simdi) continue;
            LinearLayout s = new LinearLayout(this);
            s.setOrientation(LinearLayout.VERTICAL);
            s.setGravity(Gravity.CENTER_HORIZONTAL);
            s.setPadding(dp(10), dp(10), dp(10), dp(10));
            int yerelSaat = (int) (Math.floorMod(h.sZaman[i] + h.utcFark, 86400L) / 3600);
            boolean gunduz = yerelSaat >= 7 && yerelSaat < 19;
            s.addView(ortala(yazi(eklenen == 0 ? "Şimdi" : String.format(Locale.US, "%02d", yerelSaat), 14, false)));
            s.addView(ortala(yazi(Hava.simge(h.sKod[i], gunduz), 22, false)));
            s.addView(ortala(yazi(Math.round(h.sSicaklik[i]) + "°", 17, true)));
            double ihtimal = h.sYagisIhtimal[i];
            TextView y = ortala(yazi(!Double.isNaN(ihtimal) && ihtimal >= 20 ? Math.round(ihtimal) + "%" : " ", 12, false));
            y.setTextColor(0xFFCFE6FF);
            s.addView(y);
            saatler.addView(s);
            eklenen++;
        }
        yatay.addView(saatler);
        LinearLayout saatKart = kart(Color.WHITE, 0.10f);
        saatKart.setPadding(dp(4), dp(4), dp(4), dp(4));
        saatKart.addView(yatay);
        icerik.addView(saatKart, sarmal());

        // --- 7 gün
        icerik.addView(bolumBaslik("Önümüzdeki günler"));
        LinearLayout gunKart = kart(Color.WHITE, 0.10f);
        for (int i = 0; i < h.gZaman.length; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(8), 0, dp(8));
            String ad = i == 0 ? "Bugün" : i == 1 ? "Yarın" : Instant.ofEpochSecond(h.gZaman[i] + 12 * 3600)
                .atOffset(ZoneOffset.ofTotalSeconds(h.utcFark)).getDayOfWeek().getDisplayName(TextStyle.FULL, TR);
            TextView ta = yazi(ad, 17, false);
            r.addView(ta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            double ih = h.gYagisIhtimal[i];
            TextView ty = yazi(!Double.isNaN(ih) && ih >= 30 ? Math.round(ih) + "%" : "", 13, false);
            ty.setTextColor(0xFFCFE6FF);
            ty.setGravity(Gravity.END);
            r.addView(ty, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView ts = yazi(Hava.simge(h.gKod[i], true), 20, false);
            ts.setGravity(Gravity.CENTER);
            r.addView(ts, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView td = yazi(Math.round(h.gEnDusuk[i]) + "°  " + Math.round(h.gEnYuksek[i]) + "°", 17, true);
            td.setGravity(Gravity.END);
            r.addView(td, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
            gunKart.addView(r);
        }
        icerik.addView(gunKart, sarmal());

        // --- Bildirim ayarları
        icerik.addView(bolumBaslik("Bildirimler"));
        LinearLayout ayarKart = kart(Color.WHITE, 0.10f);

        Switch sabah = anahtar("Sabah tavsiyesi", ayar.sabahAcik());
        sabah.setOnCheckedChangeListener((v, acik) -> ayar.sabahAcik(acik));
        ayarKart.addView(sabah);

        TextView saat = yazi(String.format(Locale.US, "Her sabah %02d:%02d civarı", ayar.sabahSaat(), ayar.sabahDakika()), 15, false);
        saat.setAlpha(0.85f);
        saat.setPadding(0, 0, 0, dp(12));
        saat.setOnClickListener(v -> new TimePickerDialog(this, (tp, sa, dk) -> {
            ayar.sabahZaman(sa, dk);
            ayar.sonSabah("");
            saat.setText(String.format(Locale.US, "Her sabah %02d:%02d civarı", sa, dk));
        }, ayar.sabahSaat(), ayar.sabahDakika(), true).show());
        saat.setText(saat.getText() + "  (değiştir)");
        ayarKart.addView(saat);

        Switch uyari = anahtar("Kar, fırtına, don uyarıları", ayar.uyariAcik());
        uyari.setOnCheckedChangeListener((v, acik) -> ayar.uyariAcik(acik));
        ayarKart.addView(uyari);

        TextView dene = dugme("Deneme bildirimi gönder");
        dene.setOnClickListener(v -> {
            if (!Bildirim.izinVar(this)) {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
            Bildirim.gonder(this, 1, Bildirim.KANAL_SABAH, g.baslik + " " + ayar.sehirAd() + " " + Math.round(h.sicaklik) + "°", g.ozet());
        });
        LinearLayout.LayoutParams dl = sarmal();
        dl.topMargin = dp(8);
        ayarKart.addView(dene, dl);
        icerik.addView(ayarKart, sarmal());

        // --- Alt bilgi
        java.text.DateFormat df = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, TR);
        TextView alt = yazi("Son güncelleme " + df.format(new java.util.Date(ayar.tahminZamani())) + ". Veri: Open-Meteo.", 13, false);
        alt.setAlpha(0.7f);
        alt.setPadding(0, dp(20), 0, 0);
        icerik.addView(alt);
    }

    private View ustSatir() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView sehir = yazi("📍 " + ayar.sehirAd() + "  ▾", 20, true);
        sehir.setPadding(0, dp(8), dp(8), dp(8));
        sehir.setOnClickListener(v -> sehirSor());
        r.addView(sehir, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView yenileB = yazi("↻", 26, true);
        yenileB.setPadding(dp(12), dp(4), dp(4), dp(4));
        yenileB.setContentDescription("Yenile");
        yenileB.setOnClickListener(v -> yenile(true));
        r.addView(yenileB);
        return r;
    }

    // ---------------------------------------------------------------- Şehir seçimi

    private void sehirSor() {
        EditText giris = new EditText(this);
        giris.setHint("Şehir ya da ilçe, ör. Çankaya");
        giris.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        giris.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        giris.setSingleLine(true);
        LinearLayout kap = new LinearLayout(this);
        kap.setPadding(dp(20), dp(8), dp(20), 0);
        kap.addView(giris, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle("Şehir seç")
            .setView(kap)
            .setPositiveButton("Ara", (x, y) -> ara(giris.getText().toString()))
            .setNegativeButton("Vazgeç", null)
            .create();
        giris.setOnEditorActionListener((v, eylem, olay) -> {
            ara(giris.getText().toString());
            d.dismiss();
            return true;
        });
        d.show();
    }

    private void ara(String sorgu) {
        if (sorgu.trim().length() < 2) return;
        arka.execute(() -> {
            try {
                List<Hava.Sehir> sonuc = Hava.sehirAra(sorgu);
                runOnUiThread(() -> {
                    if (sonuc.isEmpty()) {
                        Toast.makeText(this, "\"" + sorgu + "\" bulunamadı", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] adlar = new String[sonuc.size()];
                    for (int i = 0; i < adlar.length; i++) adlar[i] = sonuc.get(i).toString();
                    new AlertDialog.Builder(this)
                        .setTitle("Hangisi?")
                        .setItems(adlar, (x, i) -> {
                            Hava.Sehir s = sonuc.get(i);
                            ayar.sehirKaydet(s.ad, s.lat, s.lon);
                            bosEkran(s.ad + " için hava durumu yükleniyor…");
                            yenile(true);
                        })
                        .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Arama yapılamadı, internet bağlantını kontrol et", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------------------------------------------------------------- Görünüm yardımcıları

    private void renkUygula(int renk) {
        kok.setBackgroundColor(renk);
        getWindow().setStatusBarColor(renk);
        getWindow().setNavigationBarColor(renk);
    }

    private TextView yazi(String s, int sp, boolean kalin) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (kalin) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView ortala(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private TextView bolumBaslik(String s) {
        TextView t = yazi(s, 16, true);
        t.setAlpha(0.85f);
        t.setPadding(dp(4), dp(28), 0, dp(8));
        return t;
    }

    private LinearLayout kart(int renk, float opaklik) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(Color.argb(Math.round(opaklik * 255), Color.red(renk), Color.green(renk), Color.blue(renk)));
        k.setBackground(g);
        return k;
    }

    private TextView dugme(String s) {
        TextView t = yazi(s, 16, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setStroke(dp(2), Color.argb(150, 255, 255, 255));
        t.setBackground(g);
        return t;
    }

    private Switch anahtar(String s, boolean acik) {
        Switch sw = new Switch(this);
        sw.setText(s);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        sw.setChecked(acik);
        sw.setPadding(0, dp(8), 0, dp(8));
        return sw;
    }

    private LinearLayout.LayoutParams sarmal() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        arka.shutdown();
        super.onDestroy();
    }
}
