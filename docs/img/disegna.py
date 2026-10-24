"""Animazioni illustrative per docs/TUTORIAL.md: telefono con Couch Cast a sinistra, TV con Kodi a destra.

Sono disegni, non registrazioni dell'app: seguono l'interfaccia della 2.1 (barra a pillola con ★ e ⋮, pagina
iniziale, "Manda a Kodi" galleggiante, barra di Kodi in fondo, telecomando a schermo intero con la scheda Coda).
Per rifarle (serve Pillow): python3 docs/img/disegna.py docs/img
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
CONTAINER = "#B9EAF4"  # "Manda a Kodi": contenitore dell'accento
ON_CONTAINER = "#002A31"
TONAL = "#CDE7EC"
ON_TONAL = "#123A42"
SURFACE = "#F7FAFA"  # sfondo dell'app e della pagina iniziale
PILL = "#E3EAEB"  # barra in alto e barra di Kodi
CARD = "#EBF0F0"  # riquadri della pagina iniziale
TEXT = "#1A1C1C"
MUTED = "#5F6667"
DOT_ON = "#2E8C9C"
DOT_OFF = "#A3ABAC"
FIELD = "#E8EEEE"
WHITE = "#FFFFFF"

PX, PY, PW, PH = 30, 18, 252, 444  # telefono
SX, SY, SW, SH = PX + 9, PY + 9, PW - 18, PH - 18  # schermo
TX, TY, TW, TH = 320, 70, 420, 250  # TV

BAR_Y = SY + 22  # barra in alto
PAGE_TOP = BAR_Y + 36
MINI_Y = SY + SH - 48  # barra di Kodi
STAR = (SX + SW - 44, BAR_Y + 15)
MORE = (SX + SW - 17, BAR_Y + 15)
KODI = "Kodi (Fire TV)"
HOST = "192.168.1.50:8080"


def s(*v):
    return [int(x * S) for x in v]


class Frame:
    def __init__(self):
        self.im = Image.new("RGB", (W * S, H * S), BG)
        self.d = ImageDraw.Draw(self.im, "RGBA")

    def rect(self, x, y, w, h, fill, r=0, outline=None, width=1):
        self.d.rounded_rectangle(s(x, y, x + w, y + h), radius=r * S, fill=fill, outline=outline, width=width * S)

    def circle(self, cx, cy, r, fill, outline=None, width=1):
        self.d.ellipse(s(cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width * S)

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


def fit(f, t, size, width, bold=False):
    while f.text_w(t, size, bold) > width and len(t) > 2:
        t = t[:-2] + "…" if not t.endswith("…") else t[:-2] + "…"
    return t


# --- Icone (come le Material dell'app, semplificate) --------------------------------------------------------------

def icon_power(f, cx, cy, r, color):
    f.d.arc(s(cx - r, cy - r, cx + r, cy + r), start=-60, end=240, fill=color, width=2 * S)
    f.d.line(s(cx, cy - r - 1, cx, cy - 1), fill=color, width=2 * S)


def icon_star(f, cx, cy, r, color, filled):
    import math
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts += [cx + rr * math.cos(a), cy + rr * math.sin(a)]
    if filled:
        f.d.polygon(s(*pts), fill=color)
    else:
        f.d.polygon(s(*pts), outline=color, width=int(1.5 * S))


def icon_more(f, cx, cy, color):
    for dy in (-5, 0, 5):
        f.circle(cx, cy + dy, 1.5, color)


def icon_play(f, cx, cy, r, color):
    f.d.polygon(s(cx - r * 0.6, cy - r, cx - r * 0.6, cy + r, cx + r, cy), fill=color)


def icon_pause(f, cx, cy, r, color):
    f.rect(cx - r * 0.75, cy - r, r * 0.55, r * 2, color)
    f.rect(cx + r * 0.2, cy - r, r * 0.55, r * 2, color)


def icon_remote(f, cx, cy, color):
    f.rect(cx - 4, cy - 7, 8, 14, None, r=2, outline=color, width=1)
    f.circle(cx, cy - 3, 1.4, color)
    f.rect(cx - 2, cy + 1, 4, 1, color)
    f.rect(cx - 2, cy + 3.5, 4, 1, color)


def icon_cast(f, x, y, color):
    f.rect(x, y, 14, 10, None, r=1.5, outline=color, width=1)
    f.d.arc(s(x - 3, y + 7, x + 3, y + 13), start=270, end=360, fill=color, width=S)
    f.circle(x, y + 10, 1, color)


def icon_queue(f, cx, cy, color):
    for i, w in enumerate((10, 10, 6)):
        f.rect(cx - 7, cy - 5 + i * 4, w, 1.4, color)
    f.rect(cx + 2.5, cy + 1.5, 6, 1.4, color)
    f.rect(cx + 4.8, cy - 0.8, 1.4, 6, color)


def icon_chevron(f, cx, cy, color):
    f.d.line(s(cx - 5, cy - 2, cx, cy + 3, cx + 5, cy - 2), fill=color, width=2 * S, joint="curve")


def icon_shield(f, cx, cy, color):
    f.d.polygon(s(cx - 4, cy - 4, cx, cy - 6, cx + 4, cy - 4, cx + 4, cy, cx, cy + 5, cx - 4, cy), outline=color, width=S)


# --- Telefono -----------------------------------------------------------------------------------------------------

def phone(f, address="", typing=False, page="home", cast="none", mini="idle", star=False, shield=0, progress=None,
          toast=None, home=None):
    """address: nome del sito come lo mostra la barra (o il testo che si sta scrivendo); cast: none, ready, sending;
    mini: None (Kodi non scelto), off, idle oppure ("playing", episodio, avanzamento)."""
    f.rect(PX, PY, PW, PH, "#202426", r=30)
    f.rect(SX, SY, SW, SH, SURFACE, r=22)
    f.text(SX + 18, SY + 6, "9:41", 9, TEXT, bold=True)
    f.circle(SX + SW / 2, SY + 10, 4, "#202426")
    # barra a pillola: icona del sito, indirizzo, scudo; poi ★ e ⋮
    y = BAR_Y
    pw = SW - 66
    f.rect(SX + 6, y, pw, 30, PILL, r=15)
    tx = SX + 16
    if address and not typing:
        f.circle(SX + 19, y + 15, 6, "#1B1D2B" if "anime" in address else "#4A6FD0")
        f.text(SX + 19, y + 15, address[0].upper(), 7, WHITE, bold=True, anchor="mm")
        tx = SX + 31
    room = pw - (tx - SX - 6) - 10 - (34 if shield else 0)
    if address:
        f.text(tx, y + 15, fit(f, address, 10, room) + ("|" if typing else ""), 10, TEXT, anchor="lm")
    else:
        f.text(tx, y + 15, "Cerca o scrivi un indirizzo", 9, MUTED, anchor="lm")
    if shield:
        sx = SX + 6 + pw - 38
        f.rect(sx, y + 6, 32, 18, None, r=9, outline="#A3ABAC")
        icon_shield(f, sx + 10, y + 15, MUTED)
        f.text(sx + 22, y + 15, str(shield), 8, MUTED, bold=True, anchor="mm")
    icon_star(f, *STAR, 7, ACCENT if star else TEXT, star)
    icon_more(f, *MORE, TEXT)
    if progress is not None:
        f.rect(SX + 2, y + 32, (SW - 4) * progress, 2, ACCENT)
    # pagina
    bottom = (MINI_Y - 4) if mini else SY + SH - 8
    if page == "home":
        page_home(f, SX, PAGE_TOP, SW, bottom - PAGE_TOP, mini, **(home or {}))
    else:
        draw = PAGES[page[0] if isinstance(page, tuple) else page]
        draw(f, SX, PAGE_TOP, SW, bottom - PAGE_TOP, *(page[1:] if isinstance(page, tuple) else ()))
    # avviso in fondo e, sopra, "Manda a Kodi" e "+ Coda" galleggianti (solo con un video)
    over = bottom - 8
    if toast:
        size = 8 if f.text_w(toast, 9) > SW - 40 else 9
        f.rect(SX + 10, over - 26, SW - 20, 26, "#303436", r=8)
        f.text(SX + 18, over - 13, toast, size, WHITE, anchor="lm")
        over -= 34
    if cast != "none":
        label = "Invio a Kodi…" if cast == "sending" else "Manda a Kodi"
        bw = f.text_w(label, 10, True) + 40
        bx, by = SX + SW - 10 - bw, over - 32
        f.rect(bx + 1, by + 2, bw, 32, "#00000022", r=10)
        f.rect(bx, by, bw, 32, CONTAINER if cast == "ready" else "#D3E6EA", r=10)
        icon_cast(f, bx + 11, by + 11, ON_CONTAINER)
        f.text(bx + 32, by + 16, label, 10, ON_CONTAINER, bold=True, anchor="lm")
        if cast == "ready":
            f.circle(bx - 20 + 1, by + 18, 14, "#00000022")
            f.circle(bx - 20, by + 16, 14, TONAL)
            icon_queue(f, bx - 20, by + 16, ON_TONAL)
    # barra di Kodi
    if mini:
        mini_player(f, mini)


def mini_positions():
    y = MINI_Y + 20
    return {"bar": (SX + SW / 2 - 20, y), "play": (SX + SW - 46, y), "remote": (SX + SW - 22, y)}


def mini_player(f, state):
    x, y, w, h = SX + 6, MINI_Y, SW - 12, 40
    f.rect(x, y, w, h, PILL, r=12)
    playing = isinstance(state, tuple)
    if playing:
        f.rect(x + 6, y + 6, 28, 28, ACCENT, r=7)
        icon_play(f, x + 20, y + 20, 6, "#DDF4F8")
        title = f"JoJo – Episodio {state[1]}"
        frac = state[2]
        total = 23 * 60 + 50
        t = int(total * frac)
        status = f"Su Kodi · {t // 60}:{t % 60:02d} di 23:50"
        dot = DOT_ON
    else:
        f.circle(x + 20, y + 20, 14, "#D4DCDD")
        icon_remote(f, x + 20, y + 20, MUTED)
        title = KODI
        status = "Acceso, niente in riproduzione" if state == "idle" else "Spento o non raggiungibile"
        dot = DOT_ON if state == "idle" else DOT_OFF
    f.text(x + 42, y + 13, fit(f, title, 10, w - 100, True), 10, TEXT, bold=True, anchor="lm")
    f.circle(x + 45, y + 28, 2.5, dot)
    f.text(x + 51, y + 28, fit(f, status, 8, w - 110), 8, MUTED, anchor="lm")
    pos = mini_positions()
    if playing:
        icon_pause(f, *pos["play"], 6, TEXT)
        f.d.rounded_rectangle(s(x + 8, y + h - 2.5, x + 8 + (w - 16) * frac, y + h - 0.5), radius=S, fill=ACCENT)
    elif state == "off":
        icon_power(f, *pos["play"], 6, TEXT)
    icon_remote(f, *pos["remote"], TEXT)


def page_home(f, x, y, w, h, mini, kodi_button=None, status=None, favorites=("SerieTV",), resume=True):
    f.rect(x, y, w, h, SURFACE)
    cx, cw = x + 10, w - 20
    # Kodi
    f.rect(cx, y + 6, cw, 50, CARD, r=12)
    f.circle(cx + 20, y + 31, 12, TONAL)
    icon_remote(f, cx + 20, y + 31, ON_TONAL)
    if mini is None:
        title, status, button = "Kodi non ancora scelto", status or "Scegli la Fire TV con Kodi", "Scegli"
    elif mini == "off":
        title, status, button = KODI, status or "Spento o non raggiungibile", "Accendi"
    elif mini == "idle":
        title, status, button = KODI, status or "Acceso, nessun video in corso", "Telecomando"
    else:
        title, status, button = KODI, status or f"Sta riproducendo: JoJo – Ep. {mini[1]}", "Telecomando"
    button = kodi_button or button
    bw = f.text_w(button, 8, True) + 16
    f.text(cx + 38, y + 24, fit(f, title, 9, cw - bw - 50, True), 9, TEXT, bold=True, anchor="lm")
    f.text(cx + 38, y + 38, fit(f, status, 7, cw - bw - 50), 7, MUTED, anchor="lm")
    f.rect(cx + cw - bw - 8, y + 21, bw, 20, ACCENT, r=10)
    f.text(cx + cw - bw / 2 - 8, y + 31, button, 8, ON_ACCENT, bold=True, anchor="mm")
    ty = y + 66
    if resume:
        f.text(cx + 2, ty, "RIPRENDI", 7, ACCENT, bold=True)
        f.rect(cx, ty + 12, cw, 36, CARD, r=12)
        f.circle(cx + 18, ty + 30, 10, "#1B1D2B")
        f.text(cx + 18, ty + 30, "A", 8, WHITE, bold=True, anchor="mm")
        f.text(cx + 36, ty + 24, "JoJo – Episodio 1", 9, TEXT, bold=True, anchor="lm")
        f.text(cx + 36, ty + 37, "animetv.example", 7, MUTED, anchor="lm")
        ty += 60
    f.text(cx + 2, ty, "PREFERITI", 7, ACCENT, bold=True)
    tiles = {}
    if not favorites:
        f.text(cx + 2, ty + 16, "Apri un sito e premi ★ in alto: lo ritrovi qui.", 7, MUTED)
    for i, name in enumerate(favorites):
        tx = cx + 4 + i * (cw / 4)
        mid = tx + cw / 8 - 2
        f.circle(mid, ty + 32, 15, "#1B1D2B" if name.startswith("Anime") else TONAL)
        f.text(mid, ty + 32, name[0], 11, WHITE if name.startswith("Anime") else ON_TONAL, bold=True, anchor="mm")
        f.text(mid, ty + 56, fit(f, name, 7, cw / 4 - 4), 7, MUTED, anchor="mm")
        tiles[name] = (mid, ty + 32)
    f.text(cx + 2, ty + 78, "Per mandare un video: apri l'episodio e", 7, MUTED)
    f.text(cx + 2, ty + 89, "avvia il player. \"Manda a Kodi\" compare da solo.", 7, MUTED)
    return tiles


def home_tiles(favorites, resume=True):
    """Dove sono le icone dei preferiti nella pagina iniziale (stessi conti di page_home)."""
    cw = SW - 20
    ty = PAGE_TOP + 66 + (60 if resume else 0)
    return {name: (SX + 10 + 4 + i * (cw / 4) + cw / 8 - 2, ty + 32) for i, name in enumerate(favorites)}


def page_google(f, x, y, w, h):
    f.rect(x, y, w, h, WHITE)
    f.text(x + w / 2, y + 60, "Google", 26, "#4A6FD0", bold=True, anchor="mm")
    f.rect(x + 22, y + 90, w - 44, 26, WHITE, r=13, outline="#C9D0D0")


def page_results(f, x, y, w, h):
    f.rect(x, y, w, h, WHITE)
    rows = [("animetv.example", "JoJo's Bizarre Adventure – Episodi"), ("wikipedia.org", "Le bizzarre avventure di JoJo"),
            ("youtube.com", "JoJo – Trailer ufficiale")]
    for i, (site, title) in enumerate(rows):
        ry = y + 14 + i * 56
        f.text(x + 14, ry, site, 8, MUTED)
        f.text(x + 14, ry + 13, title[:30], 10, "#1A47B8")
        f.rect(x + 14, ry + 30, w - 50, 4, "#E3E6E6", r=2)
        f.rect(x + 14, ry + 38, w - 80, 4, "#E3E6E6", r=2)


def page_episode(f, x, y, w, h, episode=1, state="poster"):
    f.rect(x, y, w, h, WHITE)
    f.rect(x, y, w, 26, "#1B1D2B")
    f.text(x + 10, y + 7, "AnimeTV", 10, WHITE, bold=True)
    f.text(x + 12, y + 36, f"JoJo – Episodio {episode}", 12, TEXT, bold=True)
    vx, vy, vw, vh = x + 10, y + 58, w - 20, 120
    if state == "poster":
        f.rect(vx, vy, vw, vh, "#2B2F45")
        f.circle(vx + vw / 2, vy + vh / 2, 18, WHITE)
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
        f.text(bx + 15, y + 218, str(i + 1), 10, WHITE if sel else TEXT, anchor="mm")


PAGES = {"google": page_google, "results": page_results, "episode": page_episode}
PLAYER_BUTTON = (SX + SW / 2, PAGE_TOP + 58 + 60)  # play al centro del video della pagina dell'episodio


def scene(f, x, y, w, h, episode=1):
    """Un fotogramma "di cartone": cielo, sole e colline. Cambia un po' con l'episodio."""
    sky = ["#5C7FD6", "#D9825B", "#6C4FA8"][(episode - 1) % 3]
    f.rect(x, y, w, h, sky)
    f.d.ellipse(s(x + w * 0.68, y + h * 0.16, x + w * 0.68 + h * 0.22, y + h * 0.38), fill="#FFE08A")
    f.d.polygon(s(x, y + h, x, y + h * 0.62, x + w * 0.3, y + h * 0.42, x + w * 0.62, y + h * 0.7, x + w, y + h * 0.5, x + w, y + h),
                fill="#2F5D50")
    f.d.polygon(s(x, y + h, x + w * 0.2, y + h * 0.78, x + w * 0.55, y + h * 0.88, x + w, y + h * 0.74, x + w, y + h),
                fill="#1F3F37")


def tap(f, x, y, pressed=True):
    r = 13 if pressed else 17
    f.d.ellipse(s(x - r, y - r, x + r, y + r), fill="#9FC4CB80" if pressed else None, outline=ACCENT, width=2 * S)
    f.circle(x, y, 4, ACCENT)


def cast_positions(toast=False):
    """Centro di "Manda a Kodi" e di "+ Coda" (stessi conti di phone, con la barra di Kodi)."""
    over = MINI_Y - 4 - 8 - (34 if toast else 0)
    f = Frame()
    bw = f.text_w("Manda a Kodi", 10, True) + 40
    bx, by = SX + SW - 10 - bw, over - 32
    return {"cast": (bx + bw / 2, by + 16), "queue": (bx - 20, by + 16)}


# --- Dialoghi e pannelli ------------------------------------------------------------------------------------------

def dialog(f, title, height, buttons=()):
    f.dim(SX, SY, SW, SH)
    x, w = SX + 12, SW - 24
    y = SY + (SH - height) / 2
    f.rect(x, y, w, height, WHITE, r=18)
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


def radio(f, cx, cy, on):
    f.circle(cx, cy, 5, None, outline=ACCENT if on else MUTED, width=1)
    if on:
        f.circle(cx, cy, 2.6, ACCENT)


def dialog_kodi(f, step):
    x, y, w, pos = dialog(f, "Dov'è Kodi?", 280, ("Annulla", "Salva"))
    radio(f, x + 22, y + 52, True)
    f.text(x + 32, y + 52, "Automatico", 10, TEXT, anchor="lm")
    radio(f, x + 112, y + 52, False)
    f.text(x + 122, y + 52, "Manuale", 10, TEXT, anchor="lm")
    if step == 0:
        f.d.arc(s(x + 16, y + 70, x + 30, y + 84), start=0, end=270, fill=ACCENT, width=2 * S)
        f.text(x + 38, y + 77, "Cerco Kodi sul Wi-Fi…", 10, MUTED, anchor="lm")
    else:
        f.text(x + 16, y + 77, "Trovato:", 10, MUTED, anchor="lm")
        sel = step >= 2
        radio(f, x + 22, y + 98, sel)
        f.text(x + 32, y + 98, HOST, 10, TEXT, anchor="lm")
    f.rect(x + 16, y + 114, 90, 22, TONAL, r=11)
    f.text(x + 61, y + 125, "Cerca di nuovo", 8, ON_TONAL, bold=True, anchor="mm")
    f.text(x + 16, y + 150, "Accesso, se impostato in Kodi", 9, MUTED)
    f.rect(x + 16, y + 166, w - 32, 26, FIELD, r=6)
    f.text(x + 24, y + 173, "kodi" if step >= 3 else "Utente", 10, TEXT if step >= 3 else MUTED)
    f.rect(x + 16, y + 198, w - 32, 26, FIELD, r=6)
    f.text(x + 24, y + 205, "••••••" if step >= 3 else "Password", 10, TEXT if step >= 3 else MUTED)
    return pos, (x + 60, y + 98)


def sheet(f, title, height):
    """Pannello dal basso: angoli in alto arrotondati, maniglia, titolo."""
    f.dim(SX, SY, SW, SH)
    x, y, w = SX, SY + SH - height, SW
    f.rect(x, y, w, height, WHITE, r=20)
    f.rect(x + w / 2 - 14, y + 7, 28, 3, "#C4CBCC", r=2)
    f.text(x + 16, y + 20, title, 13, TEXT, bold=True)
    return x, y, w


def sheet_favorites(f, rows, add=True):
    h = 70 + 40 * len(rows) + (44 if add else 0)
    x, y, w = sheet(f, "Preferiti", h)
    ry = y + 46
    pos = {}
    if add:
        f.rect(x + 16, ry, w - 32, 32, ACCENT, r=16)
        f.text(x + w / 2, ry + 16, "+ Aggiungi questa pagina", 10, ON_ACCENT, bold=True, anchor="mm")
        pos["add"] = (x + w / 2, ry + 16)
        ry += 44
    for name, site in rows:
        f.circle(x + 30, ry + 14, 12, "#1B1D2B" if name.startswith("Anime") else TONAL)
        f.text(x + 30, ry + 14, name[0], 9, WHITE if name.startswith("Anime") else ON_TONAL, bold=True, anchor="mm")
        f.text(x + 50, ry + 8, name, 10, TEXT, anchor="lm")
        f.text(x + 50, ry + 21, site, 8, MUTED, anchor="lm")
        icon_more(f, x + w - 20, ry + 14, MUTED)
        pos[name] = (x + 90, ry + 14)
        ry += 40
    return pos


def dialog_name(f, typed):
    x, y, w, pos = dialog(f, "Aggiungi ai preferiti", 130, ("Annulla", "Salva"))
    f.rect(x + 16, y + 48, w - 32, 28, FIELD, r=6)
    f.d.line(s(x + 16, y + 76, x + w - 16, y + 76), fill=ACCENT, width=2 * S)
    f.text(x + 24, y + 56, typed + "|", 11, TEXT)
    return pos


def player_queue(f, rows, offset=0.0):
    """Telecomando a schermo intero sulla scheda Coda. rows: (nome, nota, in corso, già visto)."""
    y0 = SY + 18 + offset
    f.rect(SX, y0, SW, SH - 18 - offset, SURFACE, r=20)
    icon_chevron(f, SX + 22, y0 + 18, TEXT)
    name_w = f.text_w(KODI, 9, True)
    f.text(SX + SW - 14, y0 + 18, KODI, 9, TEXT, bold=True, anchor="rm")
    f.circle(SX + SW - 20 - name_w, y0 + 18, 3, DOT_ON)
    # schede
    tx, tw = SX + 14, SW - 28
    f.rect(tx, y0 + 34, tw, 26, PILL, r=13)
    f.rect(tx + tw / 2, y0 + 34, tw / 2, 26, TONAL, r=13)
    f.text(tx + tw / 4, y0 + 47, "Telecomando", 9, MUTED, anchor="mm")
    waiting = sum(1 for r in rows if not r[2] and not r[3])
    f.text(tx + 3 * tw / 4, y0 + 47, f"Coda · {waiting}" if waiting else "Coda", 9, ON_TONAL, bold=True, anchor="mm")
    ry = y0 + 74
    for i, (name, note, playing, done) in enumerate(rows):
        if playing:
            f.rect(SX + 8, ry - 4, SW - 16, 42, "#DCEEF1", r=12)
            f.circle(SX + 30, ry + 17, 12, ACCENT)
            icon_play(f, SX + 31, ry + 17, 5, ON_ACCENT)
        else:
            f.circle(SX + 30, ry + 17, 12, "#E0E6E7")
            f.text(SX + 30, ry + 17, str(i + 1), 9, MUTED, bold=True, anchor="mm")
        f.text(SX + 50, ry + 10, name, 10, TEXT if not done else MUTED, bold=playing, anchor="lm")
        f.text(SX + 50, ry + 25, fit(f, note, 8, SW - 80), 8, ACCENT if playing else MUTED, anchor="lm")
        icon_more(f, SX + SW - 20, ry + 17, MUTED)
        ry += 50
    f.rect(SX + 14, ry + 6, 110, 26, None, r=13, outline="#A3ABAC")
    f.text(SX + 69, ry + 19, "Svuota la coda", 9, ACCENT, bold=True, anchor="mm")


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
        f.text(x + 59, y + 26, "HDMI 1", 11, WHITE, anchor="mm")
    elif state in ("kodi", "loading"):
        f.rect(x, y, w, h, "#0F2A44", r=4)
        f.rect(x, y, 90, h, "#0A1E31", r=4)
        for i, t in enumerate(["Film", "Serie TV", "Musica", "Add-on", "Impostazioni"]):
            f.text(x + 12, y + 40 + i * 24, t, 10, "#9DB6CC")
        f.text(x + w / 2 + 45, y + h / 2 - 10, "KODI", 30, WHITE, bold=True, anchor="mm")
        if state == "loading":
            f.text(x + w / 2 + 45, y + h / 2 + 26, "Caricamento…", 10, "#9DB6CC", anchor="mm")
    elif state == "playing":
        scene(f, x, y, w, h, episode)
        f.rect(x, y + h - 28, w, 28, "#00000088")
        f.text(x + 12, y + h - 20, f"JoJo – Episodio {episode}", 10, WHITE, bold=True)
        f.rect(x + 160, y + h - 15, w - 176, 3, "#FFFFFF66")
        f.rect(x + 160, y + h - 15, (w - 176) * 0.05, 3, "#2EA8E0")
    if note:
        nw = f.text_w(note, 10) + 20
        f.rect(x + w - nw - 10, y + 10, nw, 24, "#000000AA", r=6)
        f.text(x + w - nw / 2 - 10, y + 22, note, 10, WHITE, anchor="mm")
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
    def kodi(stepn, tap_at=None):
        def a(f):
            pos, row = dialog_kodi(f, stepn)
            if tap_at == "row":
                tap(f, *row)
            elif tap_at == "save":
                tap(f, *pos["Salva"])
        return a
    c1 = "1. Apri Kodi sulla TV, poi Couch Cast"
    c2 = "2. Tocca il Kodi trovato (utente e password se servono)"
    c3 = "3. Salva: in fondo compare la barra di Kodi"
    first = dict(mini=None, home=dict(resume=False, favorites=()))
    render("configura", [
        (step(c1, **first), 900),
        (step(c1, after=kodi(0), **first), 1300),
        (step(c2, after=kodi(1), **first), 900),
        (step(c2, after=kodi(1, "row"), **first), 500),
        (step(c2, after=kodi(2), **first), 700),
        (step(c2, after=kodi(3), **first), 1100),
        (step(c3, after=kodi(3, "save"), **first), 500),
        (step(c3, mini="idle", home=dict(resume=False, favorites=())), 2200),
    ])


def manda():
    c1 = "1. Cerca il sito dalla barra in alto"
    c2 = "2. Apri l'episodio e avvia il player"
    c3 = "3. Compare Manda a Kodi: premilo"
    c4 = "4. Il video parte sulla TV"
    seq = []
    q = "jojo episodio 1"
    seq.append((step(c1, after=lambda f: tap(f, SX + 80, BAR_Y + 15)), 600))
    for n in (4, 9, 15):
        seq.append((step(c1, address=q[:n], typing=True), 280))
    seq.append((step(c1, page="results", address=q, typing=True, progress=0.5), 350))
    seq.append((step(c1, page="results", address="google.com"), 900))
    seq.append((step(c1, page="results", address="google.com", after=lambda f: tap(f, SX + 70, PAGE_TOP + 20)), 500))
    ep = "animetv.example"
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep, shield=2, progress=0.7), 400))
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep, shield=2), 900))
    seq.append((step(c2, page=("episode", 1, "poster"), address=ep, shield=2, after=lambda f: tap(f, *PLAYER_BUTTON)), 500))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, shield=2, cast="ready"), 1300))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, shield=2, cast="ready",
                     after=lambda f: tap(f, *cast_positions()["cast"])), 500))
    seq.append((step(c3, page=("episode", 1, "play"), address=ep, shield=2, cast="sending", tv_state="loading"), 1000))
    seq.append((step(c4, page=("episode", 1, "play"), address=ep, shield=2, cast="ready", tv_state="playing",
                     mini=("playing", 1, 0.01), toast="Inviato a Kodi"), 1400))
    seq.append((step(c4 + ": il telefono si può bloccare", page=("episode", 1, "play"), address=ep, shield=2,
                     cast="ready", tv_state="playing", mini=("playing", 1, 0.03)), 1800))
    render("manda-a-kodi", seq)


def coda():
    ep = "animetv.example"
    c1 = "1. Sulla TV va l'episodio 1. Apri il 2 e avvia il player"
    c2 = "2. + Coda: va in fondo, l'episodio 1 continua"
    c3 = "3. Tocca la barra di Kodi, scheda Coda"
    c4 = "4. Finito l'1, Kodi passa da solo al 2"
    common = dict(address=ep, tv_state="playing")
    rows1 = [("JoJo – Episodio 1", "In riproduzione · finisce alle 21:52", True, False),
             ("JoJo – Episodio 2", "Parte alle 21:52", False, False)]
    rows2 = [("JoJo – Episodio 1", "Già visto", False, True),
             ("JoJo – Episodio 2", "In riproduzione · finisce alle 22:16", True, False)]
    toast = "Aggiunto alla coda (la vedi in ⋮ → Coda di Kodi)"
    render("coda", [
        (step(c1, page=("episode", 2, "poster"), mini=("playing", 1, 0.2), **common), 1200),
        (step(c1, page=("episode", 2, "poster"), mini=("playing", 1, 0.2), after=lambda f: tap(f, *PLAYER_BUTTON), **common), 500),
        (step(c2, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.21), **common), 900),
        (step(c2, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.21),
              after=lambda f: tap(f, *cast_positions()["queue"]), **common), 500),
        (step(c2, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.22), toast=toast, **common), 1700),
        (step(c3, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.23),
              after=lambda f: tap(f, *mini_positions()["bar"]), **common), 500),
        (step(c3, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.23),
              after=lambda f: player_queue(f, rows1, offset=120), **common), 200),
        (step(c3, page=("episode", 2, "play"), cast="ready", mini=("playing", 1, 0.23),
              after=lambda f: player_queue(f, rows1), **common), 2600),
        (step(c4, page=("episode", 2, "play"), cast="ready", mini=("playing", 2, 0.01), tv_episode=2,
              after=lambda f: player_queue(f, rows2), **common), 2600),
    ])


def accendi():
    c1 = "1. TV spenta: premi l'accensione nella barra di Kodi"
    c2 = "2. L'app trova la Fire TV e la sveglia"
    c3 = "3. La TV passa sull'ingresso giusto e apre Kodi"
    render("accendi", [
        (step(c1, tv_state="off", mini="off"), 1200),
        (step(c1, tv_state="off", mini="off", after=lambda f: tap(f, *mini_positions()["play"])), 500),
        (step(c2, tv_state="off", mini="off", toast="Accendo la TV…"), 1100),
        (step(c2, tv_state="input", mini="off", toast="Cerco la Fire TV in rete…"), 1200),
        (step(c3, tv_state="kodi", mini="off", toast="Cerco la Fire TV in rete…"), 1000),
        (step(c3, tv_state="kodi", mini="idle", toast=f"Kodi è pronto a {HOST}"), 2200),
    ])


def preferiti():
    ep = "animetv.example"
    c1 = "1. Sulla pagina da salvare premi ★"
    c2 = "2. Aggiungi questa pagina e scegli un nome"
    c3 = "3. Lo ritrovi nella pagina iniziale: toccalo"
    pg = dict(page=("episode", 1, "poster"), address=ep)
    old = [("SerieTV", "serietv.example")]
    seq = [
        (step(c1, after=lambda f: tap(f, *STAR), **pg), 600),
        (step(c1, after=lambda f: sheet_favorites(f, old), **pg), 900),
        (step(c2, after=lambda f: tap(f, *sheet_favorites(f, old)["add"]), **pg), 500),
    ]
    for t in ("Ani", "Anime", "AnimeTV"):
        seq.append((step(c2, after=lambda f, t=t: dialog_name(f, t), **pg), 300))
    seq.append((step(c2, after=lambda f: tap(f, *dialog_name(f, "AnimeTV")["Salva"]), **pg), 500))
    seq.append((step(c2, star=True, toast="Aggiunto ai preferiti", **pg), 1200))
    favs = ("SerieTV", "AnimeTV")
    home = dict(home=dict(favorites=favs))
    seq.append((step(c3, **home), 900))
    seq.append((step(c3, after=lambda f: tap(f, *home_tiles(favs)["AnimeTV"]), **home), 500))
    seq.append((step(c3, star=True, **pg), 1600))
    render("preferiti", seq)


for fn in (configura, manda, coda, accendi, preferiti):
    fn()
