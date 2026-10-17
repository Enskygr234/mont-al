package tr.montal.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hava tahmininden kıyafet tavsiyesi ve uyarılar üretir. */
public final class Tavsiye {

    private Tavsiye() { }

    public static class Gunluk {
        public String gun;          // "Bugün" ya da "Yarın"
        public String baslik;       // "Mont al."
        public List<String> ekler = new ArrayList<>();
        public double enDusukHis, enYuksekHis;
        public int bant;            // 0 çok soğuk ... 5 çok sıcak (renk için)

        /** Bildirimde tek satır gösterim. */
        public String ozet() {
            StringBuilder b = new StringBuilder();
            b.append(String.format(Locale.US, "Hissedilen %d° ile %d° arası.", Math.round(enDusukHis), Math.round(enYuksekHis)));
            for (String e : ekler) b.append(' ').append(e);
            return b.toString();
        }
    }

    public static class Uyari {
        public String anahtar;   // tekrar bildirim atmamak için: "kar-2026-10-17"
        public String baslik;
        public String metin;
    }

    // ---------------------------------------------------------------- Kıyafet

    public static Gunluk gunluk(Hava h, long simdi) {
        long yerel = simdi + h.utcFark;
        long gunBasi = yerel - Math.floorMod(yerel, 86400L);
        int saat = (int) (Math.floorMod(yerel, 86400L) / 3600);

        long bas, son;
        Gunluk g = new Gunluk();
        if (saat < 20) {
            g.gun = "Bugün";
            bas = simdi;
            son = gunBasi + 22 * 3600 - h.utcFark;
        } else {
            g.gun = "Yarın";
            bas = gunBasi + 86400 + 8 * 3600 - h.utcFark;
            son = gunBasi + 86400 + 22 * 3600 - h.utcFark;
        }

        double enDusuk = Double.MAX_VALUE, enYuksek = -Double.MAX_VALUE;
        double enYuksekIhtimal = 0, toplamYagis = 0, toplamKar = 0, enHamle = 0;
        boolean firtina = false;
        int sayac = 0;
        for (int i = 0; i < h.sZaman.length; i++) {
            long t = h.sZaman[i];
            if (t + 3600 <= bas || t > son) continue;
            sayac++;
            enDusuk = min(enDusuk, h.sHissedilen[i]);
            enYuksek = max(enYuksek, h.sHissedilen[i]);
            enYuksekIhtimal = max(enYuksekIhtimal, h.sYagisIhtimal[i]);
            toplamYagis += sifir(h.sYagis[i]);
            toplamKar += sifir(h.sKar[i]);
            enHamle = max(enHamle, h.sRuzgarHamle[i]);
            if (h.sKod[i] >= 95) firtina = true;
        }
        if (sayac == 0) {
            enDusuk = enYuksek = h.hissedilen;
        }
        g.enDusukHis = enDusuk;
        g.enYuksekHis = enYuksek;

        // Sabah dışarı çıkan için belirleyici olan en soğuk an
        if (enDusuk <= 0) { g.baslik = "Kalın mont, bere ve eldiven."; g.bant = 0; }
        else if (enDusuk <= 7) { g.baslik = "Mont al."; g.bant = 1; }
        else if (enDusuk <= 13) { g.baslik = "Ceket ya da kalın hırka al."; g.bant = 2; }
        else if (enDusuk <= 19) { g.baslik = "İnce bir hırka yeter."; g.bant = 3; }
        else if (enDusuk <= 27) { g.baslik = "Tişört havası."; g.bant = 4; }
        else { g.baslik = "Çok sıcak, ince giyin."; g.bant = 5; }

        if (toplamKar >= 0.3) g.ekler.add("Kar yağacak, kaymayan ayakkabı giy.");
        else if (enYuksekIhtimal >= 50 || toplamYagis >= 1.0) g.ekler.add("Şemsiye al.");
        if (firtina) g.ekler.add("Gök gürültülü sağanak bekleniyor.");
        if (enHamle >= 45) g.ekler.add("Rüzgar sert esecek.");
        double uv = h.uvGun(bas);
        if (!Double.isNaN(uv) && uv >= 7 && enYuksek >= 18) g.ekler.add("Güneş kremi sür.");
        if (enYuksek - enDusuk >= 10) g.ekler.add("Gün içinde sıcaklık çok değişiyor, kat kat giyin.");
        if (enYuksek >= 30) g.ekler.add("Yanına su al.");
        return g;
    }

