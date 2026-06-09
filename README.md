# Cat Meme Stickers — WhatsApp Sticker App

A WhatsApp sticker pack app featuring 15 hand-crafted cat meme stickers:
Grumpy Cat, Nyan Cat, This Is Fine, Smudge, and Business Cat.

---

## Project Structure

```
CatMemeStickers/
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   ├── contents.json          ← sticker pack metadata
│   │   │   └── contents/              ← all 15 .webp stickers + tray_icon.webp
│   │   ├── java/com/catmemestickers/
│   │   │   ├── MainActivity.kt        ← main screen + "Add to WhatsApp" logic
│   │   │   ├── StickerContentProvider.kt  ← serves images to WhatsApp
│   │   │   ├── StickerPackLoader.kt   ← reads contents.json
│   │   │   ├── StickerAdapter.kt      ← RecyclerView grid adapter
│   │   │   └── StickerPack.kt         ← data classes
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── layout/item_sticker.xml
│   │       └── values/{strings,themes}.xml
└── build.gradle / settings.gradle
```

---

## Setup in Android Studio

1. **Open the project** — File → Open → select the `CatMemeStickers` folder
2. **Sync Gradle** — click "Sync Now" when prompted
3. **Add launcher icons** — replace the placeholder mipmap drawables (Android Studio
   right-click res → New → Image Asset → use any cat image)
4. **Run on device** — plug in your Android phone, hit ▶ Run

> Min SDK: 21 (Android 5.0+)  
> Target SDK: 34

---

## How WhatsApp Stickers Work

WhatsApp queries your app via `StickerContentProvider` using two URIs:

| URI | What WhatsApp gets |
|-----|--------------------|
| `content://<authority>/metadata` | Pack name, publisher, tray icon |
| `content://<authority>/<packId>/stickers` | List of sticker filenames + emojis |
| `content://<authority>/<packId>/<file.webp>` | Actual sticker image bytes |

The `Add to WhatsApp` button fires an Intent with your pack ID and authority.
WhatsApp calls back with `RESULT_OK` on success.

---

## Adding More Stickers

1. Drop new `.webp` files (512×512px, <100KB, transparent bg) into `assets/contents/`
2. Add entries to `contents.json` under `stickers`
3. Max 30 stickers per pack; min 3

---

## Publishing to Play Store

1. Increment `versionCode` in `app/build.gradle`
2. Build → Generate Signed Bundle/APK → Android App Bundle
3. Upload to Google Play Console
4. WhatsApp will deep-link to your Play Store listing via `android_play_store_link` in `contents.json`
