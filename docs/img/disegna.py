"""Animazioni illustrative per docs/TUTORIAL.md: telefono con Couch Cast a sinistra, TV con Kodi a destra.

Sono disegni, non registrazioni dell'app. Per rifarle (serve Pillow): python3 docs/img/disegna.py docs/img
"""
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
S = 2  # disegno a 2x e riduco: bordi morbidi
W, H = 760, 480
F = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
_fonts = {}


def font(size, bold=False):
    key = (size, bold)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(FB if bold else F, size * S)
    return _fonts[key]


# Colori tipo Material You (accento verde acqua dell'icona)
BG = "#EEF3F3"
ACCENT = "#1F6B78"
ON_ACCENT = "#FFFFFF"
TONAL = "#CDE7EC"
ON_TONAL = "#123A42"
OFF = "#E1E4E4"
ON_OFF = "#5A6366"
TEXT = "#1A1C1C"
MUTED = "#5F6667"
CARD = "#FFFFFF"
FIELD = "#E8EEEE"

PX, PY, PW, PH = 30, 18, 252, 444  # telefono
SX, SY, SW, SH = PX + 9, PY + 9, PW - 18, PH - 18  # schermo
TX, TY, TW, TH = 320, 70, 420, 250  # TV


def s(*v):
    return [int(x * S) for x in v]


class Frame:
    def __init__(self):
        self.im = Image.new("RGB", (W * S, H * S), BG)
        self.d = ImageDraw.Draw(self.im, "RGBA")

    def rect(self, x, y, w, h, fill, r=0, outline=None, width=1):
        self.d.rounded_rectangle(s(x, y, x + w, y + h), radius=r * S, fill=fill, outline=outline, width=width * S)

    def text(self, x, y, t, size=11, color=TEXT, bold=False, anchor="la"):
        self.d.text((x * S, y * S), t, font=font(size, bold), fill=color, anchor=anchor)

    def text_w(self, t, size=11, bold=False):
        return self.d.textlength(t, font=font(size, bold)) / S

    def dim(self, x, y, w, h, amount=0.45):
        box = tuple(s(x, y, x + w, y + h))
        region = self.im.crop(box)
        self.im.paste(Image.blend(region, Image.new("RGB", region.size, "#000000"), amount), box)
        self.d = ImageDraw.Draw(self.im, "RGBA")

    def done(self):
        return self.im.resize((W, H), Image.LANCZOS)


def power_icon(f, cx, cy, r, color):
    f.d.arc(s(cx - r, cy - r, cx + r, cy + r), start=-60, end=240, fill=color, width=2 * S)
    f.d.line(s(cx, cy - r - 1, cx, cy - 1), fill=color, width=2 * S)


# --- Telefono -----------------------------------------------------------------------------------------------------

def phone(f, address="", typing=False, page="google", cast="none", power_busy=False, progress=None):
    f.rect(PX, PY, PW, PH, "#202426", r=30)
    f.rect(SX, SY, SW, SH, "#FFFFFF", r=22)
    f.text(SX + 18, SY + 6, "9:41", 9, TEXT, bold=True)
    f.d.ellipse(s(SX + SW / 2 - 4, SY + 6, SX + SW / 2 + 4, SY + 14), fill="#202426")
    # barra in alto: indirizzo, ★, ⏻, Kodi
    y = SY + 22
    f.rect(SX + 6, y, 112, 28, FIELD, r=8)
    if address:
        shown = address if f.text_w(address, 10) < 102 else "…" + address[-15:]
        f.text(SX + 12, y + 8, shown + ("|" if typing else ""), 10, TEXT)
    else:
        f.text(SX + 12, y + 8, "Cerca o scrivi…", 10, MUTED)
    f.rect(SX + 122, y, 30, 28, TONAL, r=8)
    f.text(SX + 137, y + 14, "★", 13, ON_TONAL, anchor="mm")
    f.rect(SX + 156, y, 30, 28, ACCENT if power_busy else TONAL, r=8)
    power_icon(f, SX + 171, y + 15, 6, ON_ACCENT if power_busy else ON_TONAL)
    f.rect(SX + 190, y, 38, 28, TONAL, r=8)
    f.text(SX + 209, y + 14, "Kodi", 10, ON_TONAL, anchor="mm")
    if progress is not None:
        f.rect(SX + 2, y + 31, (SW - 4) * progress, 2, ACCENT)
    # pagina
    top, bottom = y + 34, SY + SH - 52
    page_draw = PAGES[page[0] if isinstance(page, tuple) else page]
    page_draw(f, SX, top, SW, bottom - top, *(page[1:] if isinstance(page, tuple) else ()))
    # barra in basso
    by = SY + SH - 48
    labels = {
        "none": ("Nessun video: avvia il player", OFF, ON_OFF),
        "ready": ("Manda a Kodi", ACCENT, ON_ACCENT),
        "sending": ("Invio a Kodi…", OFF, ON_OFF),
    }
    t, bg, fg = labels[cast]
    f.rect(SX + 6, by, 150, 36, bg, r=18)
    f.text(SX + 81, by + 18, t, 9 if cast == "none" else 11, fg, bold=cast == "ready", anchor="mm")
    active = cast == "ready"
    f.rect(SX + 162, by, 66, 36, TONAL if active else OFF, r=18)
    f.text(SX + 195, by + 18, "+ Coda", 11, ON_TONAL if active else ON_OFF, anchor="mm")