    // ---------------------------------------------------------------- Uyarılar

    /** Önümüzdeki 12 saatte dikkat edilmesi gereken durumlar. */
    public static List<Uyari> uyarilar(Hava h, long simdi) {
        List<Uyari> liste = new ArrayList<>();
        long son = simdi + 12 * 3600;
        boolean firtina = false, kar = false, yagis = false, ruzgar = false, don = false, sicak = false;
        double toplamKar = 0;

        for (int i = 0; i < h.sZaman.length; i++) {
            long t = h.sZaman[i];
            if (t + 3600 <= simdi || t > son) continue;
            String ne = saatYaz(h, t);

            if (!firtina && h.sKod[i] >= 95) {
                firtina = true;
                liste.add(uyari(h, "firtina", t, "⛈️ Fırtına uyarısı",
                    "Saat " + ne + " civarı gök gürültülü sağanak bekleniyor."
                        + (h.sKod[i] >= 96 ? " Dolu yağabilir, aracını kapalı alana çek." : " Dışarıdaysan kapalı bir yere geç.")));
            }
            toplamKar += sifir(h.sKar[i]);
            if (!kar && (sifir(h.sKar[i]) >= 1.0 || toplamKar >= 2.0)) {
                kar = true;
                liste.add(uyari(h, "kar", t, "❄️ Kar yağışı",
                    "Saat " + ne + " civarı kar yağışı başlıyor. Mont, bot ve eldiven unutma; yollar kaygan olabilir."));
            }
            if (!yagis && !kar && sifir(h.sYagis[i]) >= 5.0) {
                yagis = true;
                liste.add(uyari(h, "yagis", t, "🌧️ Kuvvetli yağış",
                    "Saat " + ne + " civarı kuvvetli yağmur bekleniyor. Şemsiye al, su birikintilerine dikkat."));
            }
            if (!ruzgar && sifir(h.sRuzgarHamle[i]) >= 60) {
                ruzgar = true;
                liste.add(uyari(h, "ruzgar", t, "💨 Kuvvetli rüzgar",
                    String.format(Locale.US, "Saat %s civarı rüzgar %d km/s'ye kadar çıkabilir. Çatı ve ağaç altlarından uzak dur.",
                        ne, Math.round(h.sRuzgarHamle[i]))));
            }
            if (!don && h.sSicaklik[i] <= -2) {
                don = true;
                liste.add(uyari(h, "don", t, "🧊 Don ve buzlanma",
                    String.format(Locale.US, "Saat %s civarı sıcaklık %d°'ye düşüyor. Yollarda buzlanma olabilir.",
                        ne, Math.round(h.sSicaklik[i]))));
            }
            if (!sicak && h.sHissedilen[i] >= 36) {
                sicak = true;
                liste.add(uyari(h, "sicak", t, "🥵 Aşırı sıcak",
                    String.format(Locale.US, "Saat %s civarı hissedilen sıcaklık %d°. Gölgede kal, bol su iç.",
                        ne, Math.round(h.sHissedilen[i]))));
            }
        }
        return liste;
    }

    private static Uyari uyari(Hava h, String tur, long t, String baslik, String metin) {
        Uyari u = new Uyari();
        u.anahtar = tur + "-" + tarih(h, t);
        u.baslik = baslik;
        u.metin = metin;
        return u;
    }

    // ---------------------------------------------------------------- Yardımcılar

    public static String saatYaz(Hava h, long t) {
        long yerel = Math.floorMod(t + h.utcFark, 86400L);
        return String.format(Locale.US, "%02d:%02d", yerel / 3600, (yerel % 3600) / 60);
    }

    /** "2026-10-17" biçiminde yerel tarih. */
    public static String tarih(Hava h, long t) {
        java.time.LocalDate d = java.time.Instant.ofEpochSecond(t)
            .atOffset(java.time.ZoneOffset.ofTotalSeconds(h.utcFark)).toLocalDate();
        return d.toString();
    }

    private static double sifir(double d) { return Double.isNaN(d) ? 0 : d; }
    private static double min(double a, double b) { return Double.isNaN(b) ? a : Math.min(a, b); }
    private static double max(double a, double b) { return Double.isNaN(b) ? a : Math.max(a, b); }
}
