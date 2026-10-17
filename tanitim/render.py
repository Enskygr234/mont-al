"""Tanıtım videosunu kare kare çizip MP4'e çevirir.
Kullanım: python render.py video.html cikti.mp4"""
import asyncio, subprocess, sys
from pathlib import Path
from playwright.async_api import async_playwright

FPS = 30

async def main(html, cikti):
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": 1080, "height": 1920})
        await pg.goto(Path(html).resolve().as_uri())
        await pg.evaluate("document.fonts.ready")
        sure = await pg.evaluate("window.SURE")
        ff = subprocess.Popen([
            "ffmpeg", "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(FPS), "-i", "-",
            "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", cikti], stdin=subprocess.PIPE)
        toplam = int(sure * FPS)
        for i in range(toplam):
            await pg.evaluate(f"ciz({i / FPS})")
            ff.stdin.write(await pg.screenshot(type="png"))
            if i % 90 == 0:
                print(f"{i}/{toplam}", flush=True)
        ff.stdin.close()
        ff.wait()
        await b.close()

asyncio.run(main(sys.argv[1], sys.argv[2]))
