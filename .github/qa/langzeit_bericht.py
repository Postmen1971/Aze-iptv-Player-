#!/usr/bin/env python3
"""Auswertung des Langzeit-Tests: Bericht je Sender und je Abschnitt aus dem App-Protokoll (AzeQA).

Aufruf: langzeit_bericht.py <Ausgabeordner des Tests> [sender.txt] [Bezeichnung der App-Version]
Schreibt <Ordner>/bericht.txt, zeigt eine Kurzfassung als Hinweis am GitHub-Lauf.
Rückgabe 1 nur bei Absturz oder „App reagiert nicht“ – alles andere ist Messwert, kein Fehler.
"""
import os
import re
import statistics
import sys
from collections import defaultdict
from datetime import datetime

LINE = re.compile(r"^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d+)\s+\d+\s+\d+\s+\w\s+AzeQA\s*:\s?(.*)$")
PKG = "de.aze.iptv"
NOPIC_MIN_S = 10  # nur Umschaltungen, bei denen mindestens so lange auf dem Sender geblieben wurde, zählen als „ohne Bild“


def de(x, nd=1):
    return f"{x:.{nd}f}".replace(".", ",")


def med(v):
    return statistics.median(v) if v else 0


def read_lines(path):
    out = []
    if not os.path.exists(path):
        return out
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            m = LINE.match(raw.rstrip("\n"))
            if not m:
                continue
            mo, d, h, mi, s, frac, msg = m.groups()
            t = datetime(2000, int(mo), int(d), int(h), int(mi), int(s)).timestamp() + int(frac) / 10 ** len(frac)
            out.append((t, f"{h}:{mi}:{s}", msg))
    out.sort(key=lambda x: x[0])
    return out


def new_ch():
    return {"zaps": 0, "zt": [], "zl": [], "nopic": 0, "gaps": [], "wd": defaultdict(int), "err": defaultdict(int),
            "load": defaultdict(int), "quick": 0, "watch": 0, "noimg": 0, "fps": [], "rejoin": 0}


