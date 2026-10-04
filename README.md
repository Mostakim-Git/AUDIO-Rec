# AUDIO-rec

A full-stack recording workspace for musicians, podcasters and audio engineers, with a responsive Android-ready interface, an installable PWA, and a Capacitor Android project.

## Run the web app

```sh
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). First visit opens a seeded demo workspace; the account menu can sign out, and the sign-in screen supports registration and login. Build production assets with `npm run build`, run the production server with `NODE_ENV=production npm start`, and run the audio-format checks with `npm test`.

## Install on Android

### Install the PWA

Deploy AUDIO-rec over HTTPS with the API available at the same origin under `/api`. Open the site in Chrome on Android and choose **Install app** (or use Chrome’s menu → **Install app** / **Add to Home screen**). The app shell and static assets are cached for launch; signing in and loading or saving workspace data still requires the API connection.

### Build the Capacitor Android app

The repository includes a native Android project under `android/`, with AUDIO-rec launcher/splash artwork and microphone permissions. To build or run it, install Android Studio with the Android SDK and a Java version compatible with the generated Gradle project, then:

```sh
npm install
npm run android:sync
npm run android:open
```

`android:sync` builds the web UI and copies it into the Capacitor project. With the Android SDK configured, `npm run android:apk` creates a debug APK at `android/app/build/outputs/apk/debug/app-debug.apk`; on Windows, run `gradlew.bat assembleDebug` from `android/`. In Android Studio, select an emulator or connected device and run the app, or use **Build → Generate Signed Bundle / APK** to make a distributable release. With a configured SDK/device, `npm run android:run` launches the app directly.

The app bundle contains the client, not the Express API. Before building, point the client at a deployed HTTPS API (include `/api` in the URL) and allow the Capacitor WebView origin on the API:

```sh
# macOS/Linux: create a debug APK with the remote API URL baked in
VITE_API_URL=https://your-api.example.com/api npm run android:apk

# Or prepare the Android Studio project instead
VITE_API_URL=https://your-api.example.com/api npm run android:sync
npm run android:open
```

Set `CORS_ORIGINS=https://localhost` on the API when it is cross-origin from the Android app. Keep the API on HTTPS so microphone capture and secure session cookies work as intended. For a hosted PWA and a cross-origin API, include both the PWA origin and `https://localhost` in `CORS_ORIGINS`. The same-origin web development setup needs no `VITE_API_URL`; it uses `/api`. On Windows, set `$env:VITE_API_URL` in PowerShell before running the npm command.

The `Build Android APK` GitHub Actions workflow also runs on pushes to the session branch and uploads `AUDIO-rec-debug-apk` as a downloadable workflow artifact. To bake in the production API URL for GitHub-built APKs, add a repository Actions variable named `AUDIOREC_API_URL` with the HTTPS base URL ending in `/api`. Android Studio and the Android SDK are required for local APK builds or device testing.

## What is included

- Session, track, device-preset and export-file CRUD, with optimistic editing and on-disk persistence in `data/workstation.json`.
- Password authentication (scrypt hashes) and seven-day, HTTP-only session cookies.
- Responsive desktop navigation and Android-friendly mobile tabs, safe-area spacing and PWA install affordance.
- Browser USB-input selection, routing for channels exposed by the browser, software gain, live monitoring and capture to uncompressed 16-, 24- or 32-bit PCM WAV.
- Lossless FLAC export using `libflacjs`, up to 24-bit PCM. FLAC is generated from the saved WAV source when downloaded; a 32-bit recording must be exported as 32-bit WAV or intentionally down-converted to 24-bit FLAC.
- Seed data for music, podcast, acoustic and field-recording sessions, ten tracks and Focusrite/Zoom/DAC device presets.

## Audio-engine boundary

The Android project wraps the same browser-based Web Audio capture engine; it is not a native USB audio driver. Capture requests disabled echo cancellation, noise reduction and automatic gain control, but Android and the selected USB device still own the driver and clock. Available channels and sample rates vary by device, browsers may resample, and monitoring may add latency. Selecting a Focusrite, Zoom or DAC preset only loads settings—it does not install, select or bypass that device’s USB driver. The app cannot promise bit-perfect/exclusive capture, guaranteed 32-bit/384 kHz hardware capture, or zero-latency monitoring. Those guarantees require a separate native audio engine and device-specific integrations.

Recorded WAV data is stored with the workspace in the local JSON database. This storage and the demo authentication are suitable for a local prototype, not a multi-user production deployment; use a transactional database, HTTPS, secret management and production session controls before hosting real recordings publicly.
