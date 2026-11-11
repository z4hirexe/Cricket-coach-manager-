# 🏏 Cricket Coach Manager

An offline-first **Android cricket management game** (Kotlin + Jetpack Compose) built on **real player data**.
You run a club in a 4-division league, build squads through auctions and transfers, pick XIs and tactics,
watch matches ball-by-ball, and run international and IPL tournaments.

Original game: the branding, UI, badges, mechanics and code are all original. Real franchise names are used, but no official logos.

---

## Features

| Area | What's in the game |
|---|---|
| **Real data** | 901 real players from 12 nations (India, Australia, England, Pakistan, South Africa, New Zealand, Bangladesh, Sri Lanka, Afghanistan, West Indies, Ireland, Zimbabwe) plus the 10 IPL franchises with their real latest rosters |
| **Player profiles** | Full name, DOB/age, role, batting and bowling style, national side, IPL team. TEST / ODI / T20I / IPL tabs show official career totals (M, Runs, Avg, HS, 100s/50s, Wkts, Bowl avg, BBI, 5w/10w, Catches) plus detailed Cricsheet splits (Inn, NO, SR, 4s/6s, 200s, Econ, SR). Also career totals, milestones, recent real form, in-game rank and career-mode history |
| **Career** | Create a club (name, short name, country, colours, 8 badge styles, 6 jersey styles). Start in Division 4 with a ready-made squad or a Mega Auction. Four 8-team T20 divisions, double round-robin, top-2 promotion and bottom-2 relegation, Division 1 IPL-style playoffs |
| **Team management** | 15–25 man squads; playing XI; captain, vice-captain and wicket-keeper; batting order; bowling rotation plan; T20 Impact Player; batting approach (defend/balanced/attack); auto-pick; XI validation (overseas limit of 4, keeper, 5 bowling options) |
| **Auctions** | Mega auction every 3rd season with up to 4 retentions, plus an optional mini auction. Live lot-by-lot bidding against 31 AI clubs with going once/twice, base prices, increments, salary-cap purse, overseas limit (8), squad-completion reserve and unsold players |
| **Transfers** | Free agents, transfer list, bids, player swaps, release with compensation, AI offers for listed players, scouting missions for fictional prospects, scout reports (potential) |
| **Tournaments** | IPL, ICC ODI World Cup, T20 World Cup (groups), Champions Trophy, World Test Championship, Asia Cup, bilateral series (Test/ODI/T20, 1–5 matches), custom tournaments (any mix of nations and franchises, single or double round-robin; league only, final, semis or IPL playoffs) |
| **Match engine** | Ball-by-ball T20 / ODI / 5-day Test. Toss (call it yourself), pitch types (balanced/flat/green/dusty), weather, rain with a DLS-style revised target or no result, DRS for both sides, free hits, powerplay/middle/death phases, match-day form (upsets happen), follow-on and declarations in Tests, super overs, Impact Player |
| **Live match screen** | Scoreboard, CRR/RRR, target, lead/trail, win probability, recent balls, batter and bowler cards, partnership, commentary, full scorecard with FOW and partnerships, key events. Controls: next ball, over or innings, auto-play (adjustable speed), sim to end, declare, change batting approach live. Every match can also be quick-simmed |
| **Development** | Weekly training (focus and intensity), coaches, facilities, potential, age curve (growth → peak → decline), form, fitness, morale, injuries, youth academy intake each off-season, retirements, contract expiry |
| **Club management** | Finances (TV, sponsorship, tickets, merchandise, salaries, staff, upkeep, transfers), balance and debt warnings, salary cap per division, stadium capacity upgrades, ticket pricing, 3 sponsor offers per season, reputation, trophy cabinet |
| **Statistics** | Separate record books per competition and format plus an "All competitions" book: most runs/wickets/100s/50s/4s/6s/catches, highest scores, best bowling, highest/lowest team totals. Season and tournament awards (champion, most runs, most wickets, player of the tournament), all-time career-mode leaders, real-world team records (Cricsheet) |
| **Saves** | 3 career slots, autosave after every action, plus a save on app pause. Stored in an SQLite index plus gzipped JSON in app storage with atomic writes. Fully offline; optional **cloud sync** to your own Supabase project runs automatically when online |