def page_google(f, x, y, w, h):
    f.text(x + w / 2, y + 60, "Google", 26, "#4A6FD0", bold=True, anchor="mm")
    f.rect(x + 22, y + 90, w - 44, 26, CARD, r=13, outline="#C9D0D0")


def page_results(f, x, y, w, h):
    rows = [("animeunity.so", "JoJo's Bizarre Adventure – Episodi"), ("wikipedia.org", "Le bizzarre avventure di JoJo"),
            ("youtube.com", "JoJo – Trailer ufficiale")]
    for i, (site, title) in enumerate(rows):
        ry = y + 14 + i * 56
        f.text(x + 14, ry, site, 8, MUTED)
        f.text(x + 14, ry + 13, title[:30], 10, "#1A47B8")
        f.rect(x + 14, ry + 30, w - 50, 4, "#E3E6E6", r=2)
        f.rect(x + 14, ry + 38, w - 80, 4, "#E3E6E6", r=2)


def page_episode(f, x, y, w, h, episode=1, state="poster"):
    f.rect(x, y, w, 26, "#1B1D2B")
    f.text(x + 10, y + 7, "AnimeUnity", 10, "#FFFFFF", bold=True)
    f.text(x + 12, y + 36, f"JoJo – Episodio {episode}", 12, TEXT, bold=True)
    vx, vy, vw, vh = x + 10, y + 58, w - 20, 120
    if state == "poster":
        f.rect(vx, vy, vw, vh, "#2B2F45")
        f.d.ellipse(s(vx + vw / 2 - 18, vy + vh / 2 - 18, vx + vw / 2 + 18, vy + vh / 2 + 18), fill="#FFFFFF")
        f.d.polygon(s(vx + vw / 2 - 6, vy + vh / 2 - 10, vx + vw / 2 - 6, vy + vh / 2 + 10, vx + vw / 2 + 11, vy + vh / 2),
                    fill="#2B2F45")
    else:
        scene(f, vx, vy, vw, vh, episode)
        f.rect(vx + 8, vy + vh - 10, vw - 16, 3, "#FFFFFF80")
        f.rect(vx + 8, vy + vh - 10, (vw - 16) * 0.08, 3, "#E53935")
    f.text(x + 12, y + 190, "Episodi", 10, TEXT, bold=True)
    for i in range(6):
        bx = x + 12 + i * 35
        sel = i + 1 == episode
        f.rect(bx, y + 206, 30, 24, "#3949AB" if sel else "#E3E6EE", r=6)
        f.text(bx + 15, y + 218, str(i + 1), 10, "#FFFFFF" if sel else TEXT, anchor="mm")


PAGES = {"google": page_google, "results": page_results, "episode": page_episode}


def scene(f, x, y, w, h, episode=1):
    """Un fotogramma "di cartone": cielo, sole e colline. Cambia un po' con l'episodio."""
    sky = ["#5C7FD6", "#D9825B", "#6C4FA8"][(episode - 1) % 3]
    f.rect(x, y, w, h, sky)
    f.d.ellipse(s(x + w * 0.68, y + h * 0.16, x + w * 0.68 + h * 0.22, y + h * 0.38), fill="#FFE08A")
    f.d.polygon(s(x, y + h, x, y + h * 0.62, x + w * 0.3, y + h * 0.42, x + w * 0.62, y + h * 0.7, x + w, y + h * 0.5, x + w, y + h),
                fill="#2F5D50")
    f.d.polygon(s(x, y + h, x + w * 0.2, y + h * 0.78, x + w * 0.55, y + h * 0.88, x + w, y + h * 0.74, x + w, y + h),
                fill="#1F3F37")


