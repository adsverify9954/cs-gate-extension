# The Gate — CloudStream Extension

Tumhara personal OTT addon **The Gate** (Stremio protocol) ab CloudStream ke andar bhi.
Ye extension The Gate worker se catalog + streams uthata hai: **Anime, Movies, Series, TV**.

## Structure

```
cs-gate-extension/
├── build.gradle.kts              ← root config (namespace: com.sgdys.gate)
├── settings.gradle.kts           ← modules auto-include hote hain
├── .github/workflows/build.yml   ← push pe GitHub Actions khud .cs3 banata hai
└── GateProvider/                 ← tumhara extension module
    └── src/main/kotlin/com/sgdys/gate/
        ├── GateProvider.kt       ← asli provider (catalog/search/meta/links)
        └── GatePlugin.kt         ← plugin registration
```

## The Gate API (jo ye extension use karta hai)

| Endpoint | Kaam |
|---|---|
| `/catalog/:type/:id.json` | Main page ke 4 sections |
| `/catalog/:type/:id.json?search=Q` | Search — **query param** (path-wala `search=Q.json` format 302 redirect deta hai, kaam nahi karta) |
| `/meta/:type/:id.json` | Detail page + episodes |
| `/stream/movie/:id.json`, `/stream/series/:id:S:E.json` | Streams — series mein `id:S:E` **colon** format (slash format 404 deta hai) |

- Stream response ya to **direct `.m3u8`/`.mp4` + `behaviorHints.proxyHeaders`** hota hai (worker-resolved, e.g. UFOload) — extension headers ke saath direct playback deta hai
- Ya **embed page URL** (Streamtape/MixDrop) — CloudStream ke built-in extractors se resolve hota hai

- Manifest: `https://my-anime-gate.unityads9954.workers.dev/manifest.json`
- Catalogs: `movie/movies`, `series/series`, `series/anime`, `series/tv` (sab pe `search` supported)

## Local build (Windows)

```bash
export JAVA_HOME="C:/Users/sgdys/Tools/jdk17"
cd "C:/Users/sgdys/Desktop/the gate/cs-gate-extension"
./gradlew.bat GateProvider:make
```

Output: `GateProvider/build/GateProvider.cs3`

Phone pe direct test (ADB connected, CloudStream installed):

```bash
./gradlew.bat GateProvider:deployWithAdb
```

## GitHub se publish (phisher98 jaisa)

1. Is folder ko GitHub pe push karo (naya repo, e.g. `cs-gate-extension`)
2. Actions pehle push pe khud build karega aur **`builds` branch** me `.cs3` + `plugins.json` daal dega
3. Repository URL: `https://raw.githubusercontent.com/<user>/<repo>/builds/repo.json`
   - ⚠️ CloudStream me **`repo.json`** (manifest object, `manifestVersion: 2` + `pluginLists`) add hota hai —
   `plugins.json` (array) nahi! plugins.json ka URL dala to *"repo.json did not return a JSON object"* error aata hai.
4. CloudStream app → Settings → Extensions → Add Repository → ye URL paste karo ✅

## Hosts / Extractors

`loadLinks()` dono tarah handle karta hai: worker-resolved **direct streams** (`.m3u8`/`.mp4` +
`proxyHeaders`) headers ke saath direct callback, aur **embed pages** CloudStream ke
**built-in extractors** se. Agar aisa host aaye jiska built-in extractor nahi hai
(Abyss, EarnVids, Byse…), to custom `Extractor` class bana ke `GateProvider.kt` ke
`loadLinks()` me `loadExtractor()` se pehle usko handle karo.
Reference ke liye `SaurabhKaperwan/CSX` ya `KSHITIJ8473/raghav` ke `Extractors.kt` dekho.

## Common changes

- **Naya catalog** The Gate me add kiya? `GateProvider.kt` me `gateCatalogs` list me ek line add karo
- **Worker URL badla?** `GateProvider.kt` me `mainUrl` update karo
- **Version bump**: `GateProvider/build.gradle.kts` me `version = 1` → `2`, warna CloudStream update nahi dikhayega
