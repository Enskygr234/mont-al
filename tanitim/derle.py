"""kaynak.html -> index.html: fontları ve simgeleri gömer."""
import base64
from pathlib import Path
K = Path(__file__).parent
f = lambda n: base64.b64encode((K / "fonts" / n).read_bytes()).decode()

def mont(boy, fermuar):
    return (f'<svg width="{boy}" height="{boy}" viewBox="0 0 108 108" aria-hidden="true">'
            '<path d="M54,36 v-4 a4,4 0 1 1 4,-4" stroke="#fff" stroke-width="3" fill="none"/>'
            '<path fill="#FFD54A" d="M54,36 L40,40 L30,52 L32,74 L38,74 L38,56 L40,78 L68,78 L70,56 L70,74 L76,74 L78,52 L68,40 Z"/>'
            f'<path fill="{fermuar}" d="M53,42 h2 v36 h-2 z"/><path fill="{fermuar}" d="M46,38 L54,46 L62,38 L58,37 L54,41 L50,37 Z"/></svg>')

def ik(govde, boy=20):
    return f'<svg width="{boy}" height="{boy}" viewBox="0 0 24 24" aria-hidden="true">{govde}</svg>'

IKON = {
    "YAGMUR": ik('<path fill="#fff" d="M7 14h10.5a4 4 0 0 0 .4-8A6 6 0 0 0 6.3 5.2 4.5 4.5 0 0 0 7 14z"/><path stroke="#BFE0FF" stroke-width="2" stroke-linecap="round" d="M8 17l-1 3M12 17l-1 3M16 17l-1 3"/>'),
    "BULUT": ik('<path fill="#fff" d="M7 18h10.5a4 4 0 0 0 .4-8A6 6 0 0 0 6.3 9.2 4.5 4.5 0 0 0 7 18z"/>'),
    "PARCALI": ik('<circle cx="8" cy="8" r="4" fill="#FFE08A"/><path fill="#fff" d="M9 20h9a3.5 3.5 0 0 0 .3-7A5 5 0 0 0 8.6 12.3 4 4 0 0 0 9 20z"/>'),
    "FIRTINA": ik('<path fill="#fff" d="M7 13h10.5a4 4 0 0 0 .4-8A6 6 0 0 0 6.3 4.2 4.5 4.5 0 0 0 7 13z"/><path fill="#FFD54A" d="M12 13l-3 5h3l-1 4 4-6h-3l1-3z"/>', 28),
    "KAR": ik('<path fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" d="M12 2v20M3.3 7l17.4 10M3.3 17L20.7 7M9 4l3 2 3-2M9 20l3-2 3 2"/>', 28),
    "RUZGAR": ik('<path fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" d="M3 8h11a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 16h8"/>', 28),
    "DON": ik('<path fill="none" stroke="#BFE0FF" stroke-width="2" stroke-linecap="round" d="M12 3v18M5 7l14 10M5 17L19 7"/><circle cx="12" cy="12" r="2.5" fill="#BFE0FF"/>', 28),
    "GUNES": ik('<circle cx="12" cy="12" r="4.5" fill="#FFE08A"/><path fill="none" stroke="#FFE08A" stroke-width="2" stroke-linecap="round" d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>', 28),
}

s = (K / "kaynak.html").read_text()
s = s.replace("{{BS}}", f("bs.woff")).replace("{{IR}}", f("ir.woff")).replace("{{IB}}", f("ib.woff"))
s = s.replace("{{MONT_KUCUK}}", mont(40, "#2E5E8C")).replace("{{MONT_MINI}}", mont(24, "#2E5E8C")).replace("{{MONT_MINI_SARI}}", mont(24, "#3A2C00"))
# Telefondaki yağmur simgesi küçük, uyarı listesindeki büyük
s = s.replace('<div class="dr">{{IKON_YAGMUR}}', '<div class="dr">' + IKON["YAGMUR"])
s = s.replace("{{IKON_YAGMUR}}<span class=\"ad\">", ik(IKON["YAGMUR"][IKON["YAGMUR"].index(">")+1:-6], 28) + '<span class="ad">')
for k, v in IKON.items():
    s = s.replace("{{IKON_" + k + "}}", v)
s = s.replace('<span>📍 Ankara ▾</span>', '<span style="display:flex;align-items:center;gap:6px">' + ik('<path fill="#fff" d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/>', 18) + 'Ankara ▾</span>')
assert "{{" not in s, s[s.index("{{"):s.index("{{")+40]
(K / "index.html").write_text(s)
print("index.html", len(s) // 1024, "KB")