def toast(f, msg):
    w = f.text_w(msg, 10) + 24
    x = SX + (SW - w) / 2
    y = SY + SH - 86
    f.rect(x, y, w, 26, "#303436", r=13)
    f.text(x + w / 2, y + 13, msg, 10, "#FFFFFF", anchor="mm")


def tap(f, x, y, pressed=True):
    r = 13 if pressed else 17
    f.d.ellipse(s(x - r, y - r, x + r, y + r), fill="#9FC4CB" if pressed else None, outline="#1F6B78", width=2 * S)
    f.d.ellipse(s(x - 4, y - 4, x + 4, y + 4), fill="#1F6B78")


# --- Dialoghi -----------------------------------------------------------------------------------------------------

def dialog(f, title, height, buttons=(), y_off=None):
    f.dim(SX, SY, SW, SH)
    x, w = SX + 12, SW - 24
    y = y_off if y_off is not None else SY + (SH - height) / 2
    f.rect(x, y, w, height, CARD, r=18)
    f.text(x + 16, y + 16, title, 14, TEXT, bold=True)
    bx = x + w - 14
    pos = {}
    for b in reversed(buttons):
        bw = f.text_w(b, 11, True) + 16
        bx -= bw
        f.text(bx + bw / 2, y + height - 20, b, 11, ACCENT, bold=True, anchor="mm")
        pos[b] = (bx + bw / 2, y + height - 20)
        bx -= 4
    return x, y, w, pos


def dialog_kodi(f, step):
    x, y, w, pos = dialog(f, "Dov'è Kodi?", 300, ("Annulla", "Salva"))
    # schede
    f.rect(x + 16, y + 44, (w - 32) / 2, 28, ACCENT, r=14)
    f.text(x + 16 + (w - 32) / 4, y + 58, "Automatico", 10, ON_ACCENT, bold=True, anchor="mm")
    f.rect(x + 16 + (w - 32) / 2, y + 44, (w - 32) / 2, 28, TONAL, r=14)
    f.text(x + 16 + 3 * (w - 32) / 4, y + 58, "Manuale", 10, ON_TONAL, anchor="mm")
    if step == 0:
        f.text(x + 16, y + 86, "Cerco Kodi sul Wi-Fi…", 10, MUTED)
    else:
        f.text(x + 16, y + 86, "Trovato:", 10, MUTED)
        sel = step >= 2
        f.rect(x + 16, y + 102, w - 32, 34, TONAL if sel else FIELD, r=10, outline=ACCENT if sel else None, width=2)
        f.text(x + 28, y + 110, "Kodi (Fire TV)", 10, TEXT, bold=True)
        f.text(x + 28, y + 123, "192.168.1.50:8080", 8, MUTED)
    f.text(x + 16, y + 150, "Accesso, se impostato in Kodi", 9, MUTED)
    f.rect(x + 16, y + 166, w - 32, 26, FIELD, r=6)
    f.text(x + 24, y + 173, "kodi" if step >= 3 else "Utente", 10, TEXT if step >= 3 else MUTED)
    f.rect(x + 16, y + 198, w - 32, 26, FIELD, r=6)
    f.text(x + 24, y + 205, "••••••" if step >= 3 else "Password", 10, TEXT if step >= 3 else MUTED)
    f.text(x + 16, y + 236, "AnimeUnity: episodi successivi", 8, MUTED)
    f.rect(x + 16, y + 250, 36, 22, FIELD, r=6)
    f.text(x + 34, y + 261, "1", 10, TEXT, anchor="mm")
    return pos, (x + w / 2, y + 119)


def menu_kodi(f, highlight=None):
    items = ["Coda di Kodi", "Dov'è Kodi?", "Log di Kodi"]
    x, y, w = SX + SW - 128, SY + 54, 122
    f.rect(x + 2, y + 2, w, 96, "#00000030", r=10)
    f.rect(x, y, w, 96, CARD, r=10, outline="#D5DADA")
    pos = {}
    for i, t in enumerate(items):
        if t == highlight:
            f.rect(x + 4, y + 4 + i * 30, w - 8, 28, TONAL, r=6)
        f.text(x + 12, y + 18 + i * 30, t, 10, TEXT, anchor="lm")
        pos[t] = (x + 50, y + 18 + i * 30)
    return pos


