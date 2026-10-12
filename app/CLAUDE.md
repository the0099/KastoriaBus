# Kastoria Bus App

## Goal
An Android app for the Kastoria city buses (Αστικό ΚΤΕΛ Καστοριάς), similar to the OASTH app for Thessaloniki: lines, stops, a map, timetables and live arrivals.

## About me
I'm learning Android development. Work in small steps, explain what you do and why in simple terms, and tell me how to test each step on my phone. After each working step, commit to Git with a clear message.

## Tech decisions (already made)
- Kotlin + Jetpack Compose, Empty Activity template, minimum SDK 26, Kotlin DSL for Gradle
- Networking: Retrofit + Gson (may already be added to app/build.gradle.kts, check first)
- INTERNET permission in AndroidManifest.xml (may already be added, check first)
- Map: MapLibre (org.maplibre.gl:android-sdk-opengl) with OpenFreeMap tiles, no API key
- Planned: Room for offline caching
- All user-facing text in strings.xml, in Greek (default) and English

## Data source
The data comes from the API behind https://kastoria.citybus.gr/el/stops. Full details are in kastoria-api-notes.txt in the project root: read it before working on anything data-related.
Key points:
- Base URL https://rest.citybus.gr, agency code 115, language el or en
- Every call needs "Authorization: Bearer <token>". There's no login API: the token is inside the HTML of /el/stops as `const token = '...'`. It's a JWT that lasts about 48 hours. Get it from the page, cache it until it expires, and refresh it on a 401. Never hardcode the token.
- Endpoints: /stops (all stops), /lines (lines + routes + colors), /routes/{routeCode}/sequence, /api/v1/115/lines/{lineCode}/points (route shapes, no language segment), /trips/stop/{stopCode}/day/{0-6} (timetable, 0 = Sunday), /stops/live/{stopCode} (live arrivals + bus positions)
- /stops/live returns 404 when no buses are coming: treat it as an empty list, not an error
- Be gentle with the server: cache stops, lines and shapes; refresh live data every 20–30 seconds only while a stop screen is open
- We don't have permission from the site owner (e-analysis.eu / KTEL Kastoria) yet. I plan to ask before publishing.

## Plan
1. Done: project created, app runs on my phone, API mapped
2. Done: data layer (token fetching, Retrofit API interface, data classes, repository)
3. Done: bottom tabs Stops + Lines (OASTH style). Stops: nearest first with location, A-Z without (or when >30 km from Kastoria), search, favorites bar + hearts. Lines: sorted by number, favorites bar + hearts, line screen with route chips and its stops. Stop screen: today's upcoming departures from the timetable
4. Done: new look (brand colours blue/orange/teal, own colour per line because the API gives all lines #787878, gradient headers, cards, metro-style line timeline) and Map tab (MapLibre OpenGL build + free OpenFreeMap tiles: all stops, line filter with route shapes, tap a stop for a card, my-location button). Note: use the `android-sdk-opengl` MapLibre artifact; the default Vulkan one crashed on the emulator
5. In progress: estimated bus positions from the timetable (data/estimate: RoutePlan). Per route, timetables of ~6 checkpoint stops are downloaded once a day; trip k = the k-th time at each checkpoint (verified: route 001 has the same trip count at every stop; the last stop has no times, so its arrival is extrapolated). Shown on the map (pick a line) and on the line screen, always labelled as an estimate. Still to do: real GPS from /stops/live when available, timetable by day on the stop screen
6. Extras: favorites, offline timetables, dark mode, maybe a widget or notifications
7. Publish: icon, privacy policy, closed testing, Google Play

## Open questions
- No real example of a non-empty /stops/live response yet (field names are known from the site's JavaScript, see the notes file)
- Route "direction" (1 vs 2) does NOT mean outbound/return (e.g. round trip 001 and one-way 031 are both 2). Μετάβαση/Επιστροφή is computed in data/RouteDirections.kt from stop positions: most main routes are one round trip ΑΦΕΤΗΡΙΑ → village → ΑΦΕΤΗΡΙΑ, split at the halfway point of the distance travelled