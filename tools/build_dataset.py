#!/usr/bin/env python3
"""
CRICKET COACH MANAGER - verified dataset builder.

Source: Cricsheet (https://cricsheet.org) ball-by-ball data, licensed under ODC-By 1.0.
  - tests_male_json.zip, odis_male_json.zip, t20s_male_json.zip, ipl_json.zip
  - register/people.csv (player identifiers incl. ESPNcricinfo IDs)
Enrichment: Wikidata (CC0) via ESPNcricinfo player ID (P2697):
  full name (label), date of birth (P569), playing hand (P741),
  bowling style (P2545), position (P413).

Every statistic is COMPUTED from Cricsheet scorecards. Nothing is invented.
Cricsheet does not cover every historical match (Tests/ODIs mostly from ~2002-2004),
so career totals reflect Cricsheet-covered matches only. The coverage window is
written into the dataset metadata and shown in the app.

Usage: python3 build_dataset.py <cricsheet_dir> <output_json>
"""
import csv, io, json, os, sys, zipfile, time, urllib.parse, urllib.request
from wiki_enrich import (clean_wiki, num, official_stats, fetch_infoboxes, afghan_candidates, birth_from_infobox)
from collections import defaultdict

NATIONS = ["India", "Australia", "England", "Pakistan", "South Africa", "New Zealand",
           "Bangladesh", "Sri Lanka", "Afghanistan", "West Indies", "Ireland", "Zimbabwe"]
FORMATS = {"tests_male_json.zip": "TEST", "odis_male_json.zip": "ODI",
           "t20s_male_json.zip": "T20I", "ipl_json.zip": "IPL"}
ACTIVE_SINCE = "2022-01-01"      # international players included if they played since this date
IPL_SINCE = "2024-01-01"         # IPL players included if they played IPL since this date
BOWLER_WICKETS = {"bowled", "caught", "lbw", "stumped", "caught and bowled", "hit wicket"}
IPL_ALIASES = {"Delhi Daredevils": "Delhi Capitals", "Kings XI Punjab": "Punjab Kings",
               "Royal Challengers Bangalore": "Royal Challengers Bengaluru",
               "Rising Pune Supergiants": "Rising Pune Supergiant"}


CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")  # persisted Wikidata/Wikipedia caches

def new_fmt():
    return dict(m=0, inn=0, no=0, runs=0, bf=0, hs=0, hsNo=False, f50=0, f100=0, f200=0,
                fours=0, sixes=0, bInn=0, balls=0, rc=0, wk=0, bbW=0, bbR=0, w5=0, w10=0,
                ct=0, st=0, first=None, last=None, team=None)



def display_name(w, fallback):
    """Wikidata English label; if the label is missing (returned as a bare QID), use the
    English Wikipedia article title (verified common name), else the Cricsheet name."""
    import re
    n = w.get("name")
    if n and not re.match(r"^Q\d+$", n):
        return n
    art = w.get("article")
    if art:
        t = urllib.parse.unquote(art.rsplit("/", 1)[-1]).replace("_", " ")
        t = re.sub(r"\s*\((cricketer|cricketer, born \d+|born \d+)\)$", "", t).strip()
        if t:
            return t
    return fallback


