# Running guide

Two apps, each with its own way to run. This document is short and precise.

---

## One-line summary

| What | Command | Prerequisite |
|------|---------|--------------|
| Tests (now) | `npm test` | Node |
| Desktop (dev) | `npm run dev:desktop` | Electron binary |
| Desktop (run build) | `npm run run:desktop` | Electron binary + build |
| Build `exe` | `npm run desktop:dist` | Electron binary |
| Android app | Android Studio → Run | JDK 17 + Android SDK |

---

## 1. Tests — work right now

```bash
npm test
```

Nothing but Node is required. Six stages run and the summary explicitly says what was **not** tested (so a green run is not mistaken for "everything works").

---

## 2. Desktop app

### Step 1: Electron binary

`npm install` downloads the Electron binary, but on some networks that download is left half-done (GitHub returns a 302 and zero bytes). If `desktop/node_modules/electron/dist/electron.exe` is missing, run:

```bash
cd desktop
ELECTRON_MIRROR="https://registry.npmmirror.com/-/binary/electron/" \
  node node_modules/electron/install.js
```

If that also stalls (long connections drop):

```bash
cd ..
node tools/fetch-electron.mjs      # chunked download with resume
```

`fetch-electron.mjs` downloads in 512 KB chunks and resumes from where it stopped, so if it drops just run it again until it finishes.

Then verify:

```bash
ls desktop/node_modules/electron/dist/electron.exe
```

### Step 2: Run

```bash
npm run dev:desktop
```

This runs `electron-vite dev`: React UI with hot-reload and opens the app window.

To run only the production build (no hot-reload):

```bash
npm run desktop:build
npm run run:desktop
```

### Building the installer

```bash
npm run desktop:dist        # → desktop/release/NetBridge Setup x.x.x.exe
```

---

## 3. Android app

This machine **cannot build** (no JDK 17, no Android SDK). On a machine with Android Studio:

1. Android Studio → **Open** → folder `mobile/`
2. Wait for Gradle Sync
3. Connect the phone with USB and enable USB Debugging
4. Press **Run ▶**

Or only build the APK:

```bash
cd mobile
gradlew.bat assembleDebug      # Windows
./gradlew assembleDebug        # Linux/macOS
```

Output: `mobile/app/build/outputs/apk/debug/app-debug.apk`

Then install on the phone and follow `docs/testing.md` for a real test.

---

## 4. Correct order for a real test

1. `npm test` — make sure the base is healthy
2. `npm run dev:desktop` — desktop app up
3. Run/install the Android app in Android Studio
4. **Turn on phone VPN** (All-apps mode)
5. **Turn on hotspot or USB tethering**
6. Press Share on the phone app
7. Desktop app: Device tab → scan → 6-digit code → pair
8. Connect tab → big button
9. Final check with `curl` per `docs/testing.md` step 4

---

## 5. If the desktop app window does not open

| Symptom | Meaning | Action |
|---------|---------|--------|
| `Electron failed to install correctly` | Binary missing | Run step 1 above |
| Window opens but white | UI not loaded | `npm run desktop:build` then `npm run run:desktop` |
| `EADDRINUSE` on 18080 | Port busy | Change port in app settings or close the previous instance |
| Phone not found | mDNS broken on some ROM hotspots | Use manual IP (`192.168.42.129` or `192.168.43.1`) |
