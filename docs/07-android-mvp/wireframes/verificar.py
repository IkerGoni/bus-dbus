"""Verificación de los wireframes Android. Uso, desde la raíz del repo: python3 docs/07-android-mvp/wireframes/verificar.py

1. JSON válido, ids únicos, containerId/boundElements resueltos, textos dentro de su frame.
2. Ejemplos contra apps/bot/data/dbus-catalog.json: paradas (nombre ↔ código), líneas
   (existen y pasan por la parada del contexto) y destinos (de esa línea en esa parada).
3. Lo mismo para stitch-references/inicio/code.html.
"""
import glob
import json
import math
import re
import sys

ROOT = (sys.argv[1] if len(sys.argv) > 1 else ".").rstrip("/")
cat = json.load(open(f"{ROOT}/apps/bot/data/dbus-catalog.json"))
coords = {s["cod"]: (s["lat"], s["lon"]) for s in json.load(open(f"{ROOT}/apps/bot/data/dbus-stop-coordinates.json"))["stops"]}
S = {s["cod"]: s for s in cat["stops"]}
L = {l["cod"]: l for l in cat["lines"]}
NAME2CODE = {}
for s in cat["stops"]:
    NAME2CODE.setdefault(s["desc"], set()).add(s["cod"])
# destinos válidos de (línea, parada) según los itinerarios; y de la línea en general
DEST_AT = {}
DEST_LINE = {c: set(l["destinations"]) for c, l in L.items()}
for it in cat["itineraries"]:
    DEST_LINE.setdefault(it["lineCod"], set()).add(it["destination"])
    for st in it["stops"]:
        DEST_AT.setdefault((it["lineCod"], st), set()).add(it["destination"])

errors, checked = [], {"paradas": 0, "lineas": 0, "destinos": 0, "distancias": 0, "itinerarios": 0}
LINE_RE = r"(?:\d{1,2}|B\d{1,2})"
STOP_RE = re.compile(r"([A-ZÁÉÍÓÚÑ][\wÁÉÍÓÚÑáéíóúñü./ -]*?[\wáéíóúñ])\s+[\(\[](\d{1,4})[\)\]]")
NOT_STOP = {"RECORRIDO", "ACTIVOS", "PAUSADOS"}
SEP = r"(?=\s{2,}|\s*·|\s*🕒|\s*●|\s*★|\s*☆|\s*›|\s*\n|\s*$|\s+\d+ min|\s+en \d)"


def err(where, msg):
    errors.append(f"{where}: {msg}")


def haversine(a, b):
    R = 6_371_000
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return round(2 * R * math.atan2(math.sqrt(h), math.sqrt(1 - h)))


def check_stop(where, name, code):
    if name.split()[-1] in NOT_STOP or name.startswith(("Versión", "Tiempos")):
        return None
    checked["paradas"] += 1
    if code not in S:
        err(where, f"parada {code} no existe ({name!r})")
        return None
    if S[code]["desc"] != name.strip():
        err(where, f"parada {code} es {S[code]['desc']!r}, no {name!r}")
    return code


def check_line(where, line, stop):
    checked["lineas"] += 1
    if line not in L:
        err(where, f"línea {line} no existe")
    elif stop and line not in S[stop]["lines"]:
        err(where, f"la línea {line} no pasa por {stop} {S[stop]['desc']}")


def check_dest(where, line, dest, stop):
    dest = dest.strip()
    if not re.match(r"[A-Za-zÁÉÍÓÚÑáéíóúñ]", dest) or dest.startswith("sin "):
        return
    if not dest or dest in ("Cualquier destino",) or line not in L:
        return
    checked["destinos"] += 1
    ok = DEST_AT.get((line, stop), set()) if stop else DEST_LINE.get(line, set())
    if dest not in ok:
        err(where, f"destino {dest!r} no es de L{line}" + (f" en {stop}" if stop else "") + f" (válidos: {sorted(ok)})")