### Screens
Main menu · Career slots · New career (club creator) · Dashboard · My Club · Squad & XI · Player database ·
Player profile · Auction · Transfers · Fixtures & results · Match centre (pre-match/toss) · Live match ·
League tables · Tournaments · Statistics & records · Finances · Season review · Settings & data sources

---

## Data sources (verified, no invented statistics)

| Source | Used for | Licence |
|---|---|---|
| [Cricsheet](https://cricsheet.org) ball-by-ball JSON (Tests, ODIs, T20Is, IPL) + people register | Detailed per-format batting/bowling/fielding splits, recent form, milestones, IPL rosters (latest season), real team records | ODC-By 1.0 |
| [Wikidata](https://www.wikidata.org) | Names, dates of birth, citizenship, ESPNcricinfo IDs | CC0 |
| [Wikipedia](https://en.wikipedia.org) "Infobox cricketer" (sourced from ESPNcricinfo) | **Official career totals** with their "as of" date, full names, batting/bowling styles | CC BY-SA |

* The dataset is bundled at `app/src/main/assets/cricket_data.json`. Its build date and source are shown in-app (main menu, Settings → Data sources, and on every profile).
* **Official totals are primary.** Cricsheet splits are labelled *"Detailed / partial (Cricsheet)"* because Cricsheet doesn't cover every match. It has nothing before ~2002, and **Afghanistan matches are withheld by Cricsheet**, so Afghan players rely on the Wikipedia totals.
* Uncapped Indian IPL players have their country *inferred* as India, and this is marked on their profile.
* Career mode starts in 2027. Players with no international or IPL appearance since **1 Jan 2025** are treated as retired in career mode, but remain viewable in the database with their real stats.
* In-game **ratings** are game abstractions derived from the real statistics. Youth-academy and scouted players are **fictional** and are clearly labelled as such, as are the 32 city club names.

### Rebuilding the dataset
```bash
# download Cricsheet zips + people.csv into a folder (see tools/build_dataset.py header), then:
cd tools
python3 build_dataset.py /path/to/cricsheet_data ../app/src/main/assets/cricket_data.json
```
Wikidata/Wikipedia responses are cached in `tools/cache/`, so rebuilds are reproducible and need few network calls.

---

## Build

Requirements: **JDK 17** and Android SDK 34. The project uses AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7 (wrapper included), minSdk 26.

```bash
./gradlew testDebugUnitTest   # headless engine tests (full seasons, auctions, every tournament type, save round-trip)
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

### CI / CD
* `.github/workflows/build-apk.yml` runs on every push and PR to `main` (plus manual dispatch). It runs the tests, builds `assembleDebug` and uploads **`app-debug.apk`** as a workflow artifact.
* `.github/workflows/release.yml` runs when you push a tag `v*`. It builds the APK and publishes a GitHub Release with the APK attached.

```bash
git tag v1.0.0 && git push origin v1.0.0   # creates a release with the APK
```

### Optional cloud sync
1. Create a Supabase project and run `docs/supabase.sql`.
2. In the game, open Settings → Online sync and enter the project URL and anon key, then enable sync.
3. Saves upload automatically whenever the device is online. Empty slots offer **Restore from cloud**.

---

## Project layout
```
app/src/main/java/com/cricketcoach/manager/
  data/Dataset.kt        real-data models, Repo loader, rating derivation
  game/Models.kt         save-game model (players, clubs, competitions, records…)
  game/Constants.kt      nations, IPL franchises, fictional clubs, name pools
  game/MatchSim.kt       ball-by-ball match engine
  game/Competitions.kt   team selection, fixtures, tables/NRR, knockouts, stats & records, money
  game/Career.kt         career setup, calendar, finances, training, transfers, season end
  game/Auction.kt        mega/mini auction with retentions and AI bidding
  game/Tournaments.kt    IPL / ICC events / Asia Cup / bilaterals / custom
  save/SaveManager.kt    SQLite + gzipped JSON slots, settings, Supabase sync
  ui/                    theme, components (badges, jerseys, stadium), GameVM, screens/
app/src/test/…/EngineTest.kt   end-to-end engine tests on the real dataset
tools/                   dataset builder + caches
```
