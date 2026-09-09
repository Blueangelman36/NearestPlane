# Nearest Plane

Two Android home-screen widgets in one app. Place either, both, or several of each.

**Nearest Plane** — the closest airborne aircraft to your GPS position: callsign,
airline, full type name ("Boeing 747-8"), scheduled route with estimated timing,
distance, compass bearing, altitude and ground speed. Emergency squawks are
flagged in red. Tap to force a refresh.

**Nearest METAR** — the closest reporting airport's current weather: station ID,
flight category colour-coded the way it is on a sectional, a decoded one-liner,
and the raw METAR in monospace. Tap for the full screen — raw METAR, decoded
METAR, raw TAF, and a period-by-period TAF breakdown.

Both are free to run. No API keys, no accounts, no paid tiers.

| | Nearest Plane | Nearest METAR |
|---|---|---|
| Source | airplanes.live + adsbdb | NOAA aviationweather.gov |
| Background refresh | 15 min | 30 min |
| Tap does | force refresh | open detail screen |
| Search area | 50 nm radius | ~45 nm box, nearest station wins |

---

## Getting it onto a phone

Two ways in, and the first one needs no PC at all.

### The APK (no build, no cable)

Every push to `main` builds an APK in GitHub Actions, and every `v*` tag turns
one into a release. On the phone, open this repo's **Releases** page, tap the
`.apk`, and allow installs from your browser when Android asks.

To cut a release:

```
git tag v1.1 && git push origin v1.1
```

If you'd rather not tag, the same APK is on the **Actions** tab under the latest
run, as the `nearest-plane-apk` artifact — though workflow artifacts expire and
release assets don't.

These are debug-signed builds, which is what makes them installable without a
keystore. That also means Android treats them as coming from an unknown source,
hence the prompt. If you later want Play-style signed releases, add a keystore
as a repository secret and switch the workflow to `assembleRelease`.

### From source, in Android Studio

The rest of this file. Take this route if you want to change anything —
the search radius, the refresh cadence, the ADS-B source.

---

## 1. Install Android Studio (on your PC)

Android Studio runs on your **computer**, not your phone. There is no phone
version — if the Play Store told you your device isn't compatible, that's why.
The phone only ever receives the finished app over a USB cable.

Download the Windows `.exe` from **developer.android.com/studio**. Roughly 1 GB,
plus several more for the SDK it pulls on first launch.

Requirements: 64-bit Windows 10 or 11, 8 GB RAM (16 GB is a much better time),
~10 GB free disk. It defaults to C:, but you can point the SDK at D: during setup.

## 2. Create the project

**File → New → New Project → Empty Activity**

| Field | Value |
|---|---|
| Name | `NearestPlane` |
| Package name | `com.connor.nearestplane` |
| Language | Kotlin |
| Minimum SDK | **API 26 (Android 8.0)** |
| Build configuration language | **Kotlin DSL** |
| Save location | `C:\Projects\nearest-plane` |

The package name must match exactly, or you'll be editing the `package` line in
every file. Let the first Gradle sync finish before touching anything.

## 3. Drop in these files

Copy over the generated skeleton, replacing where paths collide:

```
build.gradle.kts
settings.gradle.kts
gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/xml/nearest_plane_widget_info.xml
app/src/main/res/xml/weather_widget_info.xml
app/src/main/res/values/icon_colors.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/java/com/connor/nearestplane/*.kt        (13 files)
app/src/main/java/com/connor/nearestplane/wx/*.kt     (5 files)
```

Then **delete the `ui/theme/` folder** Android Studio generated under
`app/src/main/java/com/connor/nearestplane/`. This project defines its own theme
in `themes.xml`, and the leftover files won't compile without their imports.

The launcher icon ships with the repo as an adaptive icon, so the generated
`mipmap-*` PNGs are redundant — harmless either way, since `mipmap-anydpi-v26`
wins on every device this app supports (minSdk 26).

**Keep** the `gradle/wrapper/` folder — that's what runs the build, and its jar
is a binary that deliberately isn't in the repo. CI installs its own Gradle
instead, which is why there's no `gradlew` here.

## 4. Sync

**File → Sync Project with Gradle Files.** The first sync pulls Glance,
DataStore, WorkManager and play-services-location, so give it a few minutes.

