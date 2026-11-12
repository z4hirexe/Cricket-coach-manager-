# Cricket Coach Manager — Premium Overhaul: Architecture & Migration Plan

## 1. Compatibility check (done before any change)

| Item | Current | Result |
|---|---|---|
| Language / UI | Kotlin 1.9.24, Jetpack Compose (BOM 2024.06) | Keep. All game logic (career, auction, tournaments, saves, stats) stays untouched. |
| Build | AGP 8.5.2, Gradle 8.7, JDK 17, minSdk 26, targetSdk 34 | Compatible with Filament (min API 21, needs OpenGL ES 3.0, which every Android 8+ phone has). |
| Old "3D" view | Compose Canvas with software projection (v1.1) | **Replaced.** It was a 2D canvas drawing projected lines, not a real 3D renderer. It remains only as the "Low / 2D" fallback. |
| New 3D engine | **Google Filament 1.75.1** (`filament-android` + `gltfio-android`) | Real GPU renderer: physically based materials, real-time shadows, image-based lighting, ACES tone mapping, SSAO, bloom, MSAA, and skinned skeletal animation from glTF 2.0. Both AARs are pure Java (no Kotlin metadata), so the transitive kotlin-stdlib 2.4 dependency is excluded and the project stays on Kotlin 1.9. Native libraries are shipped for arm64-v8a and armeabi-v7a only (~10 MB). |

### Why Filament and not Unity / Godot (decision record)
* **Unity**: the Unity Editor (GUI, licence-activated) cannot run in this CI or build environment. The engine would also have to be rewritten in C#: 5,000+ lines of tested Kotlin game logic, which breaks the rule "do not destroy the existing engine". The APK would grow by 40–60 MB. It is possible later as *Unity-as-a-Library* for the match scene only. See §5.
* **Godot 4**: the full game would have to be rewritten in GDScript or C#. Same objection.
* **Filament** runs inside the existing app. The Kotlin match engine drives the 3D scene directly and Compose draws the UI over it. Assets are standard glTF 2.0, so artist-made models (Blender, Maya, mocap) can replace the generated ones later without code changes.

## 2. Asset pipeline (reproducible, original assets)
`tools/assets/build_assets.py` generates all 3D assets as glTF 2.0 binary files (`app/src/main/assets/models/*.glb`):
* **Stadium**: a tiered oval bowl (concrete, seating and roof canopy). Also: a mown outfield with a normal-mapped grass texture, a worn pitch, creases, a boundary rope, advertising boards with original sponsor names, sight screens, 4 floodlight towers with emissive panels, and baked crowd sections in seated and standing variants.
* **Cricketer**: an anatomically proportioned human mesh built from a signed-distance-field body and marching cubes. It has a skull, jaw, nose, ears, clavicles, deltoids and calf muscles. It uses a 20-joint skeleton with smooth skin weights and comes in two builds (athletic and heavy) plus runtime height scaling. Kit regions (shirt, trim, trousers, skin, shoes) are separate PBR materials, so every team gets its own colours. Accessories are helmet with grille, cap, batting pads, gloves, bat, ball, and several hair and beard styles.
* **Animations**: keyframed clips baked into the glb: batting stance, defence, cover drive, straight drive, pull, hook, cut, sweep, reverse sweep, lofted drive, leave and play-and-miss. Also fast and spin bowling run-ups with delivery strides, fielder ready/run/dive/catch/throw, keeper crouch/collect, umpire signals (out, four, six, wide), appeals and celebrations. Left-handers and left-arm bowlers are mirrored at runtime.

## 3. Legal constraints (stated clearly, not hidden)
* **Real cricketers' 3D likenesses (faces and hairstyles) are not used.** A 3D likeness needs image-rights licences from the players and their boards, which official games buy. The 3D models use generic faces. Each player gets a deterministic combination of skin tone, hair, beard and build, so players look different without imitating anyone.
* **Real faces appear only as photos**: freely licensed Wikimedia Commons portraits, credited on each profile (343 of 901 players). Players without a free photo are marked as having no photo.
* **IPL and league kits use team colours only.** No official logos, crests or sponsor marks.
* **Player height is not in the verified dataset.** The game does not invent it. Model height is cosmetic and is never displayed as data.

