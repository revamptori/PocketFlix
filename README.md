# PocketFlix Server

**[Download the signed Android APK](deliverables/PocketFlix.apk)** · Android 8.0 (API 26) or newer · no root or compilation required.

## Install and watch

1. Open `PocketFlix.apk` on the **host phone**. If Android prompts, allow **Install unknown apps** for the browser or file manager you opened it with. You can turn that permission off after installation.
2. Open **PocketFlix** and allow notifications so the server controls appear in the notification drawer. Connect the phone and viewer devices to the same trusted Wi-Fi.
3. Tap **Upload movies** and choose one or more local videos. Wait for **Library updated**. In **Library → Manage movie**, edit titles, descriptions and categories or remove entries. Removing a movie never deletes the original document.
4. Tap **Start server**, then **Copy website URL**. Open that exact `http://` URL on a viewer device. No viewer app is needed. If the address changes, copy the new one. Guest Wi-Fi with client isolation can prevent connections.
5. Choose a movie. The browser remembers progress for this library address on this browser profile. MP4 with H.264 video and AAC audio is a good first test; MKV, MOV and other containers can contain codecs the browser cannot decode. PocketFlix does not transcode.
6. Leave the dashboard to use the phone normally. Tap **Stop Server** in the notification or dashboard to stop sharing. Force-stop, reboot, power management and vendor task killers can interrupt hosting; reopen the app to restart if necessary.

**Access:** By default, devices on the accepted LAN can read the library. Stop the server to change the port or set a password in **Settings**. The wildcard IPv4 listener checks the actual local interface, private IPv4 destination, on-subnet private peer address and HTTP Host. It does not configure port forwarding. Never forward the port or use untrusted Wi-Fi: plain HTTP does not encrypt movies, passwords or session cookies. Restarting the server invalidates sessions.

**Storage:** The index and extracted thumbnails are local. The app normally retains read access to the selected documents; it copies a video into private app storage only if the provider cannot supply persistent seekable access. Moving an original or revoking access can require removing and re-adding the entry. Dashboard storage is total indexed media size, not app disk usage. Streaming devices counts distinct client IPs with active transfers or recent playback heartbeats.

## Build and verification

The delivered APK was built with Gradle 8.10.2 using `tools/offline.gradle` (ECJ → DX → Android SDK resource packager → alignment and APK signing). Its v2/v3 signatures and ZIP alignment were verified. **SHA-256:** `c350220f11c1d6841ebdb78e9df5c86595e0b24e84f2c5f24d9f1273e3865b77`.

For maintainers, the root Android Gradle Plugin project uses JDK 17 and Android SDK 34 (`./gradlew assembleDebug`). The application uses native Java Android views, SQLite, SAF, a `specialUse` foreground service and vendored NanoHTTPD 2.3.1. There are no remote assets or runtime cloud services.

| Check | Result |
|---|---|
| Gradle packaging, manifest inspection, signature and alignment | Passed |
| HTTP 200/206/416, HEAD, MIME type, open-ended/suffix/bounded ranges and cleanup | Passed using `tests/HttpCheck.java` and the production media-response code over real sockets |
| 48 requests across eight workers | Passed |
| Play an MP4, seek forward and backward, reopen and resume in two Chromium pages | Passed using the shipped web client, a host-side fixture catalog and the production streaming response |
| Android installation, SAF import, two-device Wi-Fi access, Activity dismissal and notification stop | **Not verified**; no Android device or emulator was available |

For device acceptance, run steps 1–4 above with an MP4, play and seek from two Wi-Fi clients, dismiss the dashboard from Recents, seek again on both clients, then stop from the notification and confirm a fresh request to the website no longer connects. Buffered video may play briefly after stopping.