If it complains about a Gradle or AGP version mismatch, the versions in the root
`build.gradle.kts` (AGP 8.7.2 / Kotlin 2.0.21) are newer or older than the
wrapper Android Studio generated. Easiest fix: change the versions in the root
`build.gradle.kts` back to whatever Android Studio originally put there, and
leave everything else alone.

## 5. Install on the phone

On the phone: **Settings → About phone → tap Build number seven times** to unlock
Developer options, then **Developer options → USB debugging → on**.

Plug in over USB, accept the RSA fingerprint prompt on the phone, then hit
**Run** (the green triangle) in Android Studio.

## 6. Grant location

The app opens to a setup screen.

1. Tap **Grant location access** → choose *While using the app*.
2. Tap **Open app settings** → Permissions → Location → **Allow all the time**.

Step 2 is not optional and it is not skippable from inside the app. Android 10
and later refuse to let an app request background location from a dialog — it has
to be set in Settings by hand. Skip it and the widgets silently stop updating
whenever the screen is off, which looks exactly like a bug in the code.

## 7. Place the widgets

Long-press an empty spot on your home screen → **Widgets** → scroll to
**Nearest Plane**. Two tiles are listed: *Nearest Plane* and *Nearest METAR*.
Drag out whichever you want.

Each populates within a few seconds of placement.

---

## Settings

Open the app any time. Every change redraws all placed tiles immediately.

### Background

| Option | What it does |
|---|---|
| **Material You** (default) | Follows your wallpaper and light/dark mode. True dynamic colour on Android 12+; a baseline Material 3 palette below that. |
| **Scrim** | A 55%-black translucent panel. Wallpaper shows through, text stays readable over anything. |
| **Transparent** | No background at all. |

### Text colour

Automatic, White, Black, Amber, Cyan, Green.

Automatic follows your theme on Material You and goes white on Scrim or
Transparent. The fixed colours exist because Glance — the widget UI toolkit —
has **no text shadow support**, unlike regular Compose. There's no way to make
white legible over an arbitrary bright photo, so if your wallpaper fights with
it, pick a colour that wins. Amber and cyan read well over most photographs.

### Text size

Compact (0.9×), Normal, Large (1.15×), Extra large (1.3×). Scales every
line on both widgets proportionally. On a small tile, larger text may clip —
drag the widget's edge to resize.

Flight category colours (VFR / MVFR / IFR / LIFR) deliberately ignore all of
this. They're a fixed aviation convention and recolouring them would make the
tile lie. They do brighten on dark backgrounds, where the darker sectional
shades disappear.

### Refresh now

Separate buttons for planes and weather, for testing without waiting out the
background interval.

### Diagnostics

Shows last run, last success, and the last error for each worker. Background
jobs fail silently by design — no console, no crash — so this is the only way
to tell a network failure apart from the system simply never running the job.

The distinction matters: if **last success** is old but **last run** is recent,
the fetch is failing. If **last run** itself is hours old, Android isn't
scheduling the work, and no amount of code fixes that — it needs the battery
exemption.

---

## How it works

**Location** — `FusedLocationProvider` at balanced-power priority, accepting a
fix up to 10 minutes old, falling back to last known position. Each tier reports
its own age, and a tile working from anything older than 20 minutes says so in
the corner: `position 40m old`. The distance and bearing on a tile are measured
*from you*, so a stale position makes a live-looking tile quietly wrong about
the one thing nothing else on it hints at. A widget refreshing
every 15 minutes doesn't need a GPS lock, and city-block accuracy is plenty when
the nearest plane is miles away.

**Refresh** — two independent paths, deliberately. WorkManager periodic jobs at
15 minutes (planes) and 30 (weather); 15 is the platform's hard floor and asking
for less silently gets rounded up. Alongside that, `updatePeriodMillis` in the
widget XML drives a second trigger through the system's AlarmManager, which
survives some conditions that defer WorkManager indefinitely. Whichever fires
first wins — unique-work policies mean a double fire costs one request, not two.

The backup path has a floor of its own: **30 minutes**, and values below it are
silently rounded up. So for the plane widget the two paths are not equals — the
alarm fires at half the rate of the job it backs up. It exists for the case
where WorkManager is being deferred outright, not to double the refresh rate.
Both widget XML files say so where someone would go to change the number.

Both are re-asserted every time you open the app, so a dropped schedule
repairs itself.

**Failures are isolated.** Only the aircraft or METAR fetch itself can fail a
refresh. The route and airframe lookups are enrichment: if adsbdb is down or
slow, you lose the route line and the full type name, never the tile.