def main(src, out_path):
    people = {}
    with open(f"{src}/people.csv", newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            people[r["identifier"]] = r
    stats = defaultdict(lambda: defaultdict(new_fmt))   # pid -> fmt -> stats
    innings_log = defaultdict(list)                     # pid -> [(date, fmt, opp, runs, notout, balls)]
    bowl_log = defaultdict(list)                        # pid -> [(date, fmt, opp, w, r)]
    names = {}
    milestones = defaultdict(list)
    ipl_last = {}                                       # pid -> (date, team)
    coverage = {}
    team_records = []                                   # real highest/lowest team totals
    for zname, fmt in FORMATS.items():
        z = zipfile.ZipFile(f"{src}/{zname}")
        dates = []
        for fn in z.namelist():
            if not fn.endswith(".json"):
                continue
            d = json.loads(z.read(fn))
            info = d["info"]
            if info.get("gender") != "male":
                continue
            teams = info["teams"]
            if fmt != "IPL" and not any(t in NATIONS for t in teams):
                continue
            if fmt == "IPL":
                teams = [IPL_ALIASES.get(t, t) for t in teams]
            date = info["dates"][0]
            dates.append(date)
            reg = info.get("registry", {}).get("people", {})
            pl = {IPL_ALIASES.get(k, k): v for k, v in info["players"].items()} if fmt == "IPL" else info["players"]
            team_of = {}
            for t, plist in pl.items():
                for n in plist:
                    pid = reg.get(n)
                    if not pid:
                        continue
                    names[pid] = n
                    team_of[pid] = t
                    s = stats[pid][fmt]
                    s["m"] += 1
                    s["first"] = min(s["first"] or date, date)
                    if not s["last"] or date >= s["last"]:
                        s["last"] = date
                        s["team"] = t
                    if fmt == "IPL" and (pid not in ipl_last or date >= ipl_last[pid][0]):
                        ipl_last[pid] = (date, t)
            match_wk = defaultdict(int)
            for inn in d.get("innings", []):
                if inn.get("super_over"):
                    continue
                bat_team = IPL_ALIASES.get(inn["team"], inn["team"]) if fmt == "IPL" else inn["team"]
                opp = [t for t in teams if t != bat_team]
                opp = opp[0] if opp else "?"
                bat = {}
                bowl = defaultdict(lambda: [0, 0, 0])  # balls, runs, wkts
                order = []
                tot_runs = tot_wk = legal = 0
                for ov in inn.get("overs", []):
                    for dl in ov["deliveries"]:
                        b = reg.get(dl["batter"]); ns = reg.get(dl["non_striker"]); bw = reg.get(dl["bowler"])
                        for p in (b, ns):
                            if p and p not in bat:
                                bat[p] = [0, 0, 0, 0, False]  # runs, balls, 4s, 6s, out
                                order.append(p)
                        ex = dl.get("extras", {})
                        r = dl["runs"]
                        tot_runs += r["total"]
                        if b:
                            bat[b][0] += r["batter"]
                            if "wides" not in ex:
                                bat[b][1] += 1
                            if r["batter"] == 4 and not dl.get("non_boundary"):
                                bat[b][2] += 1
                            if r["batter"] == 6 and not dl.get("non_boundary"):
                                bat[b][3] += 1
                        if "wides" not in ex and "noballs" not in ex:
                            legal += 1
                        if bw:
                            if "wides" not in ex and "noballs" not in ex:
                                bowl[bw][0] += 1
                            bowl[bw][1] += r["batter"] + ex.get("wides", 0) + ex.get("noballs", 0)
                        for w in dl.get("wickets", []):
                            po = reg.get(w["player_out"])
                            kind = w["kind"]
                            if kind not in ("retired hurt", "retired not out"):
                                tot_wk += 1
                                if po:
                                    if po not in bat:
                                        bat[po] = [0, 0, 0, 0, False]; order.append(po)
                                    bat[po][4] = True
                            if kind in BOWLER_WICKETS and bw:
                                bowl[bw][2] += 1
                            if kind == "caught and bowled" and bw:
                                stats[bw][fmt]["ct"] += 1
                            for f in w.get("fielders", []):
                                fp = reg.get(f.get("name", ""))
                                if fp and not f.get("substitute"):
                                    if kind == "caught":
                                        stats[fp][fmt]["ct"] += 1
                                    elif kind == "stumped":
                                        stats[fp][fmt]["st"] += 1
                team_records.append(dict(fmt=fmt, team=bat_team, opp=opp, runs=tot_runs, wk=tot_wk,
                                         balls=legal, date=date, venue=info.get("venue", "")))
                for p in order:
                    runs, balls, f4, f6, out = bat[p]
                    s = stats[p][fmt]
                    s["inn"] += 1; s["runs"] += runs; s["bf"] += balls; s["fours"] += f4; s["sixes"] += f6
                    if not out:
                        s["no"] += 1
                    if runs > s["hs"] or (runs == s["hs"] and not out):
                        s["hs"] = runs; s["hsNo"] = not out
                    if runs >= 200: s["f200"] += 1
                    if runs >= 100: s["f100"] += 1
                    elif runs >= 50: s["f50"] += 1
                    if runs >= 100 and s["f100"] == 1:
                        milestones[p].append(f"First {fmt} hundred: {runs}{'*' if not out else ''} vs {opp}, {date}")
                    innings_log[p].append((date, fmt, opp, runs, not out, balls))
                for p, (balls, runs, wk) in bowl.items():
                    s = stats[p][fmt]
                    s["bInn"] += 1; s["balls"] += balls; s["rc"] += runs; s["wk"] += wk
                    match_wk[p] += wk
                    if wk > s["bbW"] or (wk == s["bbW"] and runs < s["bbR"]) or s["bInn"] == 1:
                        if wk > s["bbW"] or s["bInn"] == 1 or (wk == s["bbW"] and runs < s["bbR"]):
                            s["bbW"], s["bbR"] = wk, runs
                    if wk >= 5:
                        s["w5"] += 1
                        if s["w5"] == 1:
                            milestones[p].append(f"First {fmt} five-wicket haul: {wk}/{runs} vs {bat_team}, {date}")
                    bowl_log[p].append((date, fmt, bat_team, wk, runs))
            for p, w in match_wk.items():
                if w >= 10 and fmt == "TEST":
                    stats[p][fmt]["w10"] += 1
        coverage[fmt] = dict(matches=len(dates), first=min(dates), last=max(dates))
        print(fmt, coverage[fmt], file=sys.stderr)

    # ---- select players ----
    selected = []
    for pid, fm in stats.items():
        intl = [f for f in ("TEST", "ODI", "T20I") if f in fm]
        recent_intl = any((fm[f]["last"] or "") >= ACTIVE_SINCE and fm[f]["team"] in NATIONS for f in intl)
        recent_ipl = "IPL" in fm and (fm["IPL"]["last"] or "") >= IPL_SINCE
        if recent_intl or recent_ipl:
            selected.append(pid)
    print("selected players:", len(selected), file=sys.stderr)

    # ---- Wikidata enrichment via cricinfo id ----
    ci_to_pid = {}
    for pid in selected:
        r = people.get(pid, {})
        for k in ("key_cricinfo", "key_cricinfo_2", "key_cricinfo_3"):
            if r.get(k):
                ci_to_pid[r[k]] = pid
    wd = {}
    import os
    cache = os.path.join(CACHE_DIR, "wikidata_cache.json")
    wcache = json.load(open(cache)) if os.path.exists(cache) else {}
    ids = [c for c in ci_to_pid if c not in wcache]
    for i in range(0, len(ids), 150):
        chunk = ids[i:i + 150]
        values = " ".join(f'"{c}"' for c in chunk)
        q = f"""SELECT ?ci ?itemLabel ?dob ?handLabel ?bowlLabel ?posLabel ?article ?citLabel WHERE {{
          VALUES ?ci {{ {values} }} ?item wdt:P2697 ?ci .
          OPTIONAL {{ ?article schema:about ?item; schema:isPartOf <https://en.wikipedia.org/> }}
          OPTIONAL {{ ?item wdt:P27 ?cit }}
          OPTIONAL {{ ?item wdt:P569 ?dob }} OPTIONAL {{ ?item wdt:P741 ?hand }}
          OPTIONAL {{ ?item wdt:P2545 ?bowl }} OPTIONAL {{ ?item wdt:P413 ?pos }}
          SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }} }}"""
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": q, "format": "json"})
        for attempt in range(4):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "CricketCoachManagerDataBuilder/1.0"})
                res = json.load(urllib.request.urlopen(req, timeout=90))
                break
            except Exception as e:
                print("wikidata retry", e, file=sys.stderr); time.sleep(5)
        else:
            continue
        for c in chunk:
            wcache.setdefault(c, [])
        for b in res["results"]["bindings"]:
            wcache[b["ci"]["value"]].append({k: v["value"] for k, v in b.items()})
        time.sleep(1)
    json.dump(wcache, open(cache, "w"))
    for c, rows in wcache.items():
        if c not in ci_to_pid:
            continue
        pid = ci_to_pid[c]
        for b0 in rows:
            b = {k: {"value": v} for k, v in b0.items()}
            e = wd.setdefault(pid, dict(name=None, dob=None, hand=set(), bowl=set(), pos=set(), article=None, cit=set()))
            if "article" in b:
                e["article"] = b["article"]["value"]
            if "citLabel" in b:
                e["cit"].add(b["citLabel"]["value"])
            e["name"] = b.get("itemLabel", {}).get("value")
            if "dob" in b and not e["dob"]:
                e["dob"] = b["dob"]["value"][:10]
            for key, fld in (("handLabel", "hand"), ("bowlLabel", "bowl"), ("posLabel", "pos")):
                if key in b and not b[key]["value"].startswith("Q"):
                    e[fld].add(b[key]["value"])
    print("wikidata matched:", len(wd), file=sys.stderr)

    # ---- Afghanistan players (Cricsheet withholds Afghanistan matches) ----
    cric_to_pid = {}
    for pid0, r in people.items():
        for k in ("key_cricinfo", "key_cricinfo_2", "key_cricinfo_3"):
            if r.get(k):
                cric_to_pid[r[k]] = pid0
    afg = afghan_candidates()
    print("afghan candidates:", len(afg), file=sys.stderr)
    title_of = lambda url: urllib.parse.unquote(url.rsplit("/", 1)[1]).replace("_", " ")
    titles = [title_of(w["article"]) for w in wd.values() if w.get("article")] + [title_of(a["article"]) for a in afg]
    boxes = fetch_infoboxes(sorted(set(titles)), os.path.join(CACHE_DIR, "infobox_cache.json"))
    afg_ids = set()
    for a in afg:
        ib = boxes.get(title_of(a["article"])) or {}
        if clean_wiki(ib.get("country", "")) != "Afghanistan":
            continue
        last_years = [num(ib.get(k, "")) or 0 for k in ("lasttestyear", "lastodiyear", "lastt20iyear")]
        if max(last_years) < int(ACTIVE_SINCE[:4]):
            continue
        pid = cric_to_pid.get(a["ci"]) or ("afg_" + a["ci"])
        afg_ids.add(pid)
        e = wd.setdefault(pid, dict(name=a["name"], dob=a["dob"], hand=set(), bowl=set(), pos=set(), article=a["article"], cit={"Afghanistan"}))
        e["article"] = a["article"]
        if pid not in names:
            names[pid] = a["name"]
        if pid not in selected:
            selected.append(pid)
        people.setdefault(pid, {"key_cricinfo": a["ci"]})
    print("afghan selected:", len(afg_ids), file=sys.stderr)

    # ---- output ----
    players = []
    for pid in selected:
        fm = stats.get(pid, {})
        w = wd.get(pid, {})
        intl_team = None
        last = ""
        for f in ("TEST", "ODI", "T20I"):
            if f in fm and (fm[f]["last"] or "") > last and fm[f]["team"] in NATIONS:
                last = fm[f]["last"]; intl_team = fm[f]["team"]
        country = intl_team
        fmt_out = {}
        for f, s in fm.items():
            s = dict(s)
            fmt_out[f] = s
        tot_balls = sum(fm[f]["balls"] for f in fm)
        tot_inn = sum(fm[f]["inn"] for f in fm)
        tot_m = sum(fm[f]["m"] for f in fm)
        tot_runs = sum(fm[f]["runs"] for f in fm)
        tot_st = sum(fm[f]["st"] for f in fm)
        ib = boxes.get(title_of(w["article"])) if w.get("article") else None
        ib = ib or {}
        off = official_stats(ib) if ib else {}
        inferred = False
        if pid in afg_ids:
            country = "Afghanistan"
        if not country:
            c_ib = clean_wiki(ib.get("country", ""))
            if c_ib in NATIONS:
                country = c_ib
            elif len(w.get("cit", [])) == 1 and next(iter(w["cit"])) in NATIONS:
                country = next(iter(w["cit"]))
            elif "IPL" in fm and not any(f in fm for f in ("TEST", "ODI", "T20I")):
                country = "India"
                inferred = True
        pos = (" ".join(w.get("pos", [])) + " " + clean_wiki(ib.get("role", ""))).lower()
        ib_role = clean_wiki(ib.get("role", "")).lower()
        bpm = tot_balls / max(1, tot_m)
        rpi = tot_runs / max(1, tot_inn)
        if ib_role:
            if "wicket" in ib_role or "keeper" in ib_role:
                role = "WK"
            elif "all" in ib_role and "round" in ib_role:
                role = "AR"
            elif "bowl" in ib_role:
                role = "BOWL"
            else:
                role = "BAT"
        elif "wicket" in pos or tot_st >= 2:
            role = "WK"
        elif "all-round" in pos or (bpm >= 12 and rpi >= 18):
            role = "AR"
        elif bpm >= 12:
            role = "BOWL"
        else:
            role = "BAT"
        hand = (" ".join(w.get("hand", [])) + " " + clean_wiki(ib.get("batting", ""))).lower()
        bowl_style = clean_wiki(ib.get("bowling", "")) or ", ".join(sorted(w.get("bowl", [])))
        dob = w.get("dob") or birth_from_infobox(ib.get("birth_date", ""))
        bat_style = "Left-hand bat" if "left" in hand else ("Right-hand bat" if "right" in hand else "")
        log = sorted(innings_log[pid], reverse=True)[:5]
        blog = sorted(bowl_log[pid], reverse=True)[:5]
        ms = list(milestones[pid])
        for f in ("TEST", "ODI", "T20I", "IPL"):
            if f in fm:
                s = fm[f]
                ms.insert(0, f"{f} debut (in dataset): {s['first']}")
                for mark in (10000, 5000, 1000):
                    if s["runs"] >= mark:
                        ms.append(f"{mark}+ {f} runs ({s['runs']})"); break
                for mark in (500, 250, 100, 50):
                    if s["wk"] >= mark:
                        ms.append(f"{mark}+ {f} wickets ({s['wk']})"); break
        players.append(dict(
            id=pid, name=display_name(w, names[pid]), short=names[pid],
            full=clean_wiki(ib.get("full_name", "")) or None,
            country=country or "", dob=dob, role=role, bat=bat_style,
            bowl=bowl_style, ipl=ipl_last.get(pid, (None, None))[1],
            off=off, offSrc=("Wikipedia infobox, source: " + clean_wiki(ib.get("source", "ESPNcricinfo"))[:80]) if off else None,
            offDate=clean_wiki(ib.get("date", "")) or None, afg=pid in afg_ids, cInf=inferred,
            iplLast=ipl_last.get(pid, (None, None))[0], stats=fmt_out,
            form=[dict(d=d, f=f, o=o, r=r, no=no, b=b) for d, f, o, r, no, b in log],
            bform=[dict(d=d, f=f, o=o, w=wk, r=r) for d, f, o, wk, r in blog],
            ms=ms, cricinfo=people.get(pid, {}).get("key_cricinfo")))
    tr = {}
    for f in FORMATS.values():
        rows = [t for t in team_records if t["fmt"] == f]
        tr[f] = dict(high=sorted(rows, key=lambda t: -t["runs"])[:10],
                     low=sorted([t for t in rows if t["wk"] == 10], key=lambda t: t["runs"])[:10])
    meta = dict(source="Cricsheet ball-by-ball data (cricsheet.org, ODC-By 1.0); Wikidata (CC0); Wikipedia infoboxes (CC BY-SA, ESPNcricinfo-sourced career totals)",
                built=time.strftime("%Y-%m-%d"), coverage=coverage, activeSince=ACTIVE_SINCE, iplSince=IPL_SINCE,
                note="Detailed statistics are computed only from matches covered by Cricsheet (Afghanistan matches are withheld by Cricsheet; pre-2002 Tests/ODIs are not covered). Official career totals come from Wikipedia infoboxes (ESPNcricinfo-sourced) with their own as-of dates.")
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(dict(meta=meta, players=players, teamRecords=tr), fh, separators=(",", ":"))
    print("wrote", out_path, len(players), file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
