"""Map Indian players in cricket_data.json to their REAL current state (domestic) team.
Source: Wikipedia infobox 'Domestic team information' (club/year fields) — cached infoboxes + polite API fetches for the rest.
Writes assets/domestic_states.json: {player_id: {"state": "...", "src": "Wikipedia: <title>"}}"""
import json, re, time, urllib.request, urllib.parse, os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = f"{ROOT}/app/src/main/assets/cricket_data.json"; OUT = f"{ROOT}/app/src/main/assets/domestic_states.json"
STATES = ["Andhra", "Arunachal Pradesh", "Assam", "Baroda", "Bengal", "Bihar", "Chandigarh", "Chhattisgarh", "Delhi", "Goa", "Gujarat", "Haryana",
          "Himachal Pradesh", "Hyderabad", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
          "Mizoram", "Mumbai", "Nagaland", "Odisha", "Puducherry", "Punjab", "Railways", "Rajasthan", "Saurashtra", "Services", "Sikkim", "Tamil Nadu",
          "Tripura", "Uttar Pradesh", "Uttarakhand", "Vidarbha"]
AL = {"orissa": "Odisha", "pondicherry": "Puducherry", "jammu & kashmir": "Jammu and Kashmir", "andhra pradesh": "Andhra"}
def norm(s): s = re.sub(r"\{\{nowrap\|([^}]*)\}\}", r"\1", s); return s
def pick(clubs):
    best = None
    for c, y in clubs:
        y = norm(y); m = re.search(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", c)
        names = [x for x in ((m.group(1), m.group(2) or "") if m else (c, "")) if x]
        st = None
        for n in names:
            n2 = re.sub(r"\s*\(?cricket team\)?.*", "", n).strip().lower(); n2 = AL.get(n2, n2)
            st = next((s for s in STATES if s.lower() == n2), None) or st
        if not st: continue
        yy = re.findall(r"(\d{4})", y); cur = "present" in y.lower()
        score = (1 if cur else 0, int(yy[-1]) if yy else 0, int(yy[0]) if yy else 0)
        if best is None or score > best[0]: best = (score, st)
    return best[1] if best else None
def get(url):
    r = urllib.request.Request(url, headers={"User-Agent": "CricketManagerPro-dataset/1.0 (offline mobile game dataset build)"})
    return json.load(urllib.request.urlopen(r, timeout=25))
d = json.load(open(DATA)); ib = json.load(open(f"{ROOT}/tools/cache/infobox_cache.json"))
out = json.load(open(OUT)) if os.path.exists(OUT) else {}
for p in d["players"]:
    if p["country"] != "India" or p["id"] in out: continue
    b = next((ib[k] for k in (p["name"], p.get("full") or "", p["short"]) if k in ib and ib[k]), None)
    if b:
        clubs = [(b.get(f"club{i}"), b.get(f"year{i}", "") or "") for i in range(1, 30) if b.get(f"club{i}")]
        st = pick(clubs)
        if st: out[p["id"]] = {"state": st, "src": f"Wikipedia: {p['name']}"}; continue
    for attempt in range(4):
        try:
            s = get("https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode(dict(action="query", list="search", srsearch=p["name"] + " Indian cricketer", format="json", srlimit=1)))
            hits = s["query"]["search"]
            if not hits: break
            t = hits[0]["title"]; time.sleep(1.2)
            w = get("https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode(dict(action="query", prop="revisions", rvprop="content", rvslots="main", titles=t, format="json", rvsection=0)))
            txt = next(iter(w["query"]["pages"].values()))["revisions"][0]["slots"]["main"]["*"]
            clubs = [(c, y) for _, c, y in re.findall(r"\|\s*club(\d+)\s*=\s*([^\n]+)\n\s*\|\s*year\1\s*=\s*([^\n]*)", txt)]
            st = pick(clubs)
            if st: out[p["id"]] = {"state": st, "src": f"Wikipedia: {t}"}
            print(p["name"], "->", t, st, flush=True); time.sleep(1.5); break
        except Exception as e:
            print("retry", p["name"], e, flush=True); time.sleep(8 * (attempt + 1))
    json.dump(out, open(OUT, "w"), indent=0, sort_keys=True)
json.dump(out, open(OUT, "w"), indent=0, sort_keys=True)
print("mapped", len(out), "of", sum(1 for p in d["players"] if p["country"] == "India"))