def new_phase():
    return {"zt": [], "gaps": [], "wd": 0, "err": 0, "nopic": 0, "start": None, "end": None}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "langzeit-out"
    sender_txt = sys.argv[2] if len(sys.argv) > 2 else None
    version = sys.argv[3] if len(sys.argv) > 3 else ""
    lines = read_lines(os.path.join(out, "azeqa.log"))
    ch = defaultdict(new_ch)
    ph = defaultdict(new_phase)
    phase_order = []
    phase = "Start"
    cur = None
    open_zap = None  # [t, name, phase, frame]
    events = []  # Auffälliges mit Uhrzeit
    stalls = []
    crash_marks = []
    test_err = []
    other_away = 0

    def close_zap(t_end):
        nonlocal open_zap
        if open_zap and not open_zap[3] and t_end - open_zap[0] >= NOPIC_MIN_S:
            ch[open_zap[1]]["nopic"] += 1
            ph[open_zap[2]]["nopic"] += 1
            events.append((open_zap[4], f"KEIN BILD nach {int(t_end - open_zap[0])} s: {open_zap[1]} ({open_zap[2]})"))
        open_zap = None

    for t, hms, msg in lines:
        if ph[phase]["start"] is None:
            ph[phase]["start"] = t
        ph[phase]["end"] = t
        if msg.startswith("LZ_PHASE "):
            close_zap(t)
            phase = msg.split(" ", 1)[1].strip()
            phase_order.append(phase)
            ph[phase]["start"] = t
            continue
        if msg.startswith("LZ_FEHLER"):
            test_err.append(f"{hms} {msg[len('LZ_FEHLER '):]}")
            events.append((hms, msg))
            continue
        if msg.startswith("SPEICHER_FREI"):
            events.append((hms, msg))
            continue
        if msg.startswith("LZ_ABSTURZ") or msg.startswith("LZ_NEUSTART"):
            crash_marks.append(f"{hms} {msg}")
            events.append((hms, msg))
            continue
        if msg.startswith("ZAP_START "):
            close_zap(t)
            cur = msg[len("ZAP_START "):].strip()
            ch[cur]["zaps"] += 1
            open_zap = [t, cur, phase, False, hms]
            continue
        m = re.match(r"ZAPTIME (\d+) (.*)$", msg)
        if m:
            v, name = int(m.group(1)), m.group(2).strip()
            ch[name]["zt"].append(v)
            ph[phase]["zt"].append(v)
            if open_zap and open_zap[1] == name:
                open_zap[3] = True
            if v >= 3000:
                events.append((hms, f"langsames Umschalten {de(v / 1000)} s: {name}"))
            continue
        m = re.match(r"ZAPLAUF (\d+) (.*)$", msg)
        if m:
            ch[m.group(2).strip()]["zl"].append(int(m.group(1)))
            continue
        if msg.startswith("FIRST_FRAME "):
            if open_zap and open_zap[1] == msg[len("FIRST_FRAME "):].strip():
                open_zap[3] = True
            continue
        if msg.startswith("LUECKE "):
            if "WEGGESCHALTET" in msg:
                other_away += 1
                continue
            m = re.match(r"LUECKE (\d+)s (.*?) \| (.*)$", msg)
            if m:
                s, name = int(m.group(1)), m.group(2).strip()
                ch[name]["gaps"].append(s)
                ph[phase]["gaps"].append(s)
                events.append((hms, f"Lücke {s} s: {name} – {m.group(3)[:90]}"))
            continue
        if msg.startswith("WATCHDOG "):
            kind = msg.split()[1] if len(msg.split()) > 1 else "?"
            if cur:
                ch[cur]["wd"][kind] += 1
            ph[phase]["wd"] += 1
            events.append((hms, f"Wächter {kind}: {cur}"))
            continue
        m = re.match(r"ERROR (\S+) ", msg)
        if m:
            if cur:
                ch[cur]["err"][m.group(1).replace("ERROR_CODE_", "")] += 1
            ph[phase]["err"] += 1
            events.append((hms, f"Fehler {m.group(1).replace('ERROR_CODE_', '')}: {cur}"))
            continue
        m = re.match(r"LADEFEHLER (.*?) ×(\d+)", msg)
        if m:
            if cur:
                ch[cur]["load"][m.group(1)] += int(m.group(2))
            continue
        if msg.startswith("PUFFER_SCHNELL"):
            if cur:
                ch[cur]["quick"] += 1
            continue
        # Neuverbinden bei zähem Nachschub / Anbieter lehnt ab (seit 1.0.318) – kommen unter AUFFÄLLIG
        if msg.startswith(("LANGSAM_NEU", "LANGSAM_NUTZLOS", "NEUVERBINDEN_HILFT", "ABGELEHNT")):
            events.append((hms, msg[:140]))
            continue
        if msg.startswith("REJOIN ") or msg == "REJOIN":
            if cur:
                ch[cur]["rejoin"] += 1
            continue
        m = re.match(r"STALL (\d+) ms", msg)
        if m:
            stalls.append(int(m.group(1)))
            continue
        if msg.startswith("TICK ") and cur:
            kv = dict(re.findall(r"(\w+)=(\S+)", msg))
            if kv.get("st") == "3" and kv.get("pwr") == "true" and kv.get("first") == "true":
                img = int(kv.get("img", "0") or 0)
                c = ch[cur]
                c["watch"] += 1
                c["fps"].append(img)
                if img == 0:
                    c["noimg"] += 1
    if lines:
        close_zap(lines[-1][0])

    # ── Speicher
    mem = []
    p = os.path.join(out, "speicher.txt")
    if os.path.exists(p):
        for l in open(p, encoding="utf-8"):
            parts = l.split()
            if len(parts) >= 3 and parts[2].isdigit() and int(parts[2]) > 0:
                mem.append((int(parts[0]), parts[1], int(parts[2]) / 1024))
    # ── Abstürze / „App reagiert nicht“ / vom System beendet
    def pkg_lines(name, pat):
        q = os.path.join(out, name)
        if not os.path.exists(q):
            return []
        return [l.strip() for l in open(q, encoding="utf-8", errors="replace") if PKG in l and re.search(pat, l)]
    crash_lines = pkg_lines("crash.log", r"Process:|FATAL")
    anr = pkg_lines("ereignisse.log", r"am_anr")
    sys_crash = pkg_lines("ereignisse.log", r"am_crash")
    killed = pkg_lines("ereignisse.log", r"am_kill")

    total_s = (lines[-1][0] - lines[0][0]) if lines else 0
    all_zt = [v for c in ch.values() for v in c["zt"]]
    # Abschnitt G stört die Leitung absichtlich – dort sind Lücken erwartet und werden getrennt bewertet
    all_gaps = [g for n, q in ph.items() if not n.startswith("G_") for g in q["gaps"]]
    g_ph = [q for n, q in ph.items() if n.startswith("G_")]
    all_wd = sum(sum(c["wd"].values()) for c in ch.values())
    all_nopic = sum(c["nopic"] for c in ch.values())
    n_zaps = sum(c["zaps"] for c in ch.values())

    R = []
    R.append(f"Langzeit-Test mit echten freien Sendern{(' – ' + version) if version else ''}")
    R.append(f"Dauer {int(total_s // 60)} min · {len([k for k in ch if ch[k]['zaps']])} Sender · {n_zaps} Umschaltungen")
    R.append("")
    R.append("ERGEBNIS")
    bad = bool(crash_marks or crash_lines or anr or sys_crash)
    if bad:
        R.append(f"✘ Abstürze/Neustarts: {len(crash_marks)} · Absturzmeldungen: {len(crash_lines)} · „App reagiert nicht“: {len(anr)}")
    else:
        R.append("✔ keine Abstürze, kein „App reagiert nicht“")
    if test_err:
        R.append("✘ " + " · ".join(test_err[:5]))
    if killed:
        R.append(f"⚠ vom System beendet (Speicher): {len(killed)}×")
    if mem:
        first = med([m[2] for m in mem[:5]])
        last = med([m[2] for m in mem[-5:]])
        peak = max(m[2] for m in mem)
        sign = "⚠" if last - first > 100 else "✔"
        R.append(f"{sign} Speicher: Anfang {int(first)} MB → Ende {int(last)} MB (höchstens {int(peak)} MB)")
    if stalls:
        R.append(f"⚠ App-Oberfläche blockiert: {len(stalls)}× (längste {de(max(stalls) / 1000)} s)")
    else:
        R.append("✔ Oberfläche nie blockiert")
    wd_norm = sum(q["wd"] for n, q in ph.items() if not n.startswith("G_"))
    R.append(f"{'✔' if not all_gaps else '⚠'} Bildlücken: {len(all_gaps)} (zusammen {sum(all_gaps)} s) · Wächter-Eingriffe: {wd_norm}"
             f" · Umschalten ohne Bild: {all_nopic}")
    if g_ph:
        gg = [x for q in g_ph for x in q["gaps"]]
        quick = sum(1 for t, hms, msg in lines if msg.startswith("PUFFER_SCHNELL") and any(q["start"] and q["end"] and q["start"] <= t <= q["end"] for q in g_ph))
        R.append(f"Gestörte Leitung (G, absichtlich): {len(gg)} Lücken, zusammen {sum(gg)} s, längste {max(gg) if gg else 0} s"
                 f" · schneller Wiederanlauf {quick}× · Wächter {sum(q['wd'] for q in g_ph)}× · ohne Bild {sum(q['nopic'] for q in g_ph)}")
    if all_zt:
        slow = max(((v, k) for k, c in ch.items() for v in c["zt"]), default=(0, ""))
        R.append(f"Umschalten bis Bild: Median {de(med(all_zt) / 1000, 2)} s · langsamster {de(slow[0] / 1000)} s ({slow[1]})")
    R.append("")
    R.append("JE SENDER (Umschalten bis Bild · Lücken · Wächter · Fehler · Bilder/s · gesehen)")
    for name in sorted(ch, key=lambda k: (k[:3], k)):
        c = ch[name]
        if not c["zaps"] and not c["watch"]:
            continue
        zt = f"{de(med(c['zt']) / 1000, 2)} s (max {de(max(c['zt']) / 1000)} s)" if c["zt"] else "–"
        wd = ", ".join(f"{k} {v}" for k, v in c["wd"].items()) or "0"
        err = ", ".join(f"{k} {v}" for k, v in c["err"].items()) or "0"
        load = ("; Ladefehler " + ", ".join(f"{k} ×{v}" for k, v in c["load"].items())) if c["load"] else ""
        fps = de(med(c["fps"]), 0) if c["fps"] else "–"
        extra = []
        if c["nopic"]:
            extra.append(f"{c['nopic']}× KEIN BILD")
        if c["quick"]:
            extra.append(f"schneller Wiederanlauf {c['quick']}×")
        if c["rejoin"]:
            extra.append(f"Neuverbindung {c['rejoin']}×")
        R.append(f"• {name}: {c['zaps']}× gewählt, {zt} · Lücken {len(c['gaps'])} ({sum(c['gaps'])} s) · Wächter {wd}"
                 f" · Fehler {err} · {fps} B/s · {de(c['watch'] / 60, 0)} min{load}"
                 + (" · " + ", ".join(extra) if extra else ""))
    R.append("")
    R.append("JE ABSCHNITT")
    for name in phase_order:
        q = ph[name]
        if name == "ENDE":
            continue
        dur = int(((q["end"] or 0) - (q["start"] or 0)) // 60)
        mm = [m[2] for m in mem if m[1] == name]
        R.append(f"• {name}: {dur} min · Umschalten Median {de(med(q['zt']) / 1000, 2) if q['zt'] else '–'} s"
                 f" · Lücken {len(q['gaps'])} ({sum(q['gaps'])} s) · Wächter {q['wd']} · Fehler {q['err']}"
                 f" · ohne Bild {q['nopic']}" + (f" · Speicher {int(min(mm))}–{int(max(mm))} MB" if mm else ""))
    R.append("")
    R.append("AUFFÄLLIG (Uhrzeit = Emulator, UTC)")
    if events:
        events.sort(key=lambda x: x[0])
        for hms, e in events[:150]:
            R.append(f"  {hms} {e}")
        if len(events) > 150:
            R.append(f"  … und {len(events) - 150} weitere")
    else:
        R.append("  nichts")
    for title, ls in (("Absturzmeldungen", crash_lines), ("App reagiert nicht", anr), ("System-Absturz", sys_crash),
                      ("vom System beendet", killed)):
        if ls:
            R.append(f"{title}:")
            R.extend("  " + l[:200] for l in ls[:20])
    if sender_txt and os.path.exists(sender_txt):
        R.append("")
        R.append(open(sender_txt, encoding="utf-8").read().rstrip())
    text = "\n".join(R) + "\n"
    with open(os.path.join(out, "bericht.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    # Kurzfassung als Hinweis am Lauf (Ergebnis + je Sender)
    short = "\n".join(R[:R.index("JE ABSCHNITT")])
    enc = short[-30000:].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::notice title=Langzeit-Ergebnis::{enc}")
    if bad:
        print("::error title=Langzeit-Test::Absturz oder „App reagiert nicht“ – siehe bericht.txt")
        sys.exit(1)


if __name__ == "__main__":
    main()
