# Cricket Manager Pro — UI Overhaul v2.7.0-beta1 · Final Report

**Repo:** https://github.com/z4hirexe/Cricket-coach-manager- (branch `main`, HEAD `4801147`)
**APK:** GitHub Releases → pre-release `build-73`
**Tests:** 187 / 187 unit + Compose UI + Roborazzi screenshot tests pass (GitHub Actions run 43931824951)
**Version:** versionCode 26 · versionName 2.7.0-beta1

---

## 1. Kya implement hua

### Phase 1 — Broadcast match UI (landscape)
Reference: Cricket Captain-style TV layout (user screenshot).

| Feature | Detail |
|---|---|
| `BroadcastMatchScreen` (new, `ui/screens/BroadcastMatch.kt`) | Top header (teams, competition, venue, score/overs/CRR/RRR), left scorecard tabs **Batting / Bowling / FOW / Partnerships / Extras / Summary**, right panels: current bowler + striker + non-striker with wagon wheel, pitch map, aggression bars. |
| Ball tracker | Colour-coded per-ball dots: dot / 1 / 2 / 3 / 4 / 6 / Wd / Nb / Bye / LB / W. Tap any ball → full ball history (over.ball, bowler, batter, runs, line, length, speed, shot type, footwork, approach, commentary) — **all from engine data**. |
| Over Summary overlay | Auto after every over: runs/wkts/extras, score, CRR, RRR, ball-by-ball, batting & bowling analysis. **CHANGE BOWLER** picker enforces T20 (4 ov) / ODI (10 ov) / Test (no cap, no consecutive) rules. Next over starts only after confirm. |
| New Batsman overlay | On wicket for the user's team: list with name, role, batting position, rating, form, fitness, style, stats. No random auto-pick for managed team. |
| Bottom control bar | Menu · Highlights · Commentary · Scorecard · Wagon Wheel · Ball History · Auto Play · Pause/Resume · Next Ball · Speed. |
| Auto Play | Stops on over complete, wicket, innings break, selection required, match end. |
| Engine extension (minimal, additive) | `game/Delivery.kt` (new) + `BallEv` fields: line, length, speed, shot, foot, approach, pitchX/Y, note, nonStriker, scoreRuns, scoreWk. `MatchSim.manualDecisions` API: `needsBatter/needsBowler/availableBatters/sendBatter/bowlerOptions/bowlerQuota/setBowler/overEvents/lastOverDone/lastWicketEv`. Engine logic itself unchanged; saves compatible. |
| Tests | `BroadcastEngineTest.kt` (bowler quota rules, manual batter flow, over events) + 7 screenshot tests (110–116). |

### Phase 2 — Management screens (broadcast theme)
- **Global chrome** (one change → every screen): `Panel`/`PanelHeader` royal-blue header bars with gold accent, `Tabs` royal filled + gold underline, sidebar selected row royal + gold edge, shell top bar & title strips (navy→royal gradient, gold rule), `DsCard`/`CcmCard` royal hairlines, Main Menu buttons (gold primary / royal gradient secondary). Logo & loading artwork untouched.
- **Dashboard:** new **Form Guide** (last 5 results W/L/D/NR + scores) and **Squad Pulse** (avg fitness/form/morale bars, injuries, in-form players) panels; responsive `pair()` layout stacks under 560dp content width.
- **New Game hub:** hero row (Multi-team [RECOMMENDED] / New Career / Create Team) + Quick Play & Tools row; `SectionStrip` component.
- **Player lists** (`PlayerRow`, used by Squad Hub, Squad tabs, Database, Transfers, Health): role-coloured accent + face ring, role chip, meta line, FIT/FRM micro-bars, `RatingTile` (gold ≥88 / green ≥80 / royal ≥70).
- **Squad Hub:** royal header strip, simplified rows.
- **Transfers:** broadcast purse board (balance / cap room / squad / overseas), gold SIGN buttons, offer panels with fee, scouting 4-col grid.
- Checked & left as-is (already broadcast-style, theme applied automatically): Calendar, Settings, Choose Franchise/State/Nation, Auction room, Stumpstagram, Squad Selection.

### Auction rule
- `Modes.auctionSeasonOpen()` — **no auction in the opening 2026 season** (real squads); Auction nav item, calendar event and "Mini Auction" button hidden; `startMiniAuction()` blocks with a message. **Auction opens from 2027** (season 2). Exception: Club mode "Build via Mega Auction" explicit choice.
- Tests: `AuctionSeasonRuleTest`, `ScheduleTest` updated. IPL setup copy updated.

### Final compliance pass (spec re-audit)
- **Bug fixed:** Over Summary opened for the *next* (empty) over — `lastOverDone` is a completed-over count while `BallEv.over` is zero-based. Now summarises the correct over with its 6 balls; resuming a match mid-innings shows the last over instead of "Start of innings".
- **Test matches:** status strip now shows **Day n • Session n** (was hidden for Tests).
- **New `BroadcastMatchUiTest`** (Compose UI, drives the real screen): ball-tracker tap opens the delivery history of that exact ball with engine line/length/shot; over summary opens automatically, blocks the next ball until confirmed; user-bowling innings forces an eligible bowler pick, quota 4 ov (T20) and no consecutive overs; wicket opens manual batter selection, no ball can be bowled until a batter is sent in, chosen batter is at the crease; auto play halts at the over/wicket and makes no progress while an overlay is up; Test header shows Day/Session.
- Decision overlays expose `Overlay: <title>` semantics (accessibility + testability).

### CI
- Emulator smoke-test step made `continue-on-error` (runner system-image download flake blocked a signed APK once). Unit + screenshot tests and signed build remain hard gates.

---

## 2. Commits (chronological)
`c7122a5`, `1f51923`, `8a06141` Phase 1 · `d4478c9` broadcast previews · `89bc985` broadcast polish · `2ed51e0`, `a901582`, `f199b58` Phase 2a · `0cf742e`, `cd0d167` Phase 2b · `e672f17` CI · `46e30ee` Phase 2c · `b8c3b7f`, `6c88318` Phase 3 · `c51db9b`, `b5c1991`, `506d530`, `4801147` compliance pass (over-summary fix, Test day/session, UI tests)

## 3. Screenshots (CI-rendered, `/home/user/screenshots/`)
`110–116_broadcast_*.png` (chase, Test, over summary + change bowler, new batter, result, small phone, portrait) · `p2_201_main_menu`, `p2_203_new_game`, `p2_33_dashboard_india`, `p2_209_squad_selection`, `p2_75_calendar_ipl`, `p2_37_squad_hub_ipl` · `p3_38_transfers_ipl`, `p3_39_dashboard_ipl_small_phone`, `p3_39_squad_hub_small_phone`

## 4. Preserved
Match engine, save format (migrations untouched), career/multi-team logic, tournaments, Stumpstagram/Yorker, commentary, 3D view, auction engine.

## 5. Known limitations
1. Byes / leg-byes are never produced by the engine — tracker indicators exist but will not appear until the engine generates them.
2. No left-hand batter mirroring on wagon wheel / pitch map (no batting-hand data in dataset).
3. "Record" column in player panels is empty when competition stats are not yet available.
4. Auction room and Calendar keep their existing internal layouts (new theme applied).
5. App is landscape-locked (`sensorLandscape`); there is no portrait management mode by design.
6. Emulator smoke test is advisory, not blocking.

