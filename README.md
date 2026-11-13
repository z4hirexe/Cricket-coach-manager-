# 🏏 Cricket Coach Manager

An offline-first **Android cricket management game** (Kotlin + Jetpack Compose) built on **real player data**.
You run a club in a 4-division league, build squads through auctions and transfers, pick XIs and tactics,
watch matches ball-by-ball, and run international and IPL tournaments.

Original game: the UI, badges, mechanics and code are original. The game logo and loading-screen artwork are owner-supplied (see `design/user_provided/`) and must not be replaced with generated art. Real franchise names are used, but no official logos.

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
| **Saves** | 3 career slots, autosave after every action, plus a save on app pause. Stored in an SQLite index plus gzipped JSON in app storage with atomic writes. Fully offline and device-local — no account, login or cloud sync |

### New in v1.1 — 3D, sound, landscape, real photos
- **3D match view**: a software-rendered 3D stadium (floodlights, crowd, stands, ad boards, mown outfield, 30-yard circle, pitch, creases and stumps). Every delivery is animated: run-up, release, bounce, shot, ball flight, fielders chasing, batters running, stumps flying and catches. **4 cameras**: TV, Aerial, Side-on and Batter-cam. The TV camera follows the ball on big shots. Tap **2D/3D** to switch.
- **Highlights**: every four, six, wicket and milestone is listed and can be replayed in 3D.
- **Charts**: wagon wheel for each innings, Manhattan (runs per over with wicket markers) and worm (cumulative runs).
- **Sound**: bat-on-ball (soft and big), crowd cheer and roar, wicket and stumps, appeal groan, applause for milestones, innings horn, toss coin, auction hammer, UI clicks, stadium crowd ambience and a looping menu theme. Spoken commentary uses the device's text-to-speech. Every sound can be toggled in Settings, with a master volume. All audio is original, synthesised by `tools/make_sounds.py`.
- **Landscape and mobile-friendly**: sensor-landscape, immersive full screen, compact top bar, and a split match layout (3D view on the left, controls, tabs and charts on the right).
- **Real player photos**: freely licensed portraits from Wikimedia Commons, matched via Wikidata P18 using each player's ESPNcricinfo ID (`tools/fetch_photos.py`). 343 of 901 players have a photo. Photos are downloaded once, face-detected and cropped on the device, then cached for offline use. Players without a free photo get an initials avatar. The credit is shown on each profile.

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
  save/SaveManager.kt    SQLite + gzipped JSON slots + device-local settings
  ui/                    theme, components (badges, jerseys, stadium), GameVM, screens/
app/src/test/…/EngineTest.kt   end-to-end engine tests on the real dataset
tools/                   dataset builder + caches
```

## New in v2.0.0-alpha1: real-time 3D (Google Filament)
- **Engine:** GPU renderer (Filament 1.75.1, Apache-2.0) with PBR materials, shadow maps, image-based lighting, FXAA/MSAA, SSAO and bloom. It is embedded in the existing Kotlin app, and all career, auction, tournament, save and real-stats logic is unchanged.
- **Graphics settings:** Settings → Graphics quality offers Low (2.5D canvas), Medium (3D with dynamic resolution) or High (MSAA, SSAO, 2k shadows, bloom).
- **Stadium:** a two-tier bowl with crowd, roof, 4 floodlight towers, pavilion and scoreboard. It has a textured outfield with mowing stripes and a normal map, and a pitch with real crease geometry (popping 1.22 m, return creases). Stumps and bails can be animated. There are ad boards with fictional brands, sight screens, and day, dusk and night skies.
- **Players:** skinned 3D models with 19 joints, 2 builds and LODs. Accessories include helmet and grille, cap, pads, gloves, bat, and hair and beard variants. Kits use team colours, or whites in Tests.
  - RHB and LHB are mirrored, using the real batting hand from the dataset. Right- and left-arm, pace and spin actions follow each bowler's real style.
  - 3D faces are generic: no real-person likeness. Real faces appear only as credited Wikimedia Commons photos.
- **Match:** 33 baked animation clips (shots, bowling actions, fielding, keeping, umpire signals). Every simulated ball is choreographed from the engine's real outcome and direction. There are TV, aerial, side and batter cameras, and replays run at half speed.
- **Title screen:** a live floodlit 3D stadium with a featured star.
- **Asset pipeline:** everything is generated by `tools/assets/*.py` (SDF → marching cubes → decimation → skinning → glTF), and `tools/assets/preview.py` re-renders the GLBs to check them. No third-party art is used.
- **Status and remaining phases:** see `docs/OVERHAUL_PLAN.md`.
