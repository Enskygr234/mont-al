/* Durakta — Ankara otobüs saatleri
 * Sunucu yok: data/ klasöründeki JSON dosyalarını okur, her şeyi tarayıcıda hesaplar.
 */
(() => {
  "use strict";

  const $ = (sel, el = document) => el.querySelector(sel);
  const uygulama = $("#uygulama");

  const GUNLER = [
    { anahtar: "hafta_ici", ad: "Hafta içi" },
    { anahtar: "cumartesi", ad: "Cumartesi" },
    { anahtar: "pazar", ad: "Pazar" },
  ];

  const durum = {
    hatlar: [],
    duraklar: null,
    hatOnbellek: new Map(),
    sayacZamanlayici: null,
    harita: null,
  };

  // ---------- Yardımcılar ----------

  const kacis = (s) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // Türkçe harfleri sadeleştirerek arama: "kizilay" = "Kızılay" = "KIZILAY"
  const sadele = (s) =>
    String(s || "")
      .toLocaleLowerCase("tr")
      .replace(/ı/g, "i").replace(/ğ/g, "g").replace(/ü/g, "u")
      .replace(/ş/g, "s").replace(/ö/g, "o").replace(/ç/g, "c")
      .replace(/[^a-z0-9]+/g, " ")
      .trim();

  // EGO büyük harf yazıyor; okunaklı olsun diye Türkçe başlık harfine çevir
  const baslikHarf = (s) =>
    String(s || "")
      .toLocaleLowerCase("tr")
      .replace(/(^|[\s\-/(.])(\p{L})/gu, (_, a, b) => a + b.toLocaleUpperCase("tr"));

  const depo = {
    al(k, varsayilan) {
      try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : varsayilan; } catch { return varsayilan; }
    },
    koy(k, v) {
      try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* gizli sekme vb. */ }
    },
  };

  const favoriler = () => depo.al("durakta.favoriler", []);
  const favoriMi = (no) => favoriler().includes(no);
  const favoriDegistir = (no) => {
    const f = favoriler();
    const i = f.indexOf(no);
    if (i >= 0) f.splice(i, 1); else f.unshift(no);
    depo.koy("durakta.favoriler", f.slice(0, 30));
    return i < 0;
  };

  const bugununGunu = (t = new Date()) => {
    const g = t.getDay();
    return g === 6 ? "cumartesi" : g === 0 ? "pazar" : "hafta_ici";
  };

  const dkCevir = (s) => {
    const [h, m] = s.split(":").map(Number);
    return h * 60 + m;
  };

  async function jsonAl(yol) {
    const r = await fetch(yol, { cache: "no-cache" });
    if (!r.ok) throw new Error(`${yol}: ${r.status}`);
    return r.json();
  }

  // ---------- Sıradaki sefer ----------

  // Bugünün tarifesinden şu andan sonraki ilk seferi bulur; yoksa yarının ilk seferi.
  function siradakiler(hat, simdi = new Date()) {
    const suan = simdi.getHours() * 60 + simdi.getMinutes();
    const bugun = hat.saatler[bugununGunu(simdi)] || [];
    const sonra = bugun
      .map((x) => ({ ...x, dk: dkCevir(x.s) }))
      .filter((x) => x.dk >= suan)
      .sort((a, b) => a.dk - b.dk)
      .map((x) => ({ ...x, kalan: x.dk - suan, gun: "bugün" }));

    if (sonra.length < 3) {
      const yarin = new Date(simdi);
      yarin.setDate(yarin.getDate() + 1);
      const yt = (hat.saatler[bugununGunu(yarin)] || [])
        .map((x) => ({ ...x, dk: dkCevir(x.s) }))
        .sort((a, b) => a.dk - b.dk)
        .map((x) => ({ ...x, kalan: x.dk + 1440 - suan, gun: "yarın" }));
      sonra.push(...yt.slice(0, 3 - sonra.length));
    }
    return sonra.slice(0, 3);
  }

  function kalanYazi(dk) {
    if (dk < 60) return { buyuk: String(dk), kucuk: "dk" };
    const s = Math.floor(dk / 60), d = dk % 60;
    return { buyuk: `${s}:${String(d).padStart(2, "0")}`, kucuk: "sa" };
  }

  // ---------- Ekranlar ----------

  function hatSatiri(h) {
    return `<li><a class="hat-satir" href="#/hat/${encodeURIComponent(h.no)}">
      <span class="hat-no ${kacis(h.tur)}">${kacis(h.no)}</span>
      <span class="hat-ad">${kacis(baslikHarf(h.ad))}</span>
    </a></li>`;
  }

  function anaSayfa(sorgu = "") {
    temizle();
    document.title = "Durakta: Ankara otobüs saatleri";
    uygulama.innerHTML = `
      <form class="arama" role="search" onsubmit="return false">
        <label for="ara">Hangi otobüse bineceksin?</label>
        <input id="ara" type="search" inputmode="search" autocomplete="off"
               placeholder="Hat no ya da semt: 442, Kızılay" value="${kacis(sorgu)}">
      </form>
      <div id="sonuclar"></div>`;

    const girdi = $("#ara");
    const ciz = () => listele(girdi.value);
    girdi.addEventListener("input", () => {
      ciz();
      history.replaceState(null, "", girdi.value ? `#/?q=${encodeURIComponent(girdi.value)}` : "#/");
    });
    ciz();
    if (!sorgu && matchMedia("(min-width: 640px)").matches) girdi.focus();
  }

  function listele(sorgu) {
    const kutu = $("#sonuclar");
    const q = sadele(sorgu);
    let html = "";

    if (!q) {
      const fav = favoriler()
        .map((no) => durum.hatlar.find((h) => h.no === no))
        .filter(Boolean);
      if (fav.length) {
        html += `<h2 class="bolum-baslik">Favori hatların</h2><ul class="hatlar">${fav.map(hatSatiri).join("")}</ul>`;
      }
      html += `<h2 class="bolum-baslik">Tüm hatlar (${durum.hatlar.length})</h2>`;
      html += hatListesiHtml(durum.hatlar, 40);
    } else {
      const bulunan = durum.hatlar
        .map((h) => {
          const no = sadele(h.no), ad = sadele(h.ad);
          let puan = 0;
          if (no === q) puan = 100;
          else if (no.startsWith(q)) puan = 80;
          else if (q.split(" ").every((p) => ad.includes(p))) puan = 40;
          return { h, puan };
        })
        .filter((x) => x.puan > 0)
        .sort((a, b) => b.puan - a.puan)
        .map((x) => x.h);

      html += bulunan.length
        ? `<h2 class="bolum-baslik">${bulunan.length} hat bulundu</h2>` + hatListesiHtml(bulunan, 40)
        : `<div class="bos">"${kacis(sorgu)}" ile eşleşen hat yok. Hat numarasını ya da geçtiği bir semti yazmayı dene.</div>`;
    }
    kutu.innerHTML = html;

    const dahaBtn = $(".daha", kutu);
    if (dahaBtn) {
      dahaBtn.addEventListener("click", () => {
        dahaBtn.previousElementSibling.insertAdjacentHTML(
          "beforeend",
          durum.sonListe.slice(40).map(hatSatiri).join("")
        );
        dahaBtn.remove();
      });
    }
  }

  function hatListesiHtml(liste, sinir) {
    durum.sonListe = liste;
    const ilk = liste.slice(0, sinir);
    let html = `<ul class="hatlar">${ilk.map(hatSatiri).join("")}</ul>`;
    if (liste.length > sinir) {
      html += `<button class="daha" type="button">${liste.length - sinir} hat daha göster</button>`;
    }
    return html;
  }

  async function hatSayfasi(no, secilenGun) {
    temizle();
    const ozet = durum.hatlar.find((h) => h.no === no);
    if (!ozet) {
      uygulama.innerHTML = `<a class="geri" href="#/">‹ Tüm hatlar</a>
        <div class="bos">${kacis(no)} numaralı hat bulunamadı. Hat kaldırılmış ya da numarası değişmiş olabilir.</div>`;
      return;
    }

    let hat = durum.hatOnbellek.get(no);
    if (!hat) {
      uygulama.innerHTML = `<div class="bos">${kacis(no)} hattının saatleri yükleniyor…</div>`;
      try {
        hat = await jsonAl(`data/hat/${encodeURIComponent(ozet.dosya)}.json`);
        durum.hatOnbellek.set(no, hat);
      } catch {
        uygulama.innerHTML = `<a class="geri" href="#/">‹ Tüm hatlar</a>
          <div class="bos">Saatler yüklenemedi. İnternet bağlantını kontrol edip sayfayı yenile.</div>`;
        return;
      }
    }

    const gun = secilenGun || bugununGunu();
    const bugun = bugununGunu();
    document.title = `${hat.no} ${baslikHarf(hat.ad)}: Durakta`;

    const bilgi = [
      hat.kalkis && hat.varis ? `${baslikHarf(hat.kalkis)} – ${baslikHarf(hat.varis)}` : "",
      hat.dk ? `yaklaşık ${hat.dk} dk` : "",
      hat.km ? `${hat.km} km` : "",
    ].filter(Boolean).join(", ");

    const fav = favoriMi(no);

    uygulama.innerHTML = `
      <a class="geri" href="#/">‹ Tüm hatlar</a>
      <section class="tabela" aria-labelledby="hat-baslik">
        <div class="tabela-ust">
          <span class="hat-no ${kacis(hat.tur)}">${kacis(hat.no)}</span>
          <div>
            <h1 class="tabela-ad" id="hat-baslik">${kacis(baslikHarf(hat.ad))}</h1>
            <div class="tabela-bilgi">${kacis(bilgi)}</div>
          </div>
          <button class="favori" type="button" aria-pressed="${fav}" aria-label="Favorilere ekle">
            <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>
          </button>
        </div>
        <div class="sayac" aria-live="polite" id="sayac"></div>
        <div class="tabela-alt">
          <a class="dis-link" href="https://www.ego.gov.tr/hareketsaatleri" target="_blank" rel="noopener">EGO'da kontrol et</a>
          <button class="dis-link" type="button" id="paylas" style="background:none;cursor:pointer;font-family:inherit">Paylaş</button>
        </div>
      </section>

      <div class="gunler" role="group" aria-label="Tarife günü">
        ${GUNLER.map((g) => `<button type="button" data-gun="${g.anahtar}"
          class="${g.anahtar === bugun ? "bugun" : ""}"
          aria-pressed="${g.anahtar === gun}">${g.ad}</button>`).join("")}
      </div>
      <div id="tarife"></div>

      ${hat.duraklar && hat.duraklar.length ? `
        <h2 class="bolum-baslik">Güzergah (${hat.duraklar.length} durak)</h2>
        <div id="harita" hidden></div>
        <ol class="guzergah" style="margin-top:12px">
          ${hat.duraklar.map((d) => `<li><span class="durak-ad">${kacis(baslikHarf(d.ad))}</span><span class="durak-no">${kacis(d.no)}</span></li>`).join("")}
        </ol>` : ""}
    `;

    const favBtn = $(".favori");
    favBtn.setAttribute("aria-label", fav ? "Favorilerden çıkar" : "Favorilere ekle");
    favBtn.addEventListener("click", () => {
      const eklendi = favoriDegistir(no);
      favBtn.setAttribute("aria-pressed", eklendi);
      favBtn.setAttribute("aria-label", eklendi ? "Favorilerden çıkar" : "Favorilere ekle");
    });

    $("#paylas").addEventListener("click", async () => {
      const veri = { title: `${hat.no} saatleri`, url: location.href };
      try {
        if (navigator.share) await navigator.share(veri);
        else { await navigator.clipboard.writeText(location.href); $("#paylas").textContent = "Bağlantı kopyalandı"; }
      } catch { /* kullanıcı vazgeçti */ }
    });

    uygulama.querySelectorAll(".gunler button").forEach((b) =>
      b.addEventListener("click", () => {
        uygulama.querySelectorAll(".gunler button").forEach((x) => x.setAttribute("aria-pressed", x === b));
        tarifeCiz(hat, b.dataset.gun);
      })
    );

    sayacCiz(hat);
    durum.sayacZamanlayici = setInterval(() => {
      sayacCiz(hat);
      const secili = $(".gunler [aria-pressed='true']");
      if (secili) tarifeCiz(hat, secili.dataset.gun);
    }, 20000);

    tarifeCiz(hat, gun);
    haritaCiz(hat);
    uygulama.focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }

  function sayacCiz(hat) {
    const kutu = $("#sayac");
    if (!kutu) return;
    const s = siradakiler(hat);
    if (!s.length) {
      kutu.innerHTML = `<div class="sayac-etiket">Bu hat için tarife bulunamadı.</div>`;
      return;
    }
    const ilk = s[0];
    const k = kalanYazi(ilk.kalan);
    const etiket = ilk.kalan === 0 ? "Sıradaki sefer şimdi kalkıyor" : "Sıradaki sefer";
    kutu.innerHTML = `
      <div class="sayac-etiket">${etiket}</div>
      <div class="sayac-dk">${ilk.kalan === 0 ? "şimdi" : `${k.buyuk}<small>${k.kucuk}</small>`}</div>
      <div class="sayac-yan">
        <strong>${kacis(ilk.s)}</strong>
        ${ilk.gun === "yarın" ? "yarın, " : ""}${kacis(hat.kalkis ? baslikHarf(hat.kalkis) + " kalkışlı" : "ilk duraktan")}
        ${ilk.not ? `<div class="not">${kacis(ilk.not)}</div>` : ""}
      </div>
      ${s.length > 1 ? `<div class="sonraki">Sonra: ${s.slice(1).map((x) => `<b>${kacis(x.s)}</b>${x.gun === "yarın" && s[0].gun !== "yarın" ? " (yarın)" : ""}`).join(", ")}</div>` : ""}`;
  }

  function tarifeCiz(hat, gun) {
    const kutu = $("#tarife");
    if (!kutu) return;
    const liste = (hat.saatler[gun] || []).slice().sort((a, b) => dkCevir(a.s) - dkCevir(b.s));
    if (!liste.length) {
      kutu.innerHTML = `<div class="bos">Bu hat ${GUNLER.find((g) => g.anahtar === gun).ad.toLocaleLowerCase("tr")} günleri çalışmıyor.</div>`;
      return;
    }

    const bugunMu = gun === bugununGunu();
    const simdi = new Date();
    const suan = simdi.getHours() * 60 + simdi.getMinutes();
    const siradakiDk = bugunMu ? (liste.find((x) => dkCevir(x.s) >= suan) || {}).s : null;

    const saatGrup = new Map();
    liste.forEach((x) => {
      const h = x.s.slice(0, 2);
      if (!saatGrup.has(h)) saatGrup.set(h, []);
      saatGrup.get(h).push(x);
    });

    const notlar = liste.filter((x) => x.not);
    kutu.innerHTML = `
      <div class="tarife" role="table" aria-label="${kacis(hat.no)} kalkış saatleri">
        ${[...saatGrup.entries()].map(([h, xs]) => `
          <div class="tarife-satir" role="row">
            <div class="tarife-saat" role="rowheader">${h}</div>
            <div class="tarife-dk" role="cell">
              ${xs.map((x) => {
                const gecti = bugunMu && dkCevir(x.s) < suan;
                const sinif = ["dk", gecti ? "gecti" : "", x.s === siradakiDk ? "siradaki" : "", x.not ? "notlu" : ""].join(" ");
                const aciklama = `${x.s}${gecti ? ", geçti" : ""}${x.s === siradakiDk ? ", sıradaki" : ""}`;
                return `<span class="${sinif}" title="${kacis(x.not || "")}" aria-label="${aciklama}">${x.s.slice(3)}</span>`;
              }).join("")}
            </div>
          </div>`).join("")}
      </div>
      ${notlar.length ? `<ul class="notlar">${notlar.map((x) => `<li><b>${x.s}</b> ${kacis(x.not)}</li>`).join("")}</ul>` : ""}`;
  }

  async function haritaCiz(hat) {
    const kutu = $("#harita");
    if (!kutu || !hat.duraklar?.length) return;
    if (!durum.duraklar) {
      try { durum.duraklar = await jsonAl("data/duraklar.json"); } catch { return; }
    }
    const noktalar = hat.duraklar
      .map((d) => ({ d, k: durum.duraklar[d.no] }))
      .filter((x) => x.k);
    if (noktalar.length < 2 || typeof L === "undefined" || !document.body.contains(kutu)) return;

    kutu.hidden = false;
    const harita = L.map(kutu, { scrollWheelZoom: false, attributionControl: true });
    durum.harita = harita;
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "© OpenStreetMap",
    }).addTo(harita);

    const latlng = noktalar.map((x) => [x.k[0], x.k[1]]);
    L.polyline(latlng, { color: "#F2B705", weight: 6, opacity: 0.9 }).addTo(harita);
    noktalar.forEach((x, i) => {
      const uc = i === 0 || i === noktalar.length - 1;
      L.circleMarker([x.k[0], x.k[1]], {
        radius: uc ? 7 : 5, color: "#13233A", weight: 3, fillColor: uc ? "#13233A" : "#fff", fillOpacity: 1,
      }).bindTooltip(`${baslikHarf(x.d.ad)} (${x.d.no})`).addTo(harita);
    });
    harita.fitBounds(latlng, { padding: [24, 24] });
  }

  function temizle() {
    clearInterval(durum.sayacZamanlayici);
    if (durum.harita) { durum.harita.remove(); durum.harita = null; }
  }

  // ---------- Yönlendirme (#/hat/442) ----------

  function yonlendir() {
    const h = location.hash.replace(/^#/, "");
    const hatEsles = h.match(/^\/hat\/([^?]+)/);
    if (hatEsles) {
      const gun = new URLSearchParams(h.split("?")[1] || "").get("gun");
      hatSayfasi(decodeURIComponent(hatEsles[1]), GUNLER.some((g) => g.anahtar === gun) ? gun : null);
    } else {
      const q = new URLSearchParams(h.split("?")[1] || "").get("q") || "";
      if (!$("#ara")) anaSayfa(q);
    }
  }

  // ---------- Başlat ----------

  async function basla() {
    try {
      const veri = await jsonAl("data/hatlar.json");
      durum.hatlar = veri.hatlar || [];
      if (veri.ornek) $("#ornek-uyari").hidden = false;
      if (veri.guncelleme) {
        const t = new Date(veri.guncelleme);
        $("#guncelleme").textContent = `Son güncelleme: ${t.toLocaleString("tr-TR", { dateStyle: "long", timeStyle: "short" })}.`;
      }
    } catch {
      uygulama.innerHTML = `<div class="bos">Hat listesi yüklenemedi. Sayfayı yenilemeyi dene.</div>`;
      return;
    }
    window.addEventListener("hashchange", () => {
      if (!location.hash.startsWith("#/hat/")) uygulama.innerHTML = "";
      yonlendir();
    });
    yonlendir();
  }

  basla();
})();
