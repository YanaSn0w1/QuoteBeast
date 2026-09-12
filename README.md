# Quote Beast

Android app that writes a short boost reply from copied text using Groq.

## Install

1. Open [Releases](https://github.com/YanaSn0w1/QuoteBeast/releases)
2. Download `app-debug.apk`
3. On your phone, open the file and tap Install
4. Allow install from that app if Android asks

This is a debug APK, not a Play Store app.

## Setup

1. Open Quote Beast
2. Settings
3. Paste your own Groq API key
4. Save settings

Get a key at https://console.groq.com

Do not put your key in this GitHub repo.

## Use

1. Copy an X post
2. Tap **Generate from clipboard**
3. Go back to X → Reply → Paste

Optional floating button:

1. Tap **Show floating button**
2. Allow **Display over other apps**
3. Copy a post on any screen
4. Tap **QB**
5. Paste the result

If you tap again without copying something new, it uses the prompt only.

## Settings

- **Prompt** — default is one short sentence, max 11 words
- **Model** — default `qwen/qwen3.6-27b`
- **Temperature** — default `0.7`

## Source

- `MainActivity.kt` — screen, settings, Groq call
- `OverlayService.kt` — floating QB button
- `AndroidManifest.xml` — permissions
