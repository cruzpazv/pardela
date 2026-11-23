<p align="center">
  <img src="fastlane/metadata/android/es-ES/images/icon.png" width="128" alt="Pardela icon: a shearwater skimming the sea at sunset">
</p>

<h1 align="center">Pardela</h1>

<p align="center"><a href="README.md">Español</a> · <b>English</b></p>

<p align="center">Follow your flights like on the airport departure board.<br>
Large text, large buttons and clear alerts. No accounts: everything stays on your phone.</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-MIT-blue" alt="MIT licence"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84" alt="Android 8.0 or later">
  <img src="https://img.shields.io/badge/language-español%20%7C%20English-orange" alt="In Spanish and English">
</p>

> **Status:** first version (1.0.0). If you find a bug, please open an [issue](https://github.com/cruzpazv/pardela/issues).

The *pardela* (shearwater) is a Canary Islands seabird that crosses the ocean and always comes home.

## Install
**With [Obtainium](https://github.com/ImranR98/Obtainium) (recommended):** install Obtainium, tap *Add app* and paste `https://github.com/cruzpazv/pardela`. It will tell you about every new version and install it for you.

**Manually:** download `pardela.apk` from [Releases](https://github.com/cruzpazv/pardela/releases) and open it on your phone. Android will ask for permission to install apps from that source.

Every version is signed with the same key, so each one installs over the previous one without losing your data.

## What it does
- **Find a flight** by the number on the ticket (`IB3166`): status, times, gate, baggage belt and live progress.
- **Search by route:** all direct flights on a day between two airports, up to a year ahead.
- **Follow flights:** alerts for boarding, take-off, landing, delays and gate or belt changes, plus a reminder a few hours before departure.
- **Watch live:** a pinned notification updated every minute with altitude, speed and time remaining.
- **Airports:** departures and arrivals board. Save the ones you check often.
- **Routes:** an alert for each flight between two cities (for example, Gran Canaria → Madrid).
- **The aircraft:** photo, manufacturer, model, registration, airline logo and its flights from the last 7 days.
- **Map:** where the aircraft is, the planned route and the real path it has flown.
- **Punctuality:** how late that flight usually departs and arrives (with a key).
- **At the destination:** local time, current weather and the forecast for when it lands.
- **Share, calendar and voice:** send the flight by WhatsApp, add it to your calendar or hear its status read aloud.
- **Widgets** for the home screen (large and small) with the next flight.
- **Which plane is flying over?** Map and list of aircraft in the air near you or near an airport, with their route.

## Made for everyone
- Large text by default and buttons of at least 68 dp.
- **Simple mode** (optional): just find, follow and listen to flights.
- A quick guide when you start, available again from Settings.
- In Spanish or English: the phone's language, or the one you choose in Settings → Appearance.
- Light, dark or automatic theme.
- **Colour-blind palette** (Okabe-Ito). Every status also has its own symbol and text, never colour alone: ✔ landed, ⚠ delayed, ✕ cancelled, ✈ in flight.
- Animations can be turned off.
- Quiet hours: at night alerts arrive without sound, except cancellations.
- Each flight shows when it was last updated, and the app warns you when there is no connection.
- Alerts have buttons to **listen to** or **share** the flight without opening the app.
- Units: metres and km/h, or feet and knots.

## Privacy
- No accounts, no sign-up, no ads and no trackers.
- Everything is stored in a single file on the phone. Android cloud backup is turned off.
- To move your data to another phone: Settings → Backup. The backup does not include the API key.
- Location (approximate only) is requested just for "Which plane is flying over?". It is not stored and does not leave the phone, except to ask the radar for aircraft in that area.
- The app only connects to the data services in the table below.

## Where the data comes from
| Source | Key needed? | What for |
|---|---|---|
| [adsb.fi](https://adsb.fi) / [adsb.lol](https://adsb.lol) | no | live aircraft positions and 24-hour paths (open ADS-B radar; adsb.lol data under the ODbL licence) |
| [adsbdb.com](https://www.adsbdb.com) | no | each flight's route and aircraft details |
| [planespotters.net](https://www.planespotters.net) | no | aircraft photo, credited to its author |
| pics.avs.io | no | airline logos |
| [OurAirports](https://ourairports.com) | no | list of 3,245 airports, built into the app |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | no | flight map (© OpenStreetMap contributors) |
| [Open-Meteo](https://open-meteo.com) | no | weather and local time at the destination (CC BY 4.0) |
| [AeroDataBox](https://rapidapi.com/aedbx-aedbx/api/aerodatabox) | **optional** | schedules, delays, gates, full airport boards, route search and aircraft history |

**Without a key**, Pardela works with radar: you see the flights that are in the air, their route and their aircraft.

**With a free AeroDataBox key** you also get schedules, delays, gates and the full board of every airport. To get one:
1. Create a free account at rapidapi.com.
2. Subscribe to AeroDataBox's BASIC plan.
3. Paste the key into the app.

The key is only stored on your phone. The free plan has about 600 requests a month, so Pardela spaces them out (every 30 min near departure, every 3 h otherwise, once a day for flights far ahead) and shows how many are left in Settings.

---

## For developers

Kotlin + Jetpack Compose, no proprietary libraries. Dependencies: OkHttp, Coil, kotlinx.serialization, WorkManager and osmdroid. The code and comments are in Spanish.

**Build:** JDK 17, Android SDK 35 and `./gradlew assembleRelease`. **Tests:** `./gradlew testDebugUnitTest`. GitHub Actions runs the tests and builds on every push.

**Texts:** English in `res/values/strings.xml` (default) and Spanish in `res/values-es/`. The tests check that both have the same keys.

**Releases:** add the changes to [CHANGELOG.md](CHANGELOG.md), then `git tag vX.Y.Z && git push --tags`. The version comes from the tag. Actions tests, builds, signs and publishes the Release.

## How it was made
Pardela was developed with the help of [Claude](https://claude.com/claude-code), an AI assistant by Anthropic. The design, decisions and testing are by [cruzpazv](https://github.com/cruzpazv). Commits it took part in say so with a `Co-Authored-By` line.

## Licence
[MIT](LICENSE). Some icons come from Material Icons (Google, Apache 2.0 licence). Airport data: OurAirports (public domain). Photos belong to their authors on planespotters.net, and logos are trademarks of their airlines.
