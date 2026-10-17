#!/usr/bin/env python3
"""
Durakta veri toplayıcı.

EGO'nun herkese açık web sayfalarından hat listesini ve hat saatlerini,
OpenStreetMap'ten de Ankara otobüs duraklarının konumlarını çekip
data/ klasörüne JSON olarak yazar. GitHub Actions her gece çalıştırır.

Sadece Python standart kütüphanesi kullanılır, kurulum gerekmez.

Kullanım:
    python scripts/veri_cek.py              # her şeyi çek
    python scripts/veri_cek.py --hat 442    # sadece bir hat (deneme için)
    python scripts/veri_cek.py --limit 20   # ilk 20 hat (deneme için)

Endpoint bilgileri için teşekkürler: github.com/byigitt/egomac (MIT)
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

KOK = Path(__file__).resolve().parent.parent
VERI = KOK / "data"
HAT_KLASORU = VERI / "hat"

EGO = "https://www.ego.gov.tr"
OVERPASS = "https://overpass-api.de/api/interpreter"
UA = "Durakta/1.0 (+https://github.com; acik kaynak Ankara ulasim sitesi)"

# EGO sunucusunu yormamak için istekler arası bekleme (saniye)
BEKLEME = 0.6

GUNLER = ("hafta_ici", "cumartesi", "pazar")

LISTE_ENDPOINTLERI = {
    "otobus": "/AjaxData/HatListesiOtobus",
    "metro": "/AjaxData/HatListesiMetro",
    "ankaray": "/AjaxData/HatListesiAnkaray",
}


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------

def post(url: str, veri: dict, ajax: bool = False, deneme: int = 3) -> str:
    govde = urllib.parse.urlencode(veri).encode()
    basliklar = {
        "User-Agent": UA,
        "Content-Type": "application/x-www-form-urlencoded",
        "Referer": EGO + "/hareketsaatleri",
    }
    if ajax:
        basliklar["X-Requested-With"] = "XMLHttpRequest"
    son_hata = None
    for i in range(deneme):
        try:
            istek = urllib.request.Request(url, data=govde, headers=basliklar)
            with urllib.request.urlopen(istek, timeout=60) as yanit:
                return yanit.read().decode("utf-8", errors="replace")
        except Exception as e:  # noqa: BLE001
            son_hata = e
            time.sleep(2 * (i + 1))
    raise RuntimeError(f"{url} alınamadı: {son_hata}")


# --------------------------------------------------------------------------
# Yardımcılar
# --------------------------------------------------------------------------

def temizle(s: str) -> str:
    """HTML etiketlerini sil, entity'leri çöz, boşlukları sadeleştir."""
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def dosya_adi(hat_no: str) -> str:
    """'107-6' gibi hat numaralarını güvenli dosya adına çevirir."""
    return re.sub(r"[^0-9A-Za-z_-]", "_", hat_no.strip())


def bas_int(s: str | None) -> int | None:
    if not s:
        return None
    m = re.match(r"\s*(\d+)", s)
    return int(m.group(1)) if m else None


# --------------------------------------------------------------------------
# Ayrıştırıcılar (parser)
# --------------------------------------------------------------------------

OPTION_RE = re.compile(r'<option\s+value="([^"]+)"\s*>\s*([^<]+?)\s*</option>', re.I)


def hat_listesi_ayristir(govde: str) -> list[dict]:
    """<option value="481"> (481 ) - UYANIŞ-...</option> satırlarını okur."""
    hatlar = []
    for kod, metin in OPTION_RE.findall(govde):
        kod = kod.strip()
        if not kod or kod in ("0", "-1"):
            continue
        ad = html.unescape(metin)
        ad = re.sub(r"^\s*\(\s*" + re.escape(kod) + r"\s*\)\s*-\s*", "", ad).strip()
        hatlar.append({"no": kod, "ad": ad})
    return hatlar


def meta_alan(baslik_html: str, etiket: str) -> str | None:
    """<td>Hat Adı</td><td>:</td><td>DEĞER</td> kalıbından değeri alır."""
    desen = (
        r"<td[^>]*>\s*" + re.escape(etiket) + r"\s*</td>\s*"
        r"<td[^>]*>\s*:\s*</td>\s*<td[^>]*>([\s\S]*?)</td>"
    )
    m = re.search(desen, baslik_html, re.I)
    if not m:
        return None
    deger = temizle(m.group(1))
    return deger or None