def badge(f, cx, cy, label, strong):
    f.d.ellipse(s(cx - 12, cy - 12, cx + 12, cy + 12), fill=ACCENT if strong else TONAL)
    f.text(cx, cy, label, 10, ON_ACCENT if strong else ON_TONAL, bold=True, anchor="mm")


def dialog_queue(f, rows):
    x, y, w, pos = dialog(f, "Coda di Kodi", 112 + 48 * len(rows), ("Svuota", "Chiudi", "Aggiorna"))
    f.text(x + 16, y + 38, "Quando un video finisce Kodi passa al", 8, MUTED)
    f.text(x + 16, y + 49, "successivo.", 8, MUTED)
    for i, (name, note, playing) in enumerate(rows):
        ry = y + 68 + i * 48
        badge(f, x + 30, ry + 15, "▶" if playing else str(i + 1), playing)
        f.text(x + 50, ry + 3, name, 11, TEXT, bold=playing)
        f.text(x + 50, ry + 19, note, 8, MUTED)
    return pos


def dialog_favorites(f, rows, add=True):
    h = 80 + 40 * len(rows) + (44 if add else 0)
    x, y, w, pos = dialog(f, "Preferiti", h, ("Chiudi",))
    ry = y + 44
    if add:
        f.rect(x + 16, ry, w - 32, 34, TONAL, r=17)
        f.text(x + w / 2, ry + 17, "+ Aggiungi questa pagina", 10, ON_TONAL, bold=True, anchor="mm")
        pos["add"] = (x + w / 2, ry + 17)
        ry += 44
    for name in rows:
        f.text(x + 18, ry + 10, name, 11, TEXT)
        f.text(x + w - 22, ry + 10, "⋮", 13, MUTED)
        ry += 40
    return pos


def dialog_name(f, typed):
    x, y, w, pos = dialog(f, "Aggiungi ai preferiti", 130, ("Annulla", "Salva"))
    f.rect(x + 16, y + 48, w - 32, 28, FIELD, r=6)
    f.d.line(s(x + 16, y + 76, x + w - 16, y + 76), fill=ACCENT, width=2 * S)
    f.text(x + 24, y + 56, typed + "|", 11, TEXT)
    return pos


# --- TV -----------------------------------------------------------------------------------------------------------

def tv(f, state="off", episode=1, note=None):
    f.rect(TX + TW / 2 - 50, TY + TH + 2, 100, 10, "#2A2E30", r=4)
    f.rect(TX + TW / 2 - 8, TY + TH - 4, 16, 10, "#2A2E30")
    f.rect(TX, TY, TW, TH, "#16191A", r=10)
    x, y, w, h = TX + 8, TY + 8, TW - 16, TH - 16
    if state == "off":
        f.rect(x, y, w, h, "#0B0C0D", r=4)
        f.d.ellipse(s(TX + TW - 18, TY + TH - 6, TX + TW - 14, TY + TH - 2), fill="#C62828")
    elif state == "input":
        f.rect(x, y, w, h, "#0B0C0D", r=4)
        f.rect(x + 14, y + 14, 90, 24, "#2F3437", r=4)
        f.text(x + 59, y + 26, "HDMI 1", 11, "#FFFFFF", anchor="mm")
    elif state in ("kodi", "loading"):
        f.rect(x, y, w, h, "#0F2A44", r=4)
        f.rect(x, y, 90, h, "#0A1E31", r=4)
        for i, t in enumerate(["Film", "Serie TV", "Musica", "Add-on", "Impostazioni"]):
            f.text(x + 12, y + 40 + i * 24, t, 10, "#9DB6CC")
        f.text(x + w / 2 + 45, y + h / 2 - 10, "KODI", 30, "#FFFFFF", bold=True, anchor="mm")
        if state == "loading":
            f.text(x + w / 2 + 45, y + h / 2 + 26, "Caricamento…", 10, "#9DB6CC", anchor="mm")
    elif state == "playing":
        scene(f, x, y, w, h, episode)
        f.rect(x, y + h - 28, w, 28, "#00000088")
        f.text(x + 12, y + h - 20, f"JoJo – Episodio {episode}", 10, "#FFFFFF", bold=True)
        f.rect(x + 160, y + h - 15, w - 176, 3, "#FFFFFF66")
        f.rect(x + 160, y + h - 15, (w - 176) * 0.05, 3, "#2EA8E0")
    if note:
        nw = f.text_w(note, 10) + 20
        f.rect(x + w - nw - 10, y + 10, nw, 24, "#000000AA", r=6)
        f.text(x + w - nw / 2 - 10, y + 22, note, 10, "#FFFFFF", anchor="mm")
    f.text(TX + TW / 2, TY + TH + 30, "TV con Fire TV e Kodi", 10, MUTED, anchor="mm")


