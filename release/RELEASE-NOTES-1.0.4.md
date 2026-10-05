# AUDIO-rec 1.0.4

**USB Audio Recording & Production Workstation** — an offline, installable Android app for
musicians, podcasters and recording engineers.

**Author:** Mostakim Billah · **Package:** `com.mostakim.audiorec` · **Requires:** Android 10 (API 29) or newer · **No root**

## Download

**[⬇ AUDIO-rec.apk](https://github.com/Mostakim-Git/AUDIO-Rec/releases/download/v1.0.4/AUDIO-rec.apk)** —
593,276 bytes (579 KiB), version 1.0.4, signed (v1 + v2 + v3)<br>
SHA-256 `3387cde60531e2b5e635c87b423ae8fd74b6ea24b501b2bdc9cb60945c55db15`

Confirm it yourself with `sha256sum AUDIO-rec.apk`. A second copy lives in the repository at
[`release/AUDIO-rec.apk`](https://github.com/Mostakim-Git/AUDIO-Rec/blob/v1.0.4/release/AUDIO-rec.apk).

This is the update asked for after 1.0.3. Everything in it comes from the operator's own list, and
each item names where it lives and how it is checked. 1.0.2's force-stop fix and 1.0.3's storage
gauge fix are unchanged and still in.

## What is new in 1.0.4

* **It is a recording app, not a website.** The dashboard and the sidebar are gone. The app opens on
  the recorder itself — one status header across the top, the page filling the window, and a
  five-tab bar along the bottom (Record · Mixer · Library · Playlist · More). There is no drawer to
  open, no side rail eating the width a phone does not have, and nothing lands on a dashboard
  first. `MainActivity` builds the shell; the recorder page is the launch page and the target of the
  back button.

* **Every screen shape, portrait included.** Pages are built from full-width blocks and weighted
  rows instead of fixed pixel widths, and the activity runs `fullSensor` with
  `configChanges="orientation|screenSize|screenLayout"`, so a 20:9 phone in portrait, the same phone
  in landscape, a 16:9 tablet and a folding cover screen all lay out the same way — nothing hangs off
  the side, nothing is squeezed to nothing. The headless UI harness measures and lays out all eleven
  pages at nine window shapes, portrait and landscape, and fails the build if a label loses its room
  to draw or the tab bar does not fit.

* **Export and share now produce a file you can actually get.** A finished take or export was only
  ever written into the app's own folder, which no file manager, Drive, WhatsApp or USB transfer can
  see — the app played its copy happily while the file the operator expected was nowhere reachable.
  Every export is now published a second time through MediaStore into **`Download/AUDIO-rec/`**,
  visible to every app on the phone and over USB, with the pending flag cleared so it appears
  immediately; the share intent carries the URI in its `ClipData` as well as its flags, so the
  per-URI read grant actually reaches the receiving app instead of arriving as "no file".
  (`share/Downloads`, `share/ExportProvider`, `ui/Dialogs.shareUri`, `audio/Exporter`.)

* **Gain and monitor move in exact 0.1 dB steps.** The faders snap to a 0.1 dB step in both
  directions, a drag starts from wherever the finger landed (the handle never jumps), and a touch
  held still for a moment turns into a fine adjustment at one eighth of the travel, so a tenth of a
  decibel is reachable with a fingertip. Each fader also carries **−0.1 / +0.1** buttons, because
  0.1 dB is far below one pixel of fader travel on a phone. The step arithmetic lives in
  `ui/kit/SliderMath`, free of the framework, and is covered by 194 off-device checks.

* **The spectrum is real time.** A 2048-point FFT analyser (Hann window, 48 bars, 40 Hz – 20 kHz)
  with falling peak caps runs live from the moment the app opens — fed from the interface while
  monitoring or recording, and from playback while auditioning a take — with tap-to-clear peaks and
  a Freeze button. The harness feeds a 1 kHz tone and requires it to land on the 1 kHz bar, a 300 Hz
  tone to move the peak, and silence to drop the bars again.

* **The gain fader opens on the value the engine is using.** A stored input gain above +12 dB used
  to be clamped by the strip's default range before the real range was applied, so the fader showed
  a number the engine was not using; the range is now applied before the stored value.

## The operator's list, item by item

| report | what 1.0.4 does | checked by |
|---|---|---|
| "feels like a website, not proper alignment" | dashboard + sidebar replaced by the recording console; full-width blocks and weighted rows everywhere | UI harness, 9 window shapes, overflow/squeeze rules |
| "isn't working in vertical screen" | `fullSensor` + orientation config changes; portrait is a first-class shape | UI harness lays out every page in portrait |
| "sharing and export — never got the file" | exports published to `Download/AUDIO-rec` through MediaStore; share URI travels in the ClipData so the grant reaches the target | UI harness drives the real `Downloads.saveNow` and `Dialogs.shareFile` paths |
| "remove the dashboard layout with sidebar navigation" | gone; bottom tab bar, recorder first | UI harness counts the five tabs on every shape |
| "make a proper recording type interface" | transport, INPUT/OUTPUT meters, scope + analyser, format, destination on one page | feature checklist on the Recorder page |
| "gain and monitor sliders should do −0.1 / +0.1" | 0.1 dB snap, ±0.1 nudge buttons, fine-drag mode, no jump on touch-down | 194 slider checks + the harness presses the buttons |
| "spectrums will be real-time" | live FFT analyser during capture and playback, falling peak caps | harness feeds tones and silence |

## Verification

Everything runs off-device — no phone, no emulator, no Android Studio:

```bash
bash tools/build.sh --release      # APK into release/, signature verified
bash tools/test/run.sh             # every layer below
```

| what | result |
|---|---|
| Container round-trips + **device-read arithmetic** (the force-stop) + **0.1 dB slider arithmetic** | 1,011 in-process checks pass |
| Independent container scorer (re-parses every artifact) | 291 checks pass |
| Foreign-file reader corpus (RF64, AIFF-C, 8-bit, float, truncated, garbage) | 108 checks pass |
| 48 kHz Opus resampler vs a single-pass reference, 12 rates | 54 checks pass |
| Sharing rules for the Drive/WhatsApp content provider | 38 checks pass |
| Headless UI harness: shell, all 11 pages, 9 shapes, choosers, **real-time spectrum, 0.1 dB faders, export/share** | 199 checks pass |
| Static rules (format strings, extras, view tree, byte/sample mistakes) + mutation test | 6/6 defect shapes caught |
| Portable zip inspection of the signed APK, including the shipped dex declaring the byte-aware frame arithmetic | 25 checks pass |
| Negative control: the inspection rejects a package whose dex lost that helper | 1 mutation caught |
| Signed-APK release gate (offline proof, no MP3, components, branding, alignment) | 41 checks pass |
| Minimum-SDK audit against the Android 10 platform jar | every API-30+ symbol inlined or SDK-guarded |

**Honest limit:** USB capture, the audio HAL and permissions still have to be exercised on real
hardware; nothing in this environment can stand in for your interface. What is verified here is that
the app builds the capture path with the right arithmetic, that every failure inside the capture and
playback threads is contained, that every page lays out in portrait and landscape at every shape, and
that every control on your list does what it says.

## Why the file is small

No third-party libraries at all — plain Java against the Android framework, built without Gradle.

| what | uncompressed |
|---|---|
| Dalvik bytecode (`classes.dex`, 364 classes) | 376,872 B (368 KB) |
| Drawables (XML shapes and vectors) | 82,980 B (81 KB) |
| Launcher icons (5 densities) | 56,882 B (56 KB) |
| Brand artwork (logo PNG) | 72,442 B (71 KB) |
| Signature (v1/v2/v3) | 21,009 B (21 KB) |
| Resource table | 18,188 B (18 KB) |
| Manifest + USB device filter | 7,300 B (7 KB) |
| **total contained** | **631,297 B (617 KB)** deflated and 4 KiB-aligned → 593,276 bytes on disk |

## Install

1. Download `AUDIO-rec.apk` above.
2. Open it on the phone and allow "Install unknown apps" for your browser/file manager.
3. Launch **AUDIO-rec** — the Recorder page opens, ready to arm.
4. Plug in the USB interface (USB-C OTG adapter on phones) and accept the USB permission dialog.
5. Point recordings at an SD card or any folder from **Recorder → Change folder** or the Storage page.

## Known limits

* Android 10 and newer, USB host required, no root. 24-bit packed and 32-bit capture need
  Android 12; below that the app records through the float path at the same resolution.
* Hardware gain, pad, phantom power and direct-monitor knobs live on the interface — USB audio
  class has no standard control for them, so the app's trims are digital and applied before the
  meters and the file.
* Monitoring and playback use the first two outputs of the selected device.
* Opus (OGG) runs at 48 kHz stereo; WAV/FLAC/AIFF keep the interface's native rate.
