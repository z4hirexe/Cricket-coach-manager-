"""Wikipedia / Wikidata enrichment helpers for the CCM dataset builder."""
import json, os, re, sys, time, urllib.parse, urllib.request

UA = "CricketCoachManagerDataBuilder/1.0 (open-source game dataset)"


def http_json(url):
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            return json.load(urllib.request.urlopen(req, timeout=90))
        except Exception as e:
            print("retry", e, file=sys.stderr)
            time.sleep(4 + attempt * 3)
    return None


def clean_wiki(v):
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    for _ in range(4):
        v = re.sub(r"\{\{(?:nowrap|small|nobr|abbr|sort)\|([^{}|]*)(?:\|[^{}]*)?\}\}", r"\1", v, flags=re.I)
        v = re.sub(r"\{\{(?:efn|sfn|sfnm|refn|citation needed|cn|as of)[^{}]*\}\}", "", v, flags=re.I)
    v = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"<[^>]+>", "", v)
    v = v.replace("'" * 3, "").replace("'" * 2, "").replace("&nbsp;", " ")
    return v.strip()


def parse_infobox(text):
    i = text.lower().find("{{infobox cricketer")
    if i < 0:
        return None
    depth, j = 0, i
    while j < len(text):
        if text.startswith("{{", j):
            depth += 1; j += 2; continue
        if text.startswith("}}", j):
            depth -= 1; j += 2
            if depth == 0:
                break
            continue
        j += 1
    body = text[i + 2:j - 2]
    fields, depth, cur, k = [], 0, "", 0
    while k < len(body):
        c2 = body[k:k + 2]
        if c2 in ("{{", "[["):
            depth += 1; cur += c2; k += 2; continue
        if c2 in ("}}", "]]"):
            depth -= 1; cur += c2; k += 2; continue
        if body[k] == "|" and depth == 0:
            fields.append(cur); cur = ""; k += 1; continue
        cur += body[k]; k += 1
    fields.append(cur)
    out = {}
    for f in fields[1:]:
        if "=" in f:
            key, val = f.split("=", 1)
            out[key.strip().lower()] = val.strip()
    return out


def birth_from_infobox(v):
    m = re.search(r"(\d{4})\|(\d{1,2})\|(\d{1,2})", v or "")
    return f"{int(m.group(1)):04d}-{int(m.group(2)):02d}-{int(m.group(3)):02d}" if m else None


def num(v):
    v = clean_wiki(v or "").replace(",", "").strip()
    try:
        return float(v) if "." in v else int(v)
    except ValueError:
        return None


def official_stats(ib):
    res = {}
    for n in range(1, 7):
        col = clean_wiki(ib.get(f"column{n}", ""))
        fmt = {"Test": "TEST", "ODI": "ODI", "T20I": "T20I"}.get(col)
        if not fmt:
            continue
        g = lambda k: ib.get(f"{k}{n}", "")
        hs = clean_wiki(g("top score"))
        hf = clean_wiki(g("100s/50s")).split("/")
        cs = clean_wiki(g("catches/stumpings")).split("/")
        bb = clean_wiki(g("best bowling"))
        res[fmt] = dict(m=num(g("matches")), runs=num(g("runs")), avg=num(g("bat avg")),
                        h100=num(hf[0]) if hf and hf[0] else None,
                        h50=num(hf[1]) if len(hf) > 1 else None,
                        hs=hs if hs and hs not in ("–", "-") else None, balls=num(g("deliveries")),
                        wk=num(g("wickets")), bavg=num(g("bowl avg")), w5=num(g("fivefor")),
                        w10=num(g("tenfor")), bb=bb if bb and bb not in ("–", "-") else None,
                        ct=num(cs[0]) if cs and cs[0] else None, st=num(cs[1]) if len(cs) > 1 else None)
    return res


def fetch_infoboxes(titles, cache_path):
    cache = json.load(open(cache_path)) if os.path.exists(cache_path) else {}
    todo = [t for t in titles if t not in cache]
    for i in range(0, len(todo), 40):
        chunk = todo[i:i + 40]
        url = "https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode(dict(
            action="query", prop="revisions", rvprop="content", rvslots="main", rvsection=0,
            format="json", formatversion=2, redirects=1, titles="|".join(chunk)))
        res = http_json(url)
        if not res:
            continue
        back = {}
        for r in res["query"].get("normalized", []) + res["query"].get("redirects", []):
            back[r["to"]] = back.get(r["from"], r["from"])
        for pg in res["query"]["pages"]:
            src = pg["title"]
            seen = 0
            while src in back and seen < 5:
                src = back[src]; seen += 1
            revs = pg.get("revisions") or [{}]
            txt = revs[0].get("slots", {}).get("main", {}).get("content", "")
            cache[src] = parse_infobox(txt) or {}
        for t in chunk:
            cache.setdefault(t, {})
        time.sleep(2.0)
        print("infobox", i + len(chunk), "/", len(todo), file=sys.stderr)
    json.dump(cache, open(cache_path, "w"))
    return cache


def afghan_candidates():
    q = """SELECT DISTINCT ?ci ?article ?itemLabel ?dob WHERE {
      ?item wdt:P2697 ?ci ; wdt:P106 wd:Q12299841 .
      { ?item wdt:P1532 wd:Q889 } UNION { ?item wdt:P27 wd:Q889 }
      ?article schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> .
      OPTIONAL { ?item wdt:P569 ?dob }
      SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } }"""
    res = http_json("https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": q, "format": "json"}))
    out = []
    for b in (res or {}).get("results", {}).get("bindings", []):
        out.append(dict(ci=b["ci"]["value"], article=b["article"]["value"], name=b["itemLabel"]["value"],
                        dob=b.get("dob", {}).get("value", "")[:10] or None))
    return out