TABLO_RE = re.compile(r"<table[^>]*>([\s\S]*?)</table>", re.I)
SATIR_RE = re.compile(r"<tr[^>]*>([\s\S]*?)</tr>", re.I)
HUCRE_RE = re.compile(r"<td[^>]*>([\s\S]*?)</td>", re.I)
SAAT_BASI_RE = re.compile(r"^\s*(\d{1,2})[:.](\d{2})\b\s*(.*)$")
SAAT_RE = re.compile(r"\b(\d{1,2}):(\d{2})\b")


def hucre_seferleri(hucre_html: str) -> list[dict]:
    """
    Bir gün sütunundaki seferleri çıkarır.
    Her sefer satır başında HH:MM ile başlar, devamı not olur:
      "06:17 21071DEN BAŞLAR /KARAPINARDAN 06:50"
    Notun içindeki saatler ayrı sefer sayılmaz.
    """
    metin = re.sub(r"<br[^>]*>", "\n", hucre_html, flags=re.I)
    metin = re.sub(r"</?p[^>]*>", "\n", metin, flags=re.I)
    metin = html.unescape(re.sub(r"<[^>]+>", " ", metin))

    seferler: list[dict] = []
    satirlar = [s for s in metin.splitlines() if s.strip()]
    satir_bazli = sum(1 for s in satirlar if SAAT_BASI_RE.match(s))

    if satir_bazli >= 1 and satir_bazli >= len(satirlar) * 0.5:
        for s in satirlar:
            m = SAAT_BASI_RE.match(s)
            if not m:
                # saatsiz satır: önceki seferin notuna ekle
                if seferler:
                    ek = s.strip(" -\t")
                    if ek:
                        seferler[-1]["not"] = ((seferler[-1].get("not") or "") + " " + ek).strip()
                continue
            # Satır başındaki ardışık saatler ayrı seferdir: "06:00 06:20 07:10 NOT"
            kalan = s
            while True:
                m = SAAT_BASI_RE.match(kalan)
                if not m:
                    break
                ss, dd, kalan = int(m.group(1)), m.group(2), m.group(3)
                if ss <= 29:
                    seferler.append({"s": f"{ss:02d}:{dd}"})
            notu = kalan.strip(" -\t")
            if notu and seferler:
                seferler[-1]["not"] = re.sub(r"\s+", " ", notu)
    else:
        # Satır yapısı yoksa (tek blok metin) her HH:MM'i sefer kabul et
        duz = re.sub(r"\s+", " ", metin)
        eslesmeler = list(SAAT_RE.finditer(duz))
        for i, m in enumerate(eslesmeler):
            son = eslesmeler[i + 1].start() if i + 1 < len(eslesmeler) else len(duz)
            notu = duz[m.end():son].strip(" -\t")
            kayit = {"s": f"{int(m.group(1)):02d}:{m.group(2)}"}
            if notu:
                kayit["not"] = notu
            seferler.append(kayit)
    return seferler


def saat_sayfasi_ayristir(govde: str, hat_no: str) -> dict | None:
    """/HareketSaatleri sayfasından bilgi, saatler ve durak listesini çıkarır."""
    isaret = govde.find("<!-- Saat Tablosu -->")
    if isaret < 0:
        return None
    ust = govde[:isaret]
    alt = govde[isaret:]
    tablolar = TABLO_RE.findall(alt)

    saatler = {g: [] for g in GUNLER}
    if tablolar:
        for satir in SATIR_RE.findall(tablolar[0]):
            hucreler = HUCRE_RE.findall(satir)
            for i, gun in enumerate(GUNLER):
                if i < len(hucreler):
                    saatler[gun].extend(hucre_seferleri(hucreler[i]))

    duraklar = []
    if len(tablolar) >= 2:
        for satir in SATIR_RE.findall(tablolar[1]):
            h = [temizle(x) for x in HUCRE_RE.findall(satir)]
            if len(h) >= 3 and h[0].isdigit():
                d = {"sira": int(h[0]), "no": h[1], "ad": h[2]}
                if len(h) >= 4 and h[3]:
                    d["adres"] = h[3]
                duraklar.append(d)

    sonuc = {
        "no": hat_no,
        "ad": meta_alan(ust, "Hat Adı") or "",
        "kalkis": meta_alan(ust, "Kalkış Yeri"),
        "varis": meta_alan(ust, "Varış Yeri"),
        "km": bas_int(meta_alan(ust, "Mesafesi")),
        "dk": bas_int(meta_alan(ust, "Süresi")),
        "saatler": saatler,
        "duraklar": duraklar,
    }
    if not any(saatler.values()) and not duraklar:
        return None
    return sonuc


# --------------------------------------------------------------------------
# Toplama adımları
# --------------------------------------------------------------------------

