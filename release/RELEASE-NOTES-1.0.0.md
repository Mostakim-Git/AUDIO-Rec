> **Superseded by [1.0.1](https://github.com/Mostakim-Git/AUDIO-Rec/releases/tag/v1.0.1).**
> This build renders no interface: its drawer shell never found its panes and never laid the
> window out. 1.0.1 fixes that, along with four blocks of UI that were built and then thrown
> away. Download 1.0.1 instead.

# AUDIO-rec 1.0.0

**USB Audio Recording & Production Workstation** — an offline, installable Android app for
musicians, podcasters and recording engineers.

**Author:** Mostakim Billah · **Package:** `com.mostakim.audiorec` · **Requires:** Android 10 (API 29) or newer · **No root**

## Download

**[⬇ AUDIO-rec.apk](https://github.com/Mostakim-Git/AUDIO-Rec/releases/download/v1.0.0/AUDIO-rec.apk)** —
585,098 bytes (571 KiB), signed (v1 + v2 + v3)<br>
SHA-256 `ecd1933881acb8c6377a570cee7725609ea4e6cafb17fd2295ccbf7e0de276b0`

GitHub reports that same SHA-256 for the asset stored on this page, so what you
download is byte-for-byte the file that was built and signed here. A second copy
lives in the repository at
[`release/AUDIO-rec.apk`](https://github.com/Mostakim-Git/AUDIO-Rec/blob/v1.0.0/release/AUDIO-rec.apk).

---

## Why the file is small (and why that is a good thing)

The APK is complete — 97 entries, 340 app classes, all 12 screens, every encoder, every icon.
It is small because it contains **no third-party libraries at all**: no AndroidX, no Kotlin
runtime, no support libraries. The whole app is plain Java against the Android framework,
built without Gradle.

| what | uncompressed |
|---|---|
| Dalvik bytecode (340 classes) | 367 KB |
| Drawables + brand artwork | 153 KB |
| Launcher icons (5 densities) | 54 KB |
| Signature (v1/v2/v3) | 21 KB |
| Resource table | 18 KB |
| Manifest | 7 KB |

For comparison, an *empty* app created by Android Studio already weighs ~3.5 MB
because of AndroidX alone. Nothing is missing here: no native code is included, so the same
file installs on arm64, arm32 and x86 phones, and everything works with the network off
forever.

## Install

1. Download `AUDIO-rec.apk` above.
2. Open it on the phone and allow "Install unknown apps" for your browser/file manager
   (the app is distributed directly, not through Play).
3. Launch **AUDIO-rec**, plug in a USB audio interface and accept the USB permission dialog.
4. Pick **Storage** in the sidebar to point recordings at an SD card or any folder you like.

## What it does

* **USB audio recording** — UAC1/UAC2 isochronous capture from audio interfaces and USB
  DACs (Jcally JM6, M-Audio Duo/Fast Track/Fast Track Pro, PreSonus 22VSL/44VSL,
  Blue Snowball/Yeti/Yeti PRO, RME Babyface, Zoom H2/H4, generic HiFi DACs).
* **WAV / FLAC / AIFF / OGG-Opus** recording — no MP3 anywhere (patent-free: compressed
  takes use Opus at 48 kHz). WAV switches to RF64 automatically past 4 GB.
* **Mono, stereo and multichannel**, 16/24/32-bit per device, up to the device's maximum
  sample rate; playback out of the first two outputs on multichannel interfaces.
* **Real-time level meters** with peak hold, tap to clear, plus a **Monitor** button for
  setting levels before recording.
* **Dashboard** with sidebar navigation and full CRUD for Sessions, Audio Tracks,
  Device Presets and Export Files; persistent SQLite storage.
* **Mixer** with internal gain, volume and mute when the device exposes them.
* **Recordings folder** anywhere you can write (external SD included), disk-space display,
  rename/delete, tape-style playlist, and **sharing to Drive/WhatsApp**.
* Buffer size 1024–16384 frames, input/output device selection, device presets.
* **Fully offline**: no login, no account, no password, no network permission at all.

## Verification

This build is checked by six off-device suites that run without a phone or emulator
(`bash tools/test/run.sh`), including a release gate that inspects the signed APK:

| what | result |
|---|---|
| In-process container checks (WAV/FLAC/AIFF/Ogg, all rates and depths) | 133 checks pass |
| Foreign-file reader corpus (RF64, AIFF-C, 8-bit, float, truncated, garbage) | 108 checks pass |
| 48 kHz Opus resampler vs a single-pass reference, 12 rates | 54 checks pass |
| Sharing rules for the Drive/WhatsApp content provider | 38 checks pass |
| Independent container scorer | 291 checks pass |
| Signed-APK release gate: no network permission/code, no MP3 encoder, all components present, adaptive icons, alignment, signature | 34 checks pass |
| Minimum-SDK audit against the Android 10 platform jar | every API-30+ symbol inlined or SDK-guarded |

The APK is reproducible: rebuilding from these sources yields a byte-identical file.

## Known limits

* Installing and recording with your own interface is the one step that has to happen on
  real hardware — USB capture, permissions and the audio stack cannot be exercised in a
  container.
* "All files access" is requested when you want to record straight onto an SD card; without
  it Android limits writing to the app's own folders (recording still works there).
