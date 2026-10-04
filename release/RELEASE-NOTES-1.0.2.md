# AUDIO-rec 1.0.2

**USB Audio Recording & Production Workstation** — an offline, installable Android app for
musicians, podcasters and recording engineers.

**Author:** Mostakim Billah · **Package:** `com.mostakim.audiorec` · **Requires:** Android 10 (API 29) or newer · **No root**

## Download

**[⬇ AUDIO-rec.apk](https://github.com/Mostakim-Git/AUDIO-Rec/releases/download/v1.0.2/AUDIO-rec.apk)** —
589,179 bytes (575 KiB), version 1.0.2, signed (v1 + v2 + v3)<br>
SHA-256 `f938e9a9f28e690fa19acebbfb10010df8069cca48c08422c2f2e65724444426`

GitHub stores that digest for this asset, so the download is byte-for-byte the file that was
built, checked and signed here — and so you can confirm it yourself with
`sha256sum AUDIO-rec.apk`. A second copy lives in the repository at
[`release/AUDIO-rec.apk`](https://github.com/Mostakim-Git/AUDIO-Rec/blob/v1.0.2/release/AUDIO-rec.apk).

## The force-stop when recording starts — fixed

Tapping record killed the app on any device whose input is not float. The capture loop read a
block of audio and then divided the returned count by the channel count to get the number of
frames:

```java
read = mRecord.read(byteBuf, 0, byteBuf.length, AudioRecord.READ_BLOCKING);
int framesRead = read / channels;        // 1.0.x
```

`AudioRecord.read()` returns a **byte** count for every PCM encoding (16-, 24- and 32-bit) and a
*sample* count only for float. For 16-bit stereo a 1024-frame read is 4096 bytes, so `read / 2`
gave 2048 frames where there were 1024 — the block that had just been read was then indexed at
twice its length by the trim, the meters and the monitor fold-back. The result was an
`ArrayIndexOutOfBoundsException` on the capture thread, and Android kills the whole process when
a thread dies uncaught: *"AUDIO-rec keeps stopping"*, every time, right after the file was
created.

The frame arithmetic now goes through one tested place (`Pcm.framesFromBytes`), the decode
reports how many samples it produced, and a block can never index past the buffer that holds it.

Also fixed in the same pass, so the recording path cannot be taken down by anything else:

* **A listener can no longer kill the app.** Meter, scope, tick, state and error events are
  delivered to every listener through a guard; a listener that throws is a logged warning
  instead of a dead capture thread. The screens are UI code — one released view was enough.
* **The capture and playback threads are wrapped.** Whatever happens inside them — a device
  that disappears, a writer that fails, a bug of ours — the take is closed properly, the reason
  is reported, and the process stays alive.
* **MP3 can no longer get in.** The system file picker offers `audio/*`, and the library used to
  import whatever was picked. It now refuses anything outside WAV / AIFF / FLAC / OGG by name
  *and* by content, and the playback engine refuses to decode a file that is not one of those
  four — even if an older library row points at one.

## The feature list, item by item

Every item on your list is in this build, and the headless UI harness now checks them where you
meet them: it opens each chooser and reads what it offers. `tools/test/ui/run.sh` prints the
checklist.

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
| Available disk space | Storage gauge + free-space figures, Recorder destination card | "used" figure on Storage |
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
| Headless UI harness: shell, all 12 pages, choosers, **the feature checklist** | 148 checks pass |
| Static rules (format strings, extras, view tree, byte/sample mistakes) + mutation test | 6/6 defect shapes caught |
| Portable zip inspection of the signed APK, including the shipped dex declaring
  the byte-aware frame arithmetic | 25 checks pass |
| Negative control: the inspection rejects a package whose dex lost that helper | 1 mutation caught |
| Signed-APK release gate (offline proof, no MP3, components, branding, alignment) | 39 checks pass |
| Minimum-SDK audit against the Android 10 platform jar | every API-30+ symbol inlined or SDK-guarded |

The read arithmetic is now covered by 684 assertions across the three byte widths (16-, 24- and
32-bit), one to eight channels and partial reads — including the exact 4096-byte 16-bit stereo case that used to kill
the capture thread — and a static rule fails the build if a device read is ever divided by the
channel count again.

**Honest limit:** USB capture, the audio HAL and permissions still have to be exercised on real
hardware; nothing in this environment can stand in for your interface. What is verified here is
that the app builds the capture path with the right arithmetic, that every failure inside the
capture and playback threads is contained, and that every control on your list opens.

## Why the file is small

No third-party libraries at all — plain Java against the Android framework, built without Gradle.

| what | uncompressed |
|---|---|
| Dalvik bytecode (`classes.dex`, ~360 classes) | 364 KB |
| Drawables (XML shapes and vectors) | 79 KB |
| Brand artwork (logo PNG) | 71 KB |
| Launcher icons (5 densities) | 53 KB |
| Signature (v1/v2/v3) | 21 KB |
| Resource table | 18 KB |
| Manifest + USB device filter | 7 KB |
| **total contained** | **612 KB** deflated to ~543 KB, padded to 4 KB alignment → 589,179 bytes on disk |

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
