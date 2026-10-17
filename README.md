# Durakta

Ankara EGO otobüslerinin saatleri, sıradaki sefere kalan süre ve güzergah durakları. Tek sayfa, sunucusuz, reklamsız.

**Canlı site:** `https://enskygr234.github.io/durakta/`

## Ne yapıyor?

- Hat numarası ya da semt adıyla arama (`kizilay`, `Kızılay`, `KIZILAY` aynı sonucu verir)
- Hat sayfasında sıradaki sefere kaç dakika kaldığı, sonraki iki sefer
- Hafta içi / Cumartesi / Pazar tarifesi; geçen seferler soluk, sıradaki vurgulu
- Güzergahtaki duraklar ve harita (OpenStreetMap)
- Favori hatlar (tarayıcında saklanır), paylaşılabilir bağlantılar (`#/hat/442`)

## Nasıl çalışıyor?

```
EGO web sitesi ─┐
                ├─> GitHub Actions (her gece) ─> data/*.json ─> GitHub Pages ─> sen
OpenStreetMap ──┘
```

Sunucu yok. `scripts/veri_cek.py` her gece EGO'nun herkese açık hat listesi ve hareket saatleri sayfalarını okur, durak konumlarını OpenStreetMap'ten alır ve sonuçları `data/` klasörüne yazar. Site bu JSON dosyalarını okuyup her şeyi tarayıcıda hesaplar.

| Dosya | İçerik |
|---|---|
| `data/hatlar.json` | Tüm hatların listesi |
| `data/hat/<hat>.json` | Bir hattın bilgisi, saatleri ve durakları |
| `data/duraklar.json` | `{durak_no: [enlem, boylam, ad]}` |

Depodaki ilk veri **örnek veridir**; ilk otomatik çalıştırmadan sonra gerçek EGO verisiyle değişir.

## Kurulum (5 dakika)

1. Bu depoyu GitHub'a yükle.
2. **Settings → Pages**: *Source* olarak `Deploy from a branch`, dal `main`, klasör `/ (root)` seç.
3. **Actions** sekmesinde *Veriyi güncelle* iş akışını aç ve **Run workflow** ile bir kez elle çalıştır (yaklaşık 10 dakika sürer).
4. Birkaç dakika sonra site `https://enskygr234.github.io/durakta/` adresinde.

## Bilgisayarında çalıştırmak

```bash
python scripts/veri_cek.py --limit 10   # ilk 10 hattı çek (deneme)
python -m http.server 8000              # http://localhost:8000
```

Script sadece Python standart kütüphanesini kullanır, `pip install` gerekmez.

## Bilinen sınırlar

- Saatler tarifedir, canlı konum değildir. Trafik, bayram ve özel günlerde gerçek saatler değişebilir. Canlı takip için EGO Cep'te uygulamasını kullan.
- EGO'nun resmi bir açık veri API'si yok; script web sitesindeki sayfaları okur. EGO sayfa yapısını değiştirirse script güncellenmelidir.
- Bazı durakların OpenStreetMap'te numarası olmadığı için haritada eksik görünebilirler. OSM'ye durak `ref` etiketi ekleyerek katkı verebilirsin.

## Teşekkür ve yasal not

Bu proje resmi değildir; Ankara Büyükşehir Belediyesi veya EGO Genel Müdürlüğü ile bağlantısı yoktur. EGO uç noktalarının belgelenmesi için [byigitt/egomac](https://github.com/byigitt/egomac) projesine teşekkürler. Durak konumları © OpenStreetMap katkıcıları (ODbL).

Lisans: MIT