def hatlari_cek() -> list[dict]:
    tum: dict[str, dict] = {}
    for tur, yol in LISTE_ENDPOINTLERI.items():
        try:
            for h in hat_listesi_ayristir(post(EGO + yol, {}, ajax=True)):
                h["tur"] = tur
                tum.setdefault(h["no"], h)
            print(f"  {tur}: tamam", file=sys.stderr)
        except Exception as e:  # noqa: BLE001
            print(f"  {tur} listesi alınamadı: {e}", file=sys.stderr)
        time.sleep(BEKLEME)
    if not tum:  # türe göre listeler çalışmazsa genel listeyi dene
        for h in hat_listesi_ayristir(post(EGO + "/AjaxData/HatListesi", {}, ajax=True)):
            h["tur"] = "otobus"
            tum.setdefault(h["no"], h)
    return sorted(tum.values(), key=lambda h: (h["tur"], [int(p) if p.isdigit() else p for p in re.split(r"(\d+)", h["no"])]))


def saatleri_cek(hat_no: str) -> dict | None:
    govde = post(EGO + "/HareketSaatleri", {"hat_no1": hat_no})
    return saat_sayfasi_ayristir(govde, hat_no)


def duraklari_cek() -> dict:
    """OpenStreetMap'teki Ankara otobüs duraklarını {durak_no: [lat, lng, ad]} olarak döner."""
    sorgu = """
    [out:json][timeout:180];
    area["name"="Ankara"]["admin_level"="4"]->.a;
    node["highway"="bus_stop"](area.a);
    out body;
    """
    istek = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": sorgu}).encode(),
        headers={"User-Agent": UA},
    )
    with urllib.request.urlopen(istek, timeout=240) as yanit:
        veri = json.load(yanit)
    duraklar = {}
    for el in veri.get("elements", []):
        t = el.get("tags", {})
        ref = (t.get("ref") or "").strip()
        if not ref:
            continue
        for no in re.split(r"[;,\s]+", ref):
            if no.isdigit():
                duraklar[no] = [round(el["lat"], 6), round(el["lon"], 6), t.get("name", "")]
    return duraklar


def yaz(yol: Path, veri) -> None:
    yol.parent.mkdir(parents=True, exist_ok=True)
    yol.write_text(json.dumps(veri, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="Durakta veri toplayıcı")
    ap.add_argument("--hat", help="sadece bu hattı çek")
    ap.add_argument("--limit", type=int, help="en fazla bu kadar hat çek")
    ap.add_argument("--durak-yok", action="store_true", help="OSM duraklarını atla")
    a = ap.parse_args()

    print("Hat listesi alınıyor...", file=sys.stderr)
    if a.hat:
        hatlar = [{"no": a.hat, "ad": "", "tur": "otobus"}]
    else:
        hatlar = hatlari_cek()
        if a.limit:
            hatlar = hatlar[: a.limit]
    print(f"{len(hatlar)} hat bulundu.", file=sys.stderr)

    basarili, hatali = [], []
    for i, h in enumerate(hatlar, 1):
        try:
            detay = saatleri_cek(h["no"])
        except Exception as e:  # noqa: BLE001
            detay = None
            print(f"  [{i}/{len(hatlar)}] {h['no']}: hata ({e})", file=sys.stderr)
        if detay:
            detay["tur"] = h.get("tur", "otobus")
            if not detay["ad"]:
                detay["ad"] = h.get("ad", "")
            yaz(HAT_KLASORU / f"{dosya_adi(h['no'])}.json", detay)
            basarili.append({
                "no": h["no"],
                "ad": detay["ad"] or h.get("ad", ""),
                "tur": detay["tur"],
                "dosya": dosya_adi(h["no"]),
            })
        else:
            hatali.append(h["no"])
        if i % 50 == 0:
            print(f"  {i}/{len(hatlar)}", file=sys.stderr)
        time.sleep(BEKLEME)

    if not basarili:
        print("Hiç hat alınamadı, mevcut veriye dokunulmadı.", file=sys.stderr)
        return 1

    if not a.hat:
        yaz(VERI / "hatlar.json", {
            "guncelleme": datetime.now(timezone.utc).isoformat(timespec="minutes"),
            "ornek": False,
            "hatlar": basarili,
        })

    if not a.durak_yok and not a.hat:
        print("OSM durakları alınıyor...", file=sys.stderr)
        try:
            duraklar = duraklari_cek()
            if duraklar:
                yaz(VERI / "duraklar.json", duraklar)
                print(f"{len(duraklar)} durak konumu yazıldı.", file=sys.stderr)
        except Exception as e:  # noqa: BLE001
            print(f"OSM durakları alınamadı (eski dosya korunuyor): {e}", file=sys.stderr)

    print(f"Bitti: {len(basarili)} hat yazıldı, {len(hatali)} hat atlandı.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
