# AUDIO-rec 1.0.3

**USB Audio Recording & Production Workstation** — an offline, installable Android app for
musicians, podcasters and recording engineers.

**Author:** Mostakim Billah · **Package:** `com.mostakim.audiorec` · **Requires:** Android 10 (API 29) or newer · **No root**

## Download

**[⬇ AUDIO-rec.apk](https://github.com/Mostakim-Git/AUDIO-rec/releases/download/v1.0.3/AUDIO-rec.apk)** —
589,179 bytes (575 KiB), version 1.0.3, signed (v1 + v2 + v3)<br>
SHA-256 `7b300c82c81a41883c0ebecb82a93fbf1a4624452955944829537203e44cf3f7`

GitHub stores that digest for this asset, so the download is byte-for-byte the file that was
built, checked and signed here, and you can confirm it yourself with `sha256sum AUDIO-rec.apk`.
A second copy lives in the repository at
[`release/AUDIO-rec.apk`](https://github.com/Mostakim-Git/AUDIO-Rec/blob/v1.0.3/release/AUDIO-rec.apk).

**This build contains the recording force-stop fix from 1.0.2 unchanged** — if you are on 1.0.0
or 1.0.1, this is the build you want. The whole story is in
[the 1.0.2 notes](https://github.com/Mostakim-Git/AUDIO-Rec/releases/tag/v1.0.2); in short:

> `AudioRecord.read()` counts **bytes** for every PCM encoding and **samples** only for float, and
> the capture loop divided the count by the channel count in both cases — so a 4096-byte 16-bit
> stereo read became 2048 frames instead of 1024, the block was indexed past its end, the capture
> thread died of `ArrayIndexOutOfBoundsException`, and Android takes the whole process down with
> an uncaught thread death. Frame arithmetic now goes through one tested place
> (`Pcm.framesFromBytes`), nothing a listener or an audio thread does can kill the app, and MP3
> can no longer be imported or decoded.

## What is new in 1.0.3

* **The storage gauge is laid out.** `DiskBarView` — the bar with the "N free" and total figures
  on the Storage page — had no `onMeasure`, and the pages sit inside a `ScrollView`, which offers
  a wrap-content child no height at all: the gauge measured 0 × 0, so its bar and both figures
  were painted outside the box it had been given. It now measures the box its drawing needs, and
  the figures are also in the view's content description (accessibility, screen readers).
  This is the last item on the feature list that was drawn but not visible.

* **The last-take card is only there when there is a take.** On the Recorder page the card that
  appears after a recording was saved used to exist, empty and invisible, from first launch. It
  is now hidden until it has something to say.

* **The package inspection is itself checked.** `tools/zip_check.py` now parses the dex
  string/type/method tables and requires the released bytecode to declare the byte-aware frame
  arithmetic — the shape a build without the fix does not have. `tools/test/zip_check_test.py` is
  the negative control: it shows the released package passing, renames that helper inside its dex
  and requires the checker to reject the package. A check that only ever sees good input proves
  nothing, so it now has to fail on a mutated one as part of `tools/test/run.sh`.

* **The UI harness checks the storage gauge** (a real box, and readable figures) and no longer
  complains about empty containers that are hidden. 150 checks now, up from 148.

## The feature list, item by item

Every item on your list is in this build. The headless UI harness opens each chooser and reads
what it offers, and the checklist prints from `tools/test/ui/run.sh`.

| feature | where it lives | checked |
|---|---|---|
| USB audio recording (UAC1/UAC2, USB host) | `audio/AudioEngine` + `audio/UsbAudioProbe` + `res/xml/usb_device_filter.xml` | Recorder page, per-device presets |
| Mono, stereo, multichannel | channel picker (1/2/4/6/8), channel index mask on Android 12+ | channel chooser offers 1, 2, 4 |
| Stereo playback on multichannel interfaces | `PlaybackEngine.buildTrack` uses `min(2, channels)` | OUTPUT meters on Recorder + Library |
| 16- / 24- / 32-bit per device | depth picker filtered by what the device reports | depth chooser offers 16, 24, 32 |
| Up to the device's highest sample rate | rate picker up to `maxSampleRate()` | rate chooser offers up to 192000 |
| Input / output selection | Devices page per-device buttons, Settings pickers | both choosers open, "System default" first |
| Buffer size 1024 – 16384 frames | `Formats.BUFFER_SIZES` + buffer chooser | chooser offers 1024 … 16384 |
| WAV / FLAC / OGG / AIFF, never MP3 | four writers, format chooser, import and playback guards | chooser offers WAV/FLAC/AIFF/OGG, no MP3 |
| Level meters for recording **and** playback | `LevelMeterView`, Recorder INPUT/OUTPUT, Mixer, Library | INPUT and OUTPUT meters on the Recorder page |
| Peak hold, tap the meter to clear | `LevelMeterView.onTouchEvent` → `clearPeaks()` | explained on the page |
| Monitor button for setting levels | "Monitor: off/on" in the transport card | present |
| Load wav / aiff / flac / ogg for playback | Library "Pick audio file" + "Scan folder", Playlist folders | both buttons present |
| Rename or delete a recording | Library row menu → Rename / Delete / Details | take menu offers Rename, Delete |
| Available disk space | Storage gauge (now laid out), free/total figures, recordable-time estimate, destination card | gauge has a box and readable figures |
| Internal gain, volume, mute when available | Mixer: per-channel trims, master, MUTE, device controls | CHANNEL TRIM and MUTE present |
| Set the recording folder (e.g. external SD) | Storage "Record here" / "Type a path?", Recorder "Change folder" | volume list present |
| Basic directory playlist, no fancy graphics | Playlist page: folders, sort, Up, add-all, auto-advance | Folder + Auto-advance present |
| Share through Drive / WhatsApp / etc. | `share/ExportProvider` + the share sheet from Library, Playlist, Exports | Share present |
| Android 10+ (API 29), no root | `minSdkVersion 29`, no privileged APIs | minimum-SDK audit |
| Microphone + all-files access requested properly | runtime prompts, Storage explains app-scoped vs full access | Storage access card |

## Verification

Everything runs off-device — no phone, no emulator, no Android Studio:

```bash
bash tools/build.sh --release      # APK into release/, signature verified
bash tools/test/run.sh             # every layer below
```

| what | result |
|---|---|
| Container round-trips + **device-read arithmetic** (the force-stop) | 817 checks pass |
| Independent container scorer (re-parses every artifact) | 291 checks pass |
| Foreign-file reader corpus (RF64, AIFF-C, 8-bit, float, truncated, garbage) | 108 checks pass |
| 48 kHz Opus resampler vs a single-pass reference, 12 rates | 54 checks pass |
| Sharing rules for the Drive/WhatsApp content provider | 38 checks pass |
| Headless UI harness: shell, all 12 pages, choosers, **the feature checklist** | 150 checks pass |
| Static rules (format strings, extras, view tree, byte/sample mistakes) + mutation test | 6/6 defect shapes caught |
| Portable zip inspection of the signed APK, including the shipped dex declaring the byte-aware frame arithmetic | 25 checks pass |
| Negative control: the inspection rejects a package whose dex lost that helper | 1 mutation caught |
| Signed-APK release gate (offline proof, no MP3, components, branding, alignment) | 39 checks pass |
| Minimum-SDK audit against the Android 10 platform jar | every API-30+ symbol inlined or SDK-guarded |

The read arithmetic is covered by 684 assertions across the three byte widths (16-, 24- and
32-bit), one to eight channels and partial reads, including the exact 4096-byte 16-bit stereo
case that used to kill the capture thread, and a static rule fails the build if a device read is
ever divided by the channel count again.

**Honest limit:** USB capture, the audio HAL and permissions still have to be exercised on real
hardware; nothing in this environment can stand in for your interface. What is verified here is
that the app builds the capture path with the right arithmetic, that every failure inside the
capture and playback threads is contained, and that every control on your list opens.

## Why the file is small

No third-party libraries at all — plain Java against the Android framework, built without Gradle.

| what | uncompressed |
|---|---|
| Dalvik bytecode (`classes.dex`, ~360 classes) | 373,040 B (364 KB) |
| Drawables (XML shapes and vectors) | 80,988 B (79 KB) |
| Brand artwork (logo PNG) | 72,442 B (71 KB) |
| Launcher icons (5 densities) | 54,498 B (53 KB) |
| Signature (v1/v2/v3) | 21,011 B (21 KB) |
| Resource table | 18,188 B (18 KB) |
| Manifest + USB device filter | 7,300 B (7 KB) |
| **total contained** | **627,467 B (613 KB)** deflated and 4 KiB-aligned → 589,179 bytes on disk |

## Install

1. Download `AUDIO-rec.apk` above.
2. Open it on the phone and allow "Install unknown apps" for your browser/file manager.
3. Launch **AUDIO-rec** — the Dashboard appears with the sidebar behind the ☰ button.
4. Plug in the USB interface (USB-C OTG adapter on phones) and accept the USB permission dialog.
5. Pick **Storage** in the sidebar to point recordings at an SD card or any folder you like.

## Known limits

* Android 10 and newer, USB host required, no root. 24-bit packed and 32-bit capture need
  Android 12+ (below that the platform only exposes 16-bit and float, and the app uses those).
* "All files access" is requested for recording straight onto an SD card; without it Android
  limits writing to the app's own folders — recording still works there.
* Multichannel monitoring is stereo by design: the first two outputs are used, as on the
  interfaces' own direct-monitor paths.