def check_text(where, text, ctx):
    """ctx: parada de contexto (str|None). Devuelve la parada de contexto actualizada."""
    for m in STOP_RE.finditer(text):
        c = check_stop(where, m.group(1), m.group(2))
        if c:
            ctx = c
    # «Vigilando L16 en Pio Baroja 15»
    for m in re.finditer(rf"Vigilando L({LINE_RE}) en ([^»\n]+)", text):
        name = m.group(2).strip()
        checked["paradas"] += 1
        codes = NAME2CODE.get(name)
        if not codes:
            err(where, f"parada {name!r} no existe")
        else:
            check_line(where, m.group(1), sorted(codes)[0])
    # deep link
    for m in re.finditer(rf"dbus://stop/(\d+)\?highlightLine=({LINE_RE})", text):
        checked["paradas"] += 1
        if m.group(1) not in S:
            err(where, f"parada {m.group(1)} no existe")
        else:
            check_line(where, m.group(2), m.group(1))
    # L16 → Igeldo
    for m in re.finditer(rf"\bL({LINE_RE}) → (.+?){SEP}", text):
        check_line(where, m.group(1), ctx)
        check_dest(where, m.group(1), m.group(2), ctx)
    # L28 6' (favoritas compactas)
    for m in re.finditer(rf"\bL({LINE_RE})\b(?! →)", text):
        check_line(where, m.group(1), ctx)
    # [16] Igeldo / [✓ 16] / [16*]
    for m in re.finditer(rf"\[(?:✓ ?)?({LINE_RE})\*?\](?: +([^\[\]\n]+?){SEP})?", text):
        if re.search(r"\[(\d+)\]", text) and STOP_RE.search(text) and f"[{m.group(1)}]" in "".join(f"[{x.group(2)}]" for x in STOP_RE.finditer(text)):
            continue  # es el código de parada «Pio Baroja 15 [344]»
        check_line(where, m.group(1), ctx)
        if m.group(2) and not re.match(r"[\d\[]", m.group(2)):
            check_dest(where, m.group(1), m.group(2), ctx)
    # [Igeldo*] como chip de destino, «Hacia X»
    for m in re.finditer(r"Hacia ([^\]\*…]+)\*?\]", text):
        sel = ctx_line.get(where.split(" · ")[0])
        if sel:
            check_dest(where, sel, m.group(1), None)
    for m in re.finditer(r"Destino: .*\[(\w[^\]]*?)\*\]", text):
        sel = ctx_line.get(where.split(" · ")[0])
        if sel:
            check_dest(where, sel, m.group(1), ctx)
    return ctx


ctx_line = {}