## 4. Phases
| Phase | Scope | Status |
|---|---|---|
| 0 | Architecture and compatibility check (this document) | ✅ done. Filament 1.80 needs compileSdk 37/AGP 9, so the project is pinned to **1.75.1**, the newest release with Java 17 bytecode and no compileSdk floor. Build verified. |
| 1 | Filament engine integration, stadium, cricketer models, skeletal animation, Low/Medium/High graphics settings | ✅ 2.0.0-alpha1. Compiles, 9/9 unit tests pass, and the GLBs are validated by an independent software renderer (docs/previews). **Not yet verified on a physical GPU.** If the engine fails, the app falls back to the Low renderer. |
| 2 | 3D match presentation: run-ups, delivery, shot-specific batting animations, fielding, keeper, umpire signals, broadcast cameras, replays, day/night/day-night lighting, weather (overcast, rain) | 🟡 alpha1 has: the BallEv-driven choreography (run-up, release, bounce, 10 shot types by real direction, chases, throws, catches, bowled/LBW/stumped/run-out mechanics, running between wickets, 4/6/out/wide signals), 4 cameras with slow-motion replay, and day / overcast / day-night / night lighting by format and weather. Still to do: animated crowd, rain visuals, a dedicated highlights reel, and per-player custom actions. |
| 3 | Title screen: live 3D stadium background with a featured player, logo, menu | ✅ alpha1: floodlit 3D stadium with an orbiting camera, a featured star playing shots, a real photo and verified stats card, and an animated menu. |
| 4 | Commentary system: English and Hindi, multiple commentator styles, situation-aware, no immediate repeats, special calls for maiden/50/100/hat-trick | ✅ 2.3.0-alpha1: two-voice booth (lead + analyst, fictional casters, distinct TTS voice/pitch), English / Hindi / Hinglish / English+Hindi booth, 3 styles, shot-direction and fielder-aware calls, 25+ special calls (50/100/150/200, team milestones, partnerships, 3-for/5-for, hat-trick ball + hat-trick, golden duck/duck, collapse, nineties, free hit, maiden, wicket maiden, big over, powerplay, death overs, last over, chase equation, winning hit), end-of-over scoreboard cards, intro/toss, innings break + target, result. Stats are tracked from ball events, so they stay exact when fast-forwarding. 4 unit tests cover every language × style × format; sample transcript in docs/commentary_sample.txt. |
| 5 | Social feed: auto posts from real game events, likes and followers from progression, user posts, optional Supabase sync | planned |
| 6 | Auction and signing presentation with a 3D player on the podium, signing announcements, transfer news | ✅ 2.4.0-alpha1: auction-hall stage (LED wall tinted by the leading team, moving spotlights, gold-rimmed stage), a generic auctioneer silhouette at a lectern whose gavel strikes on going-once / going-twice / sold, the lot card (photo, condensed broadcast type, role / age / overseas chips, skill bars, base price), an animated bid ticker with the leader's badge, a wall of team paddles (the leader's paddle is raised and glows, clubs that bid on the lot are lit), a "raise paddle" bid button, purse / squad-needs strip, SOLD / UNSOLD stamp with a team-colour burst and "welcome to the squad", an optional 3D podium (the lot in the floodlit 3D stadium, Medium/High graphics), an auction summary with your signings, and auction-room audio (music, room murmur, paddle, rival bids, gavel taps, SOLD). The engine records the bid sequence, the paddles on each lot and the last hammer result (old saves load with defaults). Signing news posts already existed (Social.onSigning). |
| 7 | Profile redesign: 3D model preview, form graph, achievements, transfer history; full UI and typography pass | ✅ 2.4.0-alpha1: broadcast hero header in club or national colours (kit stripes, giant rating watermark, large photo with club badge, role / country flag / age, batting and bowling style chips, OVR medallion, in-game role rank, market value), six key-number tiles, Overview / Real stats / Career mode tabs, a hexagonal skill radar, attribute bars, potential stars, fitness / morale / form ring gauges with a status line, a recent-form chart (runs bars with the 50 line, wickets dots), teams & contract card, a career timeline (transfers, auction buys, history) and an optional 3D view of the player in team kit. A condensed display face (Barlow Condensed, SIL OFL) is used for heroes, tickers and big numbers. |
| 8 | Audio upgrade: recorded CC0 stadium audio where available, run-up, release and umpire sounds | ✅ 2.4.0-alpha1: tools/make_sounds.py rewritten (original procedural audio, no third-party recordings): stadium reverb on every ball and crowd sound, 37 assets. New per-event sounds: thin edge (caught behind), pad thud (LBW / appeals), keeper's gloves, catch pop + crowd jump, near-miss "ooh", appeal shout, dhol groove after sixes, bugle after some fours, two variants of cheers and roars, random pitch variation. Three-layer crowd that follows the match (bed, rhythmic chant that swells after boundaries and wickets, nervous murmur + heartbeat drum in tight chases, from the pure-logic CrowdMood, unit-tested). Auction scene with its own music loop and room murmur. Mixer adds Stadium ambience and Menus & auction channels, a Dynamic crowd switch and a sound-test row (all synced to the cloud profile). Loops are OGG Vorbis, so res/raw is only 3.5 MB. |

