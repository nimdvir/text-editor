# Notepad for Android

A simple text editor for Android that works like Windows Notepad. It opens, edits and saves `.txt` and `.md` files, including files stored in **Google Drive**.

## Features

- **File:** New text / Markdown / CSV file, Open, Save, Save as, **Auto save**, Exit. You get a "Save changes?" prompt before anything unsaved is lost.
- **Edit:** Undo, Redo, Cut, Copy, Paste, Delete, Find, Find next/previous, Replace, Replace all, Go to line, Select all, Time/Date.
- **View:** Zoom in/out, Word wrap, Status bar, Monospace font, and **Theme** (System, Light or Dark).
- **Status bar:** Line and column, character count, zoom, line endings (CRLF / LF / CR) and encoding (UTF-8, UTF-8 with BOM, UTF-16, ANSI). Tap the line endings or encoding to change them. With auto save on, it also shows when the file was last saved.
- Keeps each file's original encoding and line endings when you save, so files from Windows stay Windows-friendly.
- Keyboard shortcuts work the same as in Notepad when you use a hardware keyboard (Ctrl+S, Ctrl+F, F3, F5, …).
- If Android closes the app in the background, your unsaved text is kept and comes back the next time you open it.

### Markdown (.md)

- The app opens on a new Markdown file. A new file you haven't typed in never asks to be saved.
- A button on the right of the menu bar switches views: **✎ Edit → ◫ Split → 👁 Preview**. Each tap moves to the next.
- **Styled editor:** while you type, headings appear bigger, `**bold**` appears bold, and the Markdown symbols are dimmed.
- **Split view:** the text and the formatted view sit together, top/bottom in portrait and side by side in landscape. They scroll together (View → Sync scroll). View → Split layout lets you choose top/bottom, side by side, or automatic (by how you hold the phone). Switching views keeps your place.
- **Edit in the formatted view:** tap a paragraph, heading or list item to edit it in place. While you edit, that block shows its Markdown symbols. Tap **Done** or anywhere else to finish. Tap a checkbox to tick a task.
- **Toolbar** above the keyboard (View → Markdown toolbar): headings, bold, italic, strikethrough, code, code block, bullet / numbered / task lists, check off a task, quote, link, image, table, horizontal line, indent, outdent. Each button toggles, so tapping it again removes the formatting. With no selection, bold/italic/strike/code applies to the whole line when the cursor is at the start or end of the line, and to the word when the cursor is inside one. A selection across several lines is formatted line by line.
- **While typing:** the title row and status bar hide to make room for the keyboard, and the line you're typing on stays visible.
- **Smart Enter:** in a list, Enter starts the next bullet, number or task. Enter on an empty item ends the list.

### CSV (.csv, .tsv)

- Opens in **Table** view. The view button switches between ▦ Table and ✎ Text.
- Shows a header row and row numbers. The delimiter (comma, semicolon, tab or pipe) is detected automatically. View → First row is header turns the header row on or off.
- **Sort:** tap a column header to sort ascending, then descending, then back to the original order. Sorting only changes what you see. To keep the new order, choose **Save order** on the banner (or Edit → Save rows in this order).
- **Edit:**
  - Tap a cell to change it.
  - Tap or long-press a row number to insert or delete rows.
  - Long-press a column header to rename, insert or delete columns.
- Cells you don't touch keep their exact formatting, including quotes.

### Favorites & history

Open it with the ☰ button or File → Favorites & history.

- **Favorite files:** tap ★ next to the file name. Works for Google Drive files too.
- **Favorite folders:** add a folder once, then browse it, open files from it, or save the current file into it. Folders on your phone work. Google Drive may not offer whole-folder access, depending on your Drive app.
- **History:** the last 50 files you opened or saved, with when they were opened and edited. You can search, star or remove entries.

## How Google Drive works

The app uses Android's built-in file picker. No Google sign-in or setup is needed inside the app.

- **Open…** shows the picker. Tap the ☰ menu and choose **Drive**, then pick a file.
- **Save** writes straight back to the same Drive file.
- **Save as…** lets you pick a Drive folder and a file name.
- In the Google Drive app, use **⋮ → Open with → Notepad** on a text, Markdown or CSV file. Save writes back to Drive if Drive allows editing from other apps. If it doesn't, the app asks where to save a copy.

The Google Drive app must be installed and signed in on the phone. Files you open from Drive need an internet connection unless they are available offline.

## Install on your phone

1. Open the repository's **Actions** tab on GitHub and select the latest **Build APK** run that has a green check.
2. Under **Artifacts**, download **notepad-debug-apk**. It downloads as a zip file. Unzip it to get `app-debug.apk`.
3. Copy `app-debug.apk` to your phone, or download it on the phone directly, and tap it to install. Android will ask you to allow installs from that app (for example Files or Chrome). Allow it once.

**If Android says "App not installed":** uninstall Notepad, then install the new APK. This is needed only once, when moving from a build made before version 2.1. From 2.1 on, every build uses the same signing key, so new versions install over the old one and keep your settings, favorites and history.

This is a debug build, signed with a development key. To publish on Google Play you would need a release build signed with your own key.

## Build it yourself

You need Android Studio, or JDK 17 plus the Android SDK.

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```