def caption(f, t):
    f.text(TX + TW / 2, TY - 34, t, 13, TEXT, bold=True, anchor="mm")


# --- Animazioni ---------------------------------------------------------------------------------------------------

def render(name, steps):
    frames, durations = [], []
    for draw, ms in steps:
        f = Frame()
        draw(f)
        frames.append(f.done().quantize(colors=128, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE))
        durations.append(ms)
    frames[0].save(f"{OUT}/{name}.gif", save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)


def step(caption_text, tv_state="kodi", tv_episode=1, tv_note=None, after=None, **ph):
    def draw(f):
        phone(f, **ph)
        if after:
            after(f)
        tv(f, tv_state, tv_episode, tv_note)
        caption(f, caption_text)
    return draw


def both(*fns):
    def run(f):
        for fn in fns:
            fn(f)
    return run


def configura():
    def kodi(stepn, tap_at=None, label="Salva"):
        def a(f):
            pos, row = dialog_kodi(f, stepn)
            if tap_at == "row":
                tap(f, *row)
            elif tap_at == "save":
                tap(f, *pos[label])
        return a
    c1 = "1. Apri Kodi sulla TV, poi Couch Cast"
    c2 = "2. Tocca il Kodi trovato (utente e password se servono)"
    c3 = "3. Salva: l'app ora sa dov'è Kodi"
    render("configura", [
        (step(c1, page="google"), 900),
        (step(c1, page="google", after=kodi(0)), 1300),
        (step(c2, page="google", after=kodi(1)), 900),
        (step(c2, page="google", after=kodi(1, "row")), 500),
        (step(c2, page="google", after=kodi(2)), 700),
        (step(c2, page="google", after=kodi(3)), 1100),
        (step(c3, page="google", after=kodi(3, "save")), 500),
        (step(c3, page="google"), 1600),
    ])


def manda():
    c1 = "1. Cerca il sito dalla barra in alto"
    c2 = "2. Apri l'episodio e avvia il player"
    c3 = "3. Il pulsante si colora: Manda a Kodi"
    c4 = "4. Il video parte sulla TV"
    seq = []
    q = "jojo animeunity"
    seq.append((step(c1, page="google", after=lambda f: tap(f, SX + 60, SY + 36)), 600))
    for n in (4, 9, 15):
        seq.append((step(c1, page="google", address=q[:n], typing=True), 280))
    seq.append((step(c1, page="google", address=q, typing=True, progress=0.5), 350))
    seq.append((step(c1, page="results", address="google.com/search?q=jojo…"), 900))
    seq.append((step(c1, page="results", address="google.com/search?q=jojo…", after=lambda f: tap(f, SX + 70, SY + 76)), 500))
    ep = "animeunity.so/anime/jojo/1"
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep, progress=0.7), 400))
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep), 900))
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep, after=lambda f: tap(f, SX + SW / 2, SY + 56 + 60 + 60)), 500))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, cast="ready"), 1300))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, cast="ready", after=lambda f: tap(f, SX + 81, SY + SH - 30)), 500))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, cast="sending", tv_state="loading"), 1000))
    seq.append((step(c4, page=("episode", 1, "play"), address=ep, cast="ready", tv_state="playing",
                     after=lambda f: toast(f, "Inviato a Kodi")), 1400))
    seq.append((step(c4 + ": il telefono si può bloccare", page=("episode", 1, "play"), address=ep, cast="ready",
                     tv_state="playing"), 1800))
    render("manda-a-kodi", seq)