### Professional UI & gameplay overhaul (v2.5)
| Phase | Scope | Status |
|---|---|---|
| A | SIMULATE → Cricket Calendar / Match Schedule Centre | ✅ 2.5.0-alpha1: `Screen.Calendar` with a month grid (Prev / Next / Today), category filter chips (International, IPL, Domestic, Tournaments, Auction, Transfers, Training / Rest, Key events), a date agenda with match cards (team flags/badges, competition + stage, format, date, fictional local start time, venue, status, result) and working Play / Simulate / View details buttons, a mini match centre for any fixture, auction / transfer-window / training / rest / opener / knockouts / final / awards events, "coming up" list. Top-bar SIMULATE opens the calendar; a ▶▶ button keeps the one-tap day advance; on the calendar the button advances a day. Every date is labelled **Generated / fictional schedule**. `game/Schedule.kt` maps engine game days to dates (no engine change). Tests: ScheduleTest (4) + CalendarFlowTest (Robolectric, real VM). |
| B | Auction premium redesign | ✅ build-13 (2.5.0-alpha2): broadcast stage, dossier + career stats, price rail, team panels, history dialog, retention board, auction report |
| C | Per-player 3D body model + kit + animations | ✅ 2.5.0-alpha3: 3 body builds (slim / athletic / heavy, 2 LODs), height scaling, 6 skin tones, 6 hair styles x 6 colours, 5 facial-hair options, rigid shirt-number digits on the back, team kit (jersey, trim, trousers, cap/helmet, team-tinted pads, gloves, shoes; whites for red-ball). `render3d/Appearance.kt`: ~118 curated real players get approximate public style descriptors (no scans, no likeness assets), everyone else a deterministic fictional look. Anti-clipping pass in the asset pipeline (IK hip raise + residual lift, per-clip lowest point exported as `ClipData.GROUND_MIN` and unit-tested); actor separation 0.6 m in DeliveryPlan. Profile: 3D model panel with animation picker (Idle, Batting, Pace, Spin, Running, Fielding, Catching, Throwing, Keeping, Celebrate), drag-to-rotate Filament viewer, 2D twin on Low graphics. Phase B fixes: SIMULATE always opens the calendar first, DOBs, SIM ALL bids for the user + accelerated round, compact pips. |
| D | Stumpstagram (social rename + player accounts) | ✅ 2.5.0-alpha4: CricGram renamed Stumpstagram (nav, menu, titles). `game/Stumps.kt`: every one of the 901 real players has an account (unique @username, photo/initials avatar, bio, followers, following, posts, verified badge); fictional players get small never-verified accounts. Followers come from a popularity model (caps, runs, wickets, 100s, 5-fors, country fan base, IPL contract) and are always labelled **est.** with a note that they are in-game estimates, not verified data. Auto posts by the players themselves for win, loss, 50, 100, five-for, hat-trick (from ball events), auction signing, transfer / free signing, trophy and career milestones; likes, comment counts and follower gain scale with performance and popularity. Follow / unfollow, Following feed, Players directory with search + filters, player profile (`Screen.SocialProfile`) reachable from posts, the directory and the player profile. Offline feed + Supabase online feed kept. Tests: StumpsTest (5); CI build-20, 79/79 green. |
| E | Match Centre redesign + mandatory tournament squad selection | ✅ build-26 (2.5.0-alpha5) |
| F | True fullscreen landscape match + TV scoreboard | ✅ build-34 (2.5.0-alpha6) |
| G | Integration, previews and testing | ✅ 2.5.0-beta1 (vc17) - see Phase G below |

## 5. Path to "AAA" realism (honest scope)
Procedurally generated humans cannot match motion-captured, artist-sculpted, licensed models like those in console cricket games. The engine is ready for them: drop artist glTF files into `assets/models/` with the same joint names and clip names, and the game uses them. Recommended next steps if a budget exists:
1. Commission or buy rigged sports-character glTFs. Mixamo-compatible rigs can be retargeted to our 20 joint names.
2. Get mocap cricket animation packs.
3. Licence player likenesses through a players' association, which is required for real faces.
4. Optionally add a Unity-as-a-Library match scene behind the same `MatchPresenter` interface.