**State** — each widget's data lives in its own Glance preferences store, so
tiles survive reboots and process death. The background setting is separate,
in an app-wide DataStore both widgets read.

The adsbdb cache is a third store, and it is **capped at 500 entries**, dropping
to 400 when it fills. You see a different aircraft most refreshes and DataStore
loads the whole file on every read, so without a cap this is a file that only
ever grows, for as long as the app is installed. Eviction is oldest-first and
costs one re-fetch per dropped entry.

**Aircraft type comes from the airframe database.** The app looks up the
Mode-S hex against adsbdb, which returns manufacturer and model — a hex maps to
exactly one airframe forever, so this is reliable and cached permanently. If
that misses, it falls back to the feed's own `desc` field, then the local ICAO
table in `AircraftTypes.kt`.

**Route data is not from ADS-B, and it's the weakest thing on the tile.**
Origin and destination aren't in the radio signal at all — no receiver anywhere
can give you them, including your own. The app queries adsbdb, which holds a
*static* callsign-to-route table. That works well for scheduled airlines, where
UAL123 flies the same city pair daily for a season. It fails for fractional and
charter operators: NetJets will use EJA888 for Teterboro today and Midway
tomorrow, and the table holds only one of them.

Three defences, since the data can't be made correct:

1. **Geometric contradiction check.** For an aircraft established en route
   (above 12,000 ft and faster than 150 kt), the app compares its actual track
   against the bearing to the claimed destination. More than 90° off means the
   aircraft is flying away from where the table says it's going, and the route
   line is dropped entirely. No route beats a wrong route.
2. **A `?` suffix** on routes from operators known to recycle callsigns —
   NetJets, Flexjet, VistaJet, Jet Linx, XO and similar — and on any bare
   N-number callsign, which is always a one-off flight. Timing estimates are
   suppressed for these too.
3. **A six-hour cache expiry** on routes, so a recycled callsign gets
   re-checked rather than sticking to yesterday's answer. An airframe *hit* has
   no expiry, since a hex maps to one aircraft forever — which is exactly why
   only a 404 is cached as a miss. A 429 or a 503 is a transient server problem,
   and writing that into a cache with no expiry would hide an aircraft's type
   name permanently. Misses expire after 30 days regardless, because adsbdb's
   database grows and a hex catalogued next week shouldn't stay blank.

The check can't catch everything. If the real and claimed destinations happen
to lie in similar directions, a wrong route passes. Treat any route without a
recognised airline name as indicative.

**Departure and arrival times are estimates, and they're marked with `~`.**
There is no free source for actual times. What the widget does is geometry:
distance to destination divided by current ground speed. That's reasonable at
cruise and optimistic during descent, and it ignores routing, holds and wind.
Time-since-departure is worse still — it assumes the whole leg was flown at the
current speed, which ignores taxi and climb. Treat the percentage as the most
trustworthy of the three.

**Flight category is computed locally**, not read from the API: lowest BKN/OVC
layer gives the ceiling, then the standard VFR / MVFR / IFR / LIFR thresholds.
The API's own field is inconsistent at smaller stations.

**A failed refresh never blanks a working tile.** Location has a three-tier
fallback — a current fix, then the system's last known position, then a position
cached from a previous successful run. If all three miss, or the network is
down, the tile keeps showing its last good reading and adds a small note in the
corner. The error state is only entered when there was never any data to begin
with. This is what removes the spurious "no GPS" message that used to appear
until you tapped.

Both tiles print how old their data is. That's deliberate — at a 15-minute floor,
a jet doing 450 knots can be 100+ nm from where the widget says it is. Tap before
you trust it.

---

## Things you might want to change

- **Plane search radius** — `RefreshWorker.RADIUS_NM`. Smaller means fewer
  "closest plane is a jet at FL380 forty miles away" results.
- **Ignore high cruisers** — add `.filter { (it.altitudeFt ?: 0) < 25_000 }` in
  `AdsbClient.nearest` to only surface aircraft actually near you.
- **Weather search area** — `boxDeg` in `AviationWeatherClient.nearestMetar`.
  0.75° is 45 nm; bump it if you're somewhere sparse. It's a half-width in
  nautical miles, not in degrees of longitude — `boxesAround` widens the box
  as you go north to keep it square, since 0.75° east-west is 45 nm at the
  equator but 22 in Fairbanks.