def coda():
    ep2 = "animeunity.so/anime/jojo/2"
    rows = [("JoJo – Episodio 1", "▶ In riproduzione", True), ("JoJo – Episodio 2", "Parte tra circa 22 min", False)]
    c1 = "1. Sulla TV va l'episodio 1. Apri il 2 e avvia il player"
    c2 = "2. + Coda: va in fondo, l'episodio 1 continua"
    c3 = "3. Kodi → Coda di Kodi per vedere la coda"
    c4 = "4. Finito l'1, Kodi passa da solo al 2"
    common = dict(address=ep2, tv_state="playing")
    render("coda", [
        (step(c1, page=("episode", 2, "poster"), **common), 1200),
        (step(c1, page=("episode", 2, "poster"), after=lambda f: tap(f, SX + SW / 2, SY + 176), **common), 500),
        (step(c2, page=("episode", 2, "play"), cast="ready", **common), 900),
        (step(c2, page=("episode", 2, "play"), cast="ready", after=lambda f: tap(f, SX + 195, SY + SH - 30), **common), 500),
        (step(c2, page=("episode", 2, "play"), cast="ready", after=lambda f: toast(f, "Aggiunto alla coda"), **common), 1500),
        (step(c3, page=("episode", 2, "play"), cast="ready", after=lambda f: tap(f, SX + 209, SY + 36), **common), 500),
        (step(c3, page=("episode", 2, "play"), cast="ready", after=lambda f: menu_kodi(f), **common), 600),
        (step(c3, page=("episode", 2, "play"), cast="ready",
              after=lambda f: tap(f, *menu_kodi(f, "Coda di Kodi")["Coda di Kodi"]), **common), 500),
        (step(c3, page=("episode", 2, "play"), cast="ready", after=lambda f: dialog_queue(f, rows), **common), 2600),
        (step(c4, page=("episode", 2, "play"), cast="ready", tv_episode=2, **common,
              after=lambda f: dialog_queue(f, [("JoJo – Episodio 1", "Già visto", False),
                                               ("JoJo – Episodio 2", "▶ In riproduzione", True)])), 2400),
    ])


def accendi():
    c1 = "1. TV spenta: premi il pulsante di accensione"
    c2 = "2. L'app trova la Fire TV e la sveglia"
    c3 = "3. La TV passa sull'ingresso giusto e apre Kodi"
    g = dict(page="google")
    render("accendi", [
        (step(c1, tv_state="off", **g), 1200),
        (step(c1, tv_state="off", after=lambda f: tap(f, SX + 171, SY + 37), **g), 500),
        (step(c2, tv_state="off", power_busy=True, after=lambda f: toast(f, "Accendo la TV…"), **g), 1100),
        (step(c2, tv_state="input", power_busy=True, after=lambda f: toast(f, "Accendo la TV…"), **g), 1200),
        (step(c3, tv_state="kodi", power_busy=True, **g), 1200),
        (step(c3, tv_state="kodi", after=lambda f: toast(f, "Kodi è pronto"), **g), 2000),
    ])


def preferiti():
    ep = "animeunity.so/anime/jojo"
    c1 = "1. Sulla pagina da salvare premi ★"
    c2 = "2. Aggiungi questa pagina e scegli un nome"
    c3 = "3. La prossima volta: ★ e tocca il nome"
    pg = dict(page=("episode", 1, "poster"), address=ep)
    seq = [
        (step(c1, after=lambda f: tap(f, SX + 137, SY + 36), **pg), 600),
        (step(c1, after=lambda f: dialog_favorites(f, ["StreamingCommunity"]), **pg), 900),
        (step(c2, after=lambda f: tap(f, *dialog_favorites(f, ["StreamingCommunity"])["add"]), **pg), 500),
    ]
    for t in ("Ani", "AnimeUni", "AnimeUnity"):
        seq.append((step(c2, after=lambda f, t=t: dialog_name(f, t), **pg), 300))
    seq.append((step(c2, after=lambda f: tap(f, *dialog_name(f, "AnimeUnity")["Salva"]), **pg), 500))
    seq.append((step(c2, after=lambda f: toast(f, "Aggiunto ai preferiti"), **pg), 1200))
    g = dict(page="google")
    seq.append((step(c3, after=lambda f: tap(f, SX + 137, SY + 36), **g), 500))
    favs = ["StreamingCommunity", "AnimeUnity"]
    seq.append((step(c3, after=lambda f: dialog_favorites(f, favs, add=False), **g), 900))
    seq.append((step(c3, after=lambda f: both(lambda f: dialog_favorites(f, favs, add=False),
                                              lambda f: tap(f, SX + 80, SY + SH / 2 + 4))(f), **g), 500))
    seq.append((step(c3, **pg), 1600))
    render("preferiti", seq)


for fn in (configura, manda, coda, accendi, preferiti):
    fn()