## Phase E — Match Centre + mandatory squad registration (DONE, build-26, 2.5.0-alpha5, 93/93 tests)
- Match Centre tabs: Team Sheet (XI + bench, C/VC/WK, impact), Batting Order, Bowling Plan (New/Mid/Death/Part roles), Match Info, Opponent; 5-step pre-match checklist gates toss / Watch live / Quick sim.
- Tournament squad registration (IPL 18-25, max 8 overseas, 4 replacements; Ranji/VHT/SMAT & tours 18-25, 3 replacements; ICC events 15). Locks after match 1; only injured/departed players replaceable.
- XI is picked only from the registered squad (engine-enforced); simulated matches auto-register a default squad.
- Bowling roles change who bowls in powerplay/death overs (RegistrationTest).
- Previews 88-94.
- Leftovers: batting-tab still shows in-game career runs; bench FORM chip truncates; registration not yet linked from Calendar/Shell nav.

## Phase F — True fullscreen landscape match (DONE, build-34, 2.5.0-alpha6, 104/104 tests)
- LiveMatchScreen rewritten (ui/screens/LiveBroadcast.kt): stadium is full-bleed (no top bar / split pane), keep-screen-on, cutout-safe overlays.
- Right control rail (128-144 dp, adaptive 36/42 dp buttons): Ball, Over, Innings, Auto/Pause, Sim End, Declare (Tests only), Defend/Normal/Attack, Commentary, Highlights, Scorecard, Wagon (+Manhattan/Worm/Events). Consistent drawn icon set.
- TV scoreboard: team slab, R/W, overs, CRR, RRR/PROJ, target, striker & non-striker runs (balls), bowler O-R-W, last 6 balls, partnership, last wicket, situation; Tests: innings, day, session, lead/trail, overs left.
- Win-% bug, LIVE badge, commentary ticker, replay badge, result card (Scorecard / Continue).
- LiveBroadcastUiTest (3 Compose UI tests on Robolectric) taps every rail control and checks the engine state.
- Previews 95-102 (incl. 740x360 small phone).
- Leftovers: no real-device GPU test; Robolectric previews use the legacy 2D-projected stadium (Filament 3D only on device).

## Phase G - integration, calibration, leftovers (2.5.0-beta1, vc17)
- **Score calibration** (`game/MatchSim.kt`): skill gap saturates (`DIFF_CAP*tanh`), domestic games re-centre the bat-v-ball gap on the
  international level (`levelAdj`, `LEVEL_F`), first-class base wicket/dot rates tuned (`FC_WK`, `FC_DOT`). Tuned with the pure-JVM
  harness `tools/simharness` (build.sh / run.sh, ~5 s per 1,000 matches). 1st-innings averages (N=200 / 66 for multi-day):

  | Format | Before | After | Target |
  |---|---|---|---|
  | Intl T20 | 162 (max 313) | 168 (max 258) | 160-175 |
  | IPL | 195 (300+ 6%) | 195 (max 273, 300+ 0%) | 180-195 |
  | SMAT | 192 | 181 | 170-195 |
  | Intl ODI | 253 (350+ 18%, max 442) | 263 (350+ 8%) | 260-290 |
  | VHT | 336 (max 608) | 284 (max 410) | 270-295 |
  | Test | 295 | 346 (draw 16%) | 300-350 |
  | Ranji | 431 (max 923, draw 70%) | 329 (draw 48%) | 300-360 |
- `CalibrationTest` (7 tests) guards every format band; EngineTest still sees India beat Zimbabwe 48/60.
- **Stumpstagram**: posts carry `matchKey`; one post per player per match (fan CENTURY/FIVE-FOR cards fold into the player's own post with the
  poster card), re-entry safe; "For You" uses log-engagement + recency + diversity penalty (`Stumps.forYou`) so no single superstar dominates.
- **Match Centre**: tiny-sample averages no longer show "@177.0" (`avgPart`: n.o. / "1 out" until 3 dismissals); Batting Order tab shows this
  comp -> real format record -> career (no more "0 career runs"); bench/XI chip row is one annotated line (FORM never clipped).
- **Registration**: calendar banner + sidebar "Register Squad" entry (starred while pending); calendar jumps stop at `Screen.SquadRegistration`
  instead of silently auto-registering (Sim-to-season-end still auto-registers).
- **Camera**: 2D broadcast follow camera keeps its aim inside the rope and low (sixes no longer tilt into the sky / floodlights).
- Previews 103-106. Remaining: no real-device GPU test from the sandbox; `bowl_spin` keeps a 1.85 cm residual body lift baked into the GLB.
