# Ultra Keyboard

An Android keyboard (IME) with the classic 3×4 layout from old phones
(multi-tap), with full TalkBack support, Serbian Latin and Cyrillic script,
symbols and emoji.

## How to open the project

1. Download/unzip this folder.
2. Open **Android Studio** → *Open* → select the `UltraKeyboard` folder.
3. On first open, Android Studio will ask to download the Gradle
   distribution (defined in `gradle/wrapper/gradle-wrapper.properties`) —
   that's normal, wait for the sync to finish.
4. Connect a phone (or start an emulator) and click **Run**.

## How to enable the keyboard on your phone

1. Launch the installed "Ultra Keyboard" app — a screen with instructions
   and an **"Open keyboard settings"** button will open.
2. In Settings → System → Languages & input → Keyboards → Manage
   keyboards, enable "Ultra Keyboard".
3. In any text field, long-press the space bar (or the globe icon) and
   choose "Ultra Keyboard" as the active keyboard.

## How input works

- **Keys 2–9**: press repeatedly in a row to cycle the letter (e.g. 2,2,2 =
  c). Wait a moment (0.9s) and the letter confirms itself — the next press
  on the same key starts a new letter.
- **Key 1**: punctuation (. , ? ! ' ")
- **Key 0**: space (no long press needed, first press is a space)
- **Key \***: short press = quick symbols (- : ; @ / *); **long press**
  opens the full symbol panel.
- **Key #**: short press = new line (Enter); **long press** opens the
  emoji panel.
- **⇧ (Shift)**: one press = next letter uppercase; double press (quickly
  twice) = locked uppercase (Caps Lock); press again = off.
- **LAT/CYR**: switches Latin ↔ Cyrillic script.
- **⌫**: deletes.

## Accessibility (TalkBack)

Every key is a real `Button` (not a canvas drawing), so TalkBack
automatically supports: focus by touch, reading descriptions
(`contentDescription`), and swipe navigation. Since TalkBack's system rule
requires a **double tap** to activate any button, the multi-tap cycle works
by double-tapping the same key repeatedly (each double-tap = one "press" of
that number, just like on an old phone).

Every time a letter changes or is entered, the app **announces it aloud**
(`announceForAccessibility`), so the user hears the current letter without
needing to lift a finger and explore the screen.

## Localization / multi-language support

All user-facing text — menus, instructions, and everything TalkBack/JAWS
reads aloud while typing — lives in Android string resources, not
hardcoded in the Kotlin code. Serbian is the default (`res/values/strings.xml`);
English lives in `res/values-en/strings.xml`. Android automatically picks
the matching language based on the phone's system language.

To add another language later: create a new `res/values-xx/strings.xml`
(where `xx` is the language code) with the exact same string names as
`res/values/strings.xml`, translated. No code changes needed. The T9 letter
layout itself (`KeyMaps.kt`) stays Serbian (Latin/Cyrillic) regardless of
interface language, unless a language-specific letter layout is added there
too.

## Sending a test log (without adb)

The app now writes a log to a file on the phone while the keyboard is used.
When someone is testing and runs into a problem:

1. Open the "Ultra Keyboard" app (the icon on the phone).
2. Tap **"Send log (for testing)"** — a share menu opens (WhatsApp, Email,
   etc.) with the log file ready to send.
3. Optionally, tap **"Clear log"** before a new test so the log isn't full
   of old attempts.

## Signed (release) build — for GitHub / Play Store

The project now contains your personal Ultra signing key:
- `keystore/ultra-release-key.jks` — the key itself
- `keystore/PASSWORD_SACUVAJ_OVO.txt` — the password (same for store and
  key)
- `keystore.properties` — the file Gradle reads to automatically sign the
  release build

**CRITICAL**: this key is forever the identity of the "Ultra" apps on the
Play Store. If you lose it, **you will never be able to publish an update**
for the same app under the same name again — you'd have to create a
completely new app from scratch. So:

1. Immediately back up `keystore/ultra-release-key.jks` and the password
   from `PASSWORD_SACUVAJ_OVO.txt` SOMEWHERE OUTSIDE this folder (e.g. a
   password-protected cloud folder, a USB drive you won't lose, a password
   manager).
2. We'll use the same key for ALL future "Ultra" apps (Creative Suite, AI
   Camera...) — one key, one brand.
3. `keystore.properties` and the `keystore/` folder itself are
   intentionally in `.gitignore` — **they never go to GitHub**. Only the
   source code goes there.

To build a signed APK:
```
gradlew.bat assembleRelease
```
The file will appear at `app\build\outputs\apk\release\app-release.apk` —
that's the version signed with your key, ready to share or for the next
steps toward the Play Store (which additionally requires an .aab file —
`gradlew.bat bundleRelease` when it's time for that).

## GitHub Actions — automatic signed APK build

The workflow (`.github/workflows/build-release.yml`) builds a signed APK on
GitHub's server on every push to `main`, and creates a real GitHub Release
(with the APK attached for download) whenever you push a tag starting with
`v` (e.g. `v1.0`).

Since `keystore/` and `keystore.properties` are **intentionally not** in the
repo (see `.gitignore`), the workflow assembles them itself from **GitHub
Secrets** on every build, uses them, and discards them — they never end up
in the code or the repo history.

### One-time setup (before the first push)

**1. Convert the keystore to base64 text** (in cmd, in the project folder):
```
powershell -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore\ultra-release-key.jks')) | Out-File -Encoding ascii keystore_base64.txt"
```
This creates a file `keystore_base64.txt` with a long string of text (no
spaces/newlines) — that's your keystore, just in text form suitable for
Secrets.

**2. Open the password**:
```
type keystore\PASSWORD_SACUVAJ_OVO.txt
```

**3. On GitHub**: open the repo → **Settings → Secrets and variables →
Actions → New repository secret**, and add four:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the entire content of `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | the password from `PASSWORD_SACUVAJ_OVO.txt` |
| `KEY_ALIAS` | `ultra_keyboard` |
| `KEY_PASSWORD` | same password as `KEYSTORE_PASSWORD` |

**4. Delete `keystore_base64.txt`** after copying it into GitHub (it was
only temporary, it contains the secret key in readable form - it shouldn't
stay on disk or accidentally get added to git).

After this, every push automatically builds an APK (visible under the
repo's **Actions** tab, the **Artifacts** button at the bottom of each
build), and pushing a version tag creates a real Release ready to share via
link.

## Known limitations / ideas for later

- Dž, Lj, Nj are typed as two separate letters (d+ž, l+j, n+j) — intentionally
  simplified for the first version.
- No word prediction (T9 dictionary) yet — agreed to add later.
- The letter layout per key is easy to change in `KeyMaps.kt`.
- The app icon is a simple placeholder (vector drawable) — replace it as you
  like with `app/src/main/res/drawable/ic_launcher.xml` or a proper mipmap
  set.
- Not tested on a real device from this conversation (no access to the
  Android SDK/emulator in this environment) — let me know what Android
  Studio reports on the first build and we'll sort it out together.
