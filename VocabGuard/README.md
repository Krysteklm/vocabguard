# VocabGuard

A friction layer for distracting apps: an AccessibilityService puts a short vocabulary quiz in
front of the apps you choose. Solve it to get a timed pass, or walk away.

## Features
- **Decks**: group words into decks, turn decks on or off for quizzes, edit or delete words.
- **Import**: paste a list or pick a TXT/CSV/TSV file (Anki and Quizlet exports work). The
  separator (dash, tab, semicolon, comma, equals sign, colon) is detected automatically, with a
  live preview, column swap and header skip.
- **Quiz types**: pick the translation, pick the word, type the translation (forgives one typo
  and missing accents), and true or false. Mix any of them in Settings.
- **Per-app rules**: choose any installed app, then set correct answers needed (1 to 5), pass
  length (1 to 30 min) and an optional daily unlock limit.

- **Display size**: 100% to 175% scaling for the whole app and the quiz, on top of the
  system font size.
- **Auto-add words (optional)**: a few new words a day from a built-in, offline German–English
  list graded A1 to B2, at the level your existing words suggest.

## Open & run
1. Open the `VocabGuard` folder in Android Studio and let it sync.
2. Run on a device (Android 8.0+, targets 15).
3. Settings tab: turn on the Accessibility service and "Display over other apps".
   On Android 13+ with a sideloaded APK, allow restricted settings in App info first.

## Structure
```
data/local        Entities (Deck, Word, GuardedApp), DAOs, AppDatabase (v2) + seeding
data/repository   WordRepository, QuizRepository, GuardedAppsRepository, SettingsRepository
domain            QuizMode/QuizQuestion, AnswerChecker, VocabImportParser
service           VocabAccessibilityService
lock              LockScreenActivity, LockScreenViewModel, LockScreenContent
main              MainActivity, 3 ViewModels, screens (Status, Words, Import, Apps, Settings)
ui                Theme and shared components
```

## Installing updates without the hassle
1. Create one signing key, once:
   `keytool -genkeypair -v -keystore vocabguard.jks -alias vocabguard -keyalg RSA -keysize 2048 -validity 10000`
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in. Every build is
   now signed with the same key, so new APKs install as updates.
3. Building on GitHub instead: add repository secrets `KEYSTORE_BASE64`
   (the .jks file in base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Every push to
   `main` (or "Run workflow" in Actions) publishes a Release. Direct link to the newest APK:
   `https://github.com/<user>/<repo>/releases/latest/download/VocabGuard.apk`.
   Tip: add the repo URL to the Obtainium app on the phone to get one-tap updates.
4. Easiest of all: install over USB or Wi-Fi from Android Studio (Run) or with
   `adb install -r app-debug.apk`. No "unknown sources" prompt.

Keep the .jks and passwords safe: losing them means the next build can't update the app.

## Database note
Version 2 replaces the unreleased v1 test schema with a destructive rebuild. Any future
schema change needs a real `Migration` so user decks survive updates.

## Play Store note
Google Play treats non-accessibility uses of the Accessibility API as policy-sensitive: you need
the in-app disclosure (included) plus a Permissions Declaration and demo video when submitting.