def validate_excalidraw(path):
    rel = path.split("/wireframes/")[1]
    d = json.load(open(path))
    els = d["elements"]
    ids = [e["id"] for e in els]
    if len(ids) != len(set(ids)):
        err(rel, f"ids duplicados: {sorted({i for i in ids if ids.count(i) > 1})}")
    byid = {e["id"]: e for e in els}
    for e in els:
        if e.get("containerId") and e["containerId"] not in byid:
            err(rel, f"{e['id']}: containerId {e['containerId']} no existe")
        for b in e.get("boundElements") or []:
            if b["id"] not in byid:
                err(rel, f"{e['id']}: boundElements {b['id']} no existe")
        if e["type"] == "text":
            if e["text"] != e["originalText"]:
                err(rel, f"{e['id']}: text != originalText")
    frames = [e for e in els if e["type"] == "frame"]
    leaf = [f for f in frames if not any(g is not f and f["x"] <= g["x"] and f["y"] <= g["y"] and g["x"] + g["width"] <= f["x"] + f["width"] and g["y"] + g["height"] <= f["y"] + f["height"] for g in frames)]

    def frame_of(e):
        for f in leaf:
            if f["x"] <= e["x"] <= f["x"] + f["width"] and f["y"] <= e["y"] <= f["y"] + f["height"]:
                return f
        return None

    for e in els:
        if e["type"] in ("text", "rectangle"):
            f = frame_of(e)
            if f and e["y"] + e["height"] > f["y"] + f["height"]:
                err(rel, f"{e['id']} se sale por abajo de {f['id']}")
    # ejemplos por frame, de arriba abajo
    for f in leaf:
        texts = sorted((e for e in els if e["type"] == "text" and frame_of(e) is f), key=lambda e: e["y"])
        ctx = None
        for i, e in enumerate(texts):
            # tarjeta de aviso: «[16] Igeldo» y debajo «Pio Baroja 15 (344) · …»
            if re.match(rf"\[{LINE_RE}\]", e["text"]) and not STOP_RE.search(e["text"]) and i + 1 < len(texts):
                nxt = STOP_RE.match(texts[i + 1]["text"])
                if nxt and nxt.group(2) in S:
                    ctx = nxt.group(2)
            where = f"{rel} · {e['id']}"
            # línea elegida en A06 / flujo (chip con * o ✓) para validar el chip de destino
            sel = re.search(rf"\[(?:✓ ?)({LINE_RE})\]|\[({LINE_RE})\*\]|Línea ({LINE_RE}) ·", e["text"])
            if sel:
                ctx_line[rel] = sel.group(1) or sel.group(2) or sel.group(3)
            ctx = check_text(where, e["text"], ctx)
            # distancias «0 m · 1 min» respecto a la parada 340 (usuario junto a Pío Baroja 10)
            for m in re.finditer(r"\((\d+)\)\s+(\d+) m · (\d+) min", e["text"]):
                checked["distancias"] += 1
                dist = haversine(coords["340"], coords[m.group(1)])
                walk = max(1, math.ceil(dist / 80))
                if (dist, walk) != (int(m.group(2)), int(m.group(3))):
                    err(where, f"distancia {m.group(1)}: {dist} m · {walk} min, no {m.group(2)} m · {m.group(3)} min")
            # itinerario L28
            m = re.search(r"Línea (\w+) · (\d+) paradas", e["text"])
            if m:
                checked["itinerarios"] += 1
                its = [i for i in cat["itineraries"] if i["lineCod"] == m.group(1)]
                first = its and next((i for i in its if i["destination"] == "Amara - Ospitaleak - Miramon"), None)
                if not first or len(first["stops"]) != int(m.group(2)):
                    err(where, f"L{m.group(1)} hacia Amara - Ospitaleak - Miramon no tiene {m.group(2)} paradas")
            m = re.search(r"(\d+) paradas más", e["text"])
            if m:
                checked["itinerarios"] += 1
                if 4 + int(m.group(1)) != 15:
                    err(where, "las paradas del itinerario no suman 15")
            m = re.search(r"(\d+) paradas · (\d+) líneas", e["text"])
            if m:
                checked["itinerarios"] += 1
                if (int(m.group(1)), int(m.group(2))) != (len(S), len(L)):
                    err(where, f"catálogo: {len(S)} paradas · {len(L)} líneas")
    return d


def validate_stitch(path):
    rel = path.split("/docs/07-android-mvp/")[1]
    h = open(path).read()
    for m in re.finditer(r'truncate">([^<]+?)(?: <span[^>]*>\((\d+)\)</span>)?</span>\s*(?:<span[^>]*>(\d+)</span>)?', h):
        name, code = m.group(1), m.group(2) or m.group(3)
        if code:
            check_stop(rel, name, code)
    # tarjetas: parada de contexto = la última vista; filas línea + destino
    ctx = None
    for m in re.finditer(r'truncate">([^<]+?)(?: <span[^>]*>\((\d+)\)</span>)?</span>|whitespace-nowrap">(\d+)</span>|tnum whitespace-nowrap">(' + LINE_RE + r')</div>\s*<span[^>]*>([^<]+)</span>', h):
        if m.group(2):
            ctx = m.group(2)
        elif m.group(3) and m.group(3) in S:
            ctx = m.group(3)
        elif m.group(4):
            check_line(rel, m.group(4), ctx)
            check_dest(rel, m.group(4), m.group(5), ctx)


for p in sorted(glob.glob(f"{ROOT}/docs/07-android-mvp/wireframes/**/*.excalidraw", recursive=True)):
    validate_excalidraw(p)
validate_stitch(f"{ROOT}/docs/07-android-mvp/stitch-references/inicio/code.html")
print("comprobados:", checked)
print("discrepancias:", len(errors))
for e in errors:
    print("  -", e)
sys.exit(1 if errors else 0)
