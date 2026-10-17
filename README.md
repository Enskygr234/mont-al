# Mont Al

Havaya bakıp ne giyeceğini söyleyen Android uygulaması. Her sabah "Mont al.", "Şemsiye al." gibi tek cümlelik bir tavsiye bildirimi atar; kar, fırtına, don, kuvvetli yağış, sert rüzgar ya da aşırı sıcak gelince ayrıca uyarır.

Tanıtım sitesi `docs/` klasöründe: Settings → Pages → `main` dalı, `/docs` klasörü seçilirse `https://enskygr234.github.io/mont-al/` adresinde yayınlanır. Tanıtım videosu: [docs/mont-al-tanitim.mp4](docs/mont-al-tanitim.mp4). Videonun ve sitenin kaynakları `tanitim/` klasöründe.

## Özellikler

- Büyük harflerle günün kıyafet tavsiyesi; ekranın rengi havanın sıcaklığına göre değişir
- Şu anki hava, saat saat 24 saat, 7 günlük tahmin
- Sabah tavsiyesi bildirimi (saatini sen seçersin, varsayılan 07:00)
- Önümüzdeki 12 saat için uyarılar: fırtına, kar, kuvvetli yağış, rüzgar, don/buzlanma, aşırı sıcak. Aynı uyarı aynı gün bir kez gelir.
- Şehir seçimi (varsayılan Ankara), internet yokken son tahmin
- Hesap yok, reklam yok, konum izni istemez

## Kurulum

[Releases](../../releases/latest) sayfasından `mont-al.apk` dosyasını telefondan indirip kur. İlk açılışta bildirim izni ister, izin ver.

Telefonun pil tasarrufu uygulamaları uyutuyorsa bildirimler gecikebilir. Ayarlar → Uygulamalar → Mont Al → Pil → "Kısıtlanmamış" seçersen zamanında gelir.

## Nasıl çalışıyor?

- Hava verisi [Open-Meteo](https://open-meteo.com)'dan gelir (ücretsiz, anahtar gerektirmez).
- Android saatte bir uygulamayı arka planda uyandırır; tahmini yeniler, gerekiyorsa bildirim atar.
- Tavsiye, gün içindeki en düşük hissedilen sıcaklığa göre seçilir: 0° altı kalın mont, 7°'ye kadar mont, 13°'ye kadar ceket, 19°'ye kadar ince hırka, sonrası tişört.
- Kod `app/src/main/java/tr/montal/app/` içinde; kurallar `Tavsiye.java` dosyasında.

`app/` klasöründe bir değişiklik olunca GitHub Actions yeni APK'yı derleyip Releases'a koyar.

Uyarılar hava tahmin modeline göre hesaplanır, resmi meteoroloji uyarısı değildir. Önemli durumlarda MGM'yi takip et.
