# Notepad for Android

A simple text editor for Android that works like Windows Notepad. It opens, edits and saves `.txt` and `.md` files, including files stored in **Google Drive**.

## Features

- **File:** New, New Markdown file, Open, Recent files, Save, Save as, Exit. You get a "Save changes?" prompt before anything unsaved is lost.
- **Edit:** Undo, Redo, Cut, Copy, Paste, Delete, Find, Find next/previous, Replace, Replace all, Go to line, Select all, Time/Date.
- **View:** Zoom in/out, Word wrap, Status bar, Monospace font, Markdown preview.
- **Status bar:** Line and column, character count, zoom, line endings (CRLF / LF / CR) and encoding (UTF-8, UTF-8 with BOM, UTF-16, ANSI). Tap the line endings or encoding to change them.
- Keeps each file's original encoding and line endings when you save, so files from Windows stay Windows-friendly.
- Keyboard shortcuts work the same as in Notepad when you use a hardware keyboard (Ctrl+S, Ctrl+F, F3, F5, …).
- If Android closes the app in the background, your unsaved text is kept and comes back the next time you open it.
- Follows your phone's light/dark mode.

## How Google Drive works

The app uses Android's built-in file picker. No Google sign-in or setup is needed inside the app.

- **Open…** shows the picker. Tap the ☰ menu and choose **Drive**, then pick a file.
- **Save** writes straight back to the same Drive file.
- **Save as…** lets you pick a Drive folder and a file name.
- In the Google Drive app you can also use **⋮ → Open with → Notepad** on a text or Markdown file.

The Google Drive app must be installed and signed in on the phone. Files you open from Drive need an internet connection unless they are available offline.

## Install on your phone

1. Open the repository's **Actions** tab on GitHub and select the latest **Build APK** run that has a green check.
2. Under **Artifacts**, download **notepad-debug-apk**. It downloads as a zip file. Unzip it to get `app-debug.apk`.
3. Copy `app-debug.apk` to your phone, or download it on the phone directly, and tap it to install. Android will ask you to allow installs from that app (for example Files or Chrome). Allow it once.

This is a debug build, signed with a development key. To publish on Google Play you would need a release build signed with your own key.

## Build it yourself

You need Android Studio, or JDK 17 plus the Android SDK.

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```
