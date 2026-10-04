> **Superseded by [1.0.3](https://github.com/Mostakim-Git/AUDIO-Rec/releases/tag/v1.0.3).**
> This build still force-stops when a recording starts: its capture loop divides a device read
> (a byte count) by the channel count, so the block it just read is indexed past its end and the
> capture thread dies. [1.0.2](https://github.com/Mostakim-Git/AUDIO-Rec/releases/tag/v1.0.2)
> fixes that. Download 1.0.3 instead.

# AUDIO-rec 1.0.1

**USB Audio Recording & Production Workstation** — an offline, installable Android app for
musicians, podcasters and recording engineers.

**Author:** Mostakim Billah · **Package:** `com.mostakim.audiorec` · **Requires:** Android 10 (API 29) or newer · **No root**

## Download

**[⬇ AUDIO-rec.apk](https://github.com/Mostakim-Git/AUDIO-Rec/releases/download/v1.0.1/AUDIO-rec.apk)** —
585,083 bytes (571 KiB), signed (v1 + v2 + v3)<br>
SHA-256 `9778262907a8a9498b12337a354ac70d5fe0e7b95eedb03ab864ffc25659a605`

GitHub stores that same SHA-256 for the asset on this page, and the repository copy is
at [`release/AUDIO-rec.apk`](https://github.com/Mostakim-Git/AUDIO-Rec/blob/v1.0.1/release/AUDIO-rec.apk).

## This release fixes the blank window

**1.0.0 opened onto an empty screen.** Not a slow start, not a theme problem: the window
never measured or laid out a single view. Four separate defects were behind it, and all four
are fixed here.

1. **The drawer shell never found its panes.** `SidebarLayout` picked up its sidebar and
   content views in `onFinishInflate()` — a callback Android only fires when a layout is
   *inflated from XML*. This app has no XML layouts: the whole interface is built in Java.
   The callback never ran, both pane references stayed `null`, and `onMeasure`/`onLayout`
   returned without drawing anything. The shell is now handed its panes explicitly
   (`setChildren(content, rail)`) and re-checks them on configuration changes.
2. **Building a page could crash between the shell and the screen.** The `Screen` base class
   ran each page's `build()` from its constructor, before the subclass's own fields were
   initialised — the first to notice was Playlist, which threw a `NullPointerException` while
   computing an empty queue. Pages now build lazily, on first use.
3. **Four blocks of interface were built, filled in, and then thrown away** — they were never
   attached to a parent, so they could not have appeared on any device:
   * the empty-state card on every list page ("Nothing recorded yet…"),
   * every row on **Export Files** (the page counted "2 exports" and listed none),
   * every card on **Device Presets**,
   * the whole **Library health** card on **Storage** (take counts, orphan scan, repair buttons).
4. **A long take name ate its row on Library**, squeezing the red "missing" badge to zero
   width. Titles now yield space (single line, ellipsised) and badges keep their size.

Alongside those: dialogs now use the app's own `AppTheme.Dialog` (wired through
`android:alertDialogTheme`, so every dialog matches the console instead of the platform
default), and the version is stamped from one file instead of a hard-coded manifest attribute.

## How this is verified now

The blank window was invisible to every check this project had, so two new layers were added
and wired into `bash tools/test/run.sh`:

* **A headless UI harness** (`tools/test/ui/`) that compiles the *real* UI sources against a
  small Android stand-in, constructs the real `MainActivity`, measures and lays out the shell
  and **all 12 pages** at phone and tablet size, walks the resulting view tree, and fires every
  click handler. It caught defect 2 as a crash and defects 3 and 4 as missing/zero-sized views,
  and now reports **95 checks passed**, 12/12 pages laid out, with the view and text counts of
  each page printed.
* **View-tree rules + a mutation test** (`tools/static_check.py`,
  `tools/test/static_check_test.py`): the checker flags a container that gets children but is
  never attached, a child laid out with zero size and no weight, and a page that fills nothing
  in; the mutation test re-introduces all five shapes of that bug into a scratch copy and proves
  each one is still reported (**5/5 caught**). A rule that cannot fail is not a rule.

| what | result |
|---|---|
| Headless UI harness: shell, all 12 pages, phone + tablet layout, click handlers | 95 checks pass |
| View-tree mutation test (the blank-interface bug shapes) | 5/5 caught |
| In-process container checks (WAV/FLAC/AIFF/Ogg, all rates and depths) | 133 checks pass |
| Foreign-file reader corpus (RF64, AIFF-C, 8-bit, float, truncated, garbage) | 108 checks pass |
| 48 kHz Opus resampler vs a single-pass reference, 12 rates | 54 checks pass |
| Sharing rules for the Drive/WhatsApp content provider | 38 checks pass |
| Independent container scorer | 291 checks pass |
| Signed-APK release gate (no network permission/code, no MP3 encoder, components, branding, alignment, signature) | 39 checks pass |
| Minimum-SDK audit against the Android 10 platform jar | every API-30+ symbol inlined or SDK-guarded |

**Honest limit:** this build environment has no emulator (no KVM, no network to fetch an SDK),
so the interface is verified by constructing, measuring and laying out the real Activity and
reading the resulting view tree — not by taking a screenshot. The one thing worth doing first is
installing this APK and opening it: the Dashboard should appear immediately, with the sidebar
behind the ☰ button.

## Why the file is small (and why that is a good thing)

The APK is complete — 97 entries, 340 app classes, all 12 screens, every encoder, every icon.
It is small because it contains **no third-party libraries at all**: no AndroidX, no Kotlin
runtime, no support libraries. The whole app is plain Java against the Android framework,
built without Gradle.

| what | uncompressed |
|---|---|
| Dalvik bytecode (`classes.dex`, 340 classes) | 359 KB |
| Drawables (XML shapes and vectors) | 79 KB |
| Brand artwork (logo PNG) | 71 KB |
| Launcher icons (5 densities) | 53 KB |
| Signature (v1/v2/v3) | 21 KB |
| Resource table | 18 KB |
| Manifest + USB device filter | 7 KB |
| **total contained** | **607 KB** deflated to 539 KB, padded to 4 KB alignment → 585,083 bytes on disk |

For comparison, an *empty* app created by Android Studio already weighs ~3.5 MB because of
AndroidX alone. Nothing is missing here: no native code is included, so the same file installs
on arm64, arm32 and x86 phones, and everything works with the network off forever.

## Install

1. Download `AUDIO-rec.apk` above.
2. Open it on the phone and allow "Install unknown apps" for your browser/file manager
   (the app is distributed directly, not through Play).
3. Launch **AUDIO-rec** — the Dashboard appears with the sidebar behind the ☰ button.
4. Plug in a USB audio interface and accept the USB permission dialog.
5. Pick **Storage** in the sidebar to point recordings at an SD card or any folder you like.

## What it does

* **USB audio recording** — UAC1/UAC2 isochronous capture from audio interfaces and USB
  DACs (Jcally JM6, M-Audio Duo/Fast Track/Fast Track Pro, PreSonus 22VSL/44VSL,
  Blue Snowball/Yeti/Yeti PRO, RME Babyface, Zoom H2/H4, generic HiFi DACs).
* **WAV / FLAC / AIFF / OGG-Opus** recording — no MP3 anywhere (patent-free: compressed
  takes use Opus at 48 kHz). WAV switches to RF64 automatically past 4 GB.
* **Mono, stereo and multichannel**, 16/24/32-bit per device, up to the device's maximum
  sample rate; stereo playback out of the first two outputs on multichannel interfaces.
* **Real-time level meters** with peak hold, tap to clear, plus a **Monitor** button for
  setting levels before recording.
* **Dashboard** with sidebar navigation and full CRUD for Sessions, Audio Tracks,
  Device Presets and Export Files; persistent SQLite storage.
* **Mixer** with internal gain, volume and mute when the device exposes them.
* **Recordings folder** anywhere you can write (external SD included), disk-space display,
  rename/delete, tape-style playlist, and **sharing to Drive/WhatsApp**.
* Buffer size 1024–16384 frames, input/output device selection, device presets.
* **Fully offline**: no login, no account, no password, no network permission at all.

## Known limits

* Installing and recording with your own interface is the one step that has to happen on
  real hardware — USB capture, permissions and the audio stack cannot be exercised in a
  container.
* "All files access" is requested when you want to record straight onto an SD card; without
  it Android limits writing to the app's own folders (recording still works there).
* 1.0.0 is superseded by this release: it renders no interface. Use 1.0.1.