- **Always get a TAF** — right now the nearest station wins even if it's a small
  field that doesn't issue one. Filter to stations that returned a TAF if you'd
  rather always have a forecast.
- **Different ADS-B source** — one constant, `ENDPOINT` in `AdsbClient.kt`.
  `https://opendata.adsb.fi/api/v2/lat/%s/lon/%s/dist/%d` and
  `https://api.adsb.lol/v2/point/%s/%s/%d` are drop-in; all three return the same
  ADSBExchange v2 shape.
- **Point at your own Pi** — swap `ENDPOINT` for your dump1090 host's
  `/data/aircraft.json`. Note dump1090 has no `dst` or `dir` fields, so you'd
  compute distance and bearing from lat/lon yourself before sorting. There's
  already a haversine in `AviationWeatherClient` you can lift.
- **Refresh cadence** — the interval in each `schedulePeriodic`. Tap-to-refresh
  stays instant regardless.
- **More weather decoding** — `decodeWeather` in `WxModels.kt` covers the standard
  intensity / descriptor / phenomenon codes. Unknown groups pass through raw
  rather than getting dropped.
- **More aircraft type names** — `AircraftTypes.kt` holds about 180 ICAO type
  codes as a fallback. The feed's own `desc` field is preferred when present, so
  you'll rarely hit the table; add entries if you keep seeing a bare code.
- **Disable route lookups** — delete the `AdsbdbClient.lookup` call in
  `RefreshWorker`. Saves a request per new aircraft, but you lose the full type
  name too, since both come from the same request.
- **Stricter or looser route filtering** — the 90° contradiction threshold and
  the 12,000 ft / 150 kt en-route gate are in `RouteMath.assessRoute`. Tighten
  the angle to drop more suspect routes; add operator prefixes to
  `CALLSIGN_REUSERS` if you keep seeing bad ones from a particular company.

---

## Troubleshooting

**The widgets don't appear in the picker** — both receivers need
`android:exported="true"` in the manifest. The system enumerates widget providers
from a different UID, so a non-exported receiver is invisible to it.

**Widget stuck on "Tap to find a plane"** — WorkManager was never scheduled.
Open the app once; `MainActivity` schedules both workers on launch.

**"Location off" after granting** — you granted foreground only. See step 6.2.

**Widgets only update when you open the app** — this is the common one, and
it's battery optimisation, not a bug in the fetch. Open the app: if background
refresh is restricted, a card at the top says so with a one-tap fix. Check the
Diagnostics card too — if "last run" is hours old, the system isn't running the
job at all, which is a scheduling problem rather than a network one.

On Samsung and Xiaomi you usually need both: the in-app exemption *and*
Settings → Apps → Nearest Plane → Battery → **Unrestricted**.

**A widget is stuck saying "offline"** — shouldn't happen any more. A failed
refresh now leaves the last good reading in place and only adds a small corner
note, so the next success clears it automatically. If a tile is genuinely stuck,
the Diagnostics card shows the actual error text.

**Text invisible on the widget** — you're on Transparent over a light wallpaper.
Switch to Scrim, or pick a fixed text colour.

**Text clipped at the edge** — larger text sizes need a larger tile. Drag the
widget's edge to resize, or drop to Compact.

**No route shown on a plane** — one of three things, all normal: adsbdb has no
route for that callsign (typical for GA and military), the aircraft is
broadcasting no callsign, or the route failed the contradiction check and was
deliberately dropped.

**A route ends in `?`** — the operator recycles callsigns across unrelated
flights, so the destination is a guess from a static table. The aircraft type
and position next to it are still accurate.

**Wrong destination on a business jet** — expected, and the `?` is there to say
so. Fractional operators reuse callsigns daily. If it's badly wrong and the jet
is at cruise, the contradiction check should have caught it; if it didn't, the
two airports were probably in similar directions.

**`Unresolved reference: glance`** — Gradle sync didn't complete. Check that
`app/build.gradle.kts` has the `org.jetbrains.kotlin.plugin.compose` plugin;
Kotlin 2.0+ requires it separately from the Android plugin.

**`resource style/Theme.NearestPlane not found`** — `themes.xml` didn't make it
into both `res/values/` and `res/values-night/`.

**Weather tile says a station doesn't issue a TAF** — that's correct behaviour,
not a failure. Only larger airports issue them.

---

## Not for flight planning

This is a convenience readout. Decoded output is only as good as the parser here,
the data is minutes to tens of minutes old, and a real briefing comes from an
official source.
