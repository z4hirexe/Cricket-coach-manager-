"""Fetch freely-licensed player portrait filenames (Wikidata P18 -> Wikimedia Commons).
Writes tools/cache/photos_cache.json  {cricinfo_id: "File name.jpg"} and patches assets/cricket_data.json
(player field "img"). Images are loaded at runtime from Wikimedia Commons (Special:FilePath) and cached on device."""
import json, os, sys, time, urllib.parse, urllib.request
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", "cache", "photos_cache.json")
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "cricket_data.json")
UA = "CricketCoachManager/1.0 (offline game dataset builder; contact dev@cricketcoach.local)"

def sparql(q):
    url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": q, "format": "json"})
    for i in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/sparql-results+json"})
            return json.load(urllib.request.urlopen(req, timeout=90))["results"]["bindings"]
        except Exception as e:
            print("retry", e, file=sys.stderr); time.sleep(5)
    return []

def wiki_pageimages(titles):
    url = "https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode({"action": "query", "prop": "pageimages", "piprop": "name",
        "pilicense": "free", "titles": "|".join(titles), "format": "json", "formatversion": 2, "redirects": 1})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    d = json.load(urllib.request.urlopen(req, timeout=60))
    norm = {}
    for k in ("normalized", "redirects"):
        for n in d["query"].get(k, []): norm[n["to"]] = n["from"]
    out = {}
    for p in d["query"]["pages"]:
        if "pageimage" in p:
            t = p["title"]; orig = norm.get(t, t); orig = norm.get(orig, orig)
            out[orig] = p["pageimage"]
    return out

def main():
    data = json.load(open(DATA))
    cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    ids = [p["cricinfo"] for p in data["players"] if p.get("cricinfo") and p["cricinfo"] not in cache]
    for i in range(0, len(ids), 150):
        chunk = ids[i:i+150]
        vals = " ".join('"%s"' % c for c in chunk)
        rows = sparql(f"SELECT ?ci ?img WHERE {{ VALUES ?ci {{ {vals} }} ?item wdt:P2697 ?ci . OPTIONAL {{ ?item wdt:P18 ?img }} }}")
        for c in chunk: cache.setdefault(c, "")
        for r in rows:
            if "img" in r:
                cache[r["ci"]["value"]] = urllib.parse.unquote(r["img"]["value"].rsplit("/", 1)[-1])
        print("wikidata", i + len(chunk), "/", len(ids), file=sys.stderr); time.sleep(1.5)
    # fallback: English Wikipedia free lead image by player name
    missing = [p for p in data["players"] if not cache.get(p.get("cricinfo") or ("name:" + p["name"]))]
    for i in range(0, len(missing), 50):
        chunk = missing[i:i+50]
        try: res = wiki_pageimages([p["name"] for p in chunk])
        except Exception as e: print("wp err", e, file=sys.stderr); res = {}
        for p in chunk:
            key = p.get("cricinfo") or ("name:" + p["name"])
            if p["name"] in res: cache[key] = res[p["name"]]
        time.sleep(2)
    json.dump(cache, open(CACHE, "w"), indent=0, sort_keys=True)
    n = 0
    for p in data["players"]:
        f = cache.get(p.get("cricinfo") or ("name:" + p["name"]))
        if f: p["img"] = f; n += 1
        else: p.pop("img", None)
    data["meta"]["photoSource"] = "Player photos: Wikimedia Commons (free licences, CC BY / CC BY-SA / public domain) via Wikidata P18; loaded online and cached on device."
    json.dump(data, open(DATA, "w"), separators=(",", ":"), ensure_ascii=False)
    print("players with photo:", n, "/", len(data["players"]), file=sys.stderr)

if __name__ == "__main__": main()
