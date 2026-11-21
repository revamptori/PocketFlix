# PocketFlix Server

**[Download PocketFlix 1.3.2 (signed APK)](deliverables/PocketFlix-1.3.2.apk)** · Android 8.0 (API 26) or newer · no root or compilation required.

## Discover movies and anime

1. Install **PocketFlix** and open **Library → Browse release feeds**. Six Internet Archive RSS feeds are configured: Feature films, Anime, Silent films, Sci-fi & horror, Comedy films, and Animation & cartoons. The full catalogue opens by default. Typing filters the current page immediately. Type a title and tap **Search**, or leave it blank to browse the collection. Select a sort before searching. **Next** follows the archive’s continuation cursor until the source runs out of results; the 100-item page size is not a total limit. **Recent RSS only** switches to the shorter publisher-provided list. Anime catalogue search also includes videos tagged with the Anime subject. No feed URL, torrent client, account or server setup is required.
2. Select a feed and tap a release to read its torrent file list. **No video is checked by default.** Pick one episode, several files, or **All episodes** (Anime) or **Select all** for the videos in that release. Review the size and tap **Download**. By default, files are fetched directly from Internet Archive over HTTPS, without peers or aria2. Select **Use BitTorrent instead of direct HTTPS** only if you want the peer-based transfer. Files that the source restricts or has removed will not download. A pack can include alternate encodings as well as episodes. Availability depends on the feed, peers and web seeds; this is not a search of every movie ever released.
3. The phone downloads only the selected video files and indexes them on completion. Anime releases default to the **Anime collection**; you can change that on the confirmation screen or later in **Manage movie**. Use **Anime collection only** on the phone or **Anime** in the browser. One transfer runs at a time, independently of the streaming server. Cancel from the release screen or notification. Cancelling discards partial files.

RSS updates only fetch listings. Opening a stale feed or tapping **Refresh feed** checks it immediately; **Background updates** schedules metadata refreshes roughly every six hours on an unmetered network, subject to Android scheduling. Turning that option off does not disable manual browsing. Feed discovery needs internet; local library streaming still works without it. BitTorrent exposes your IP to peers and may upload pieces during an active download. Use content you have permission to share. No automatic post-download seeding is requested.

**Existing installations:** Install over 1.3.1 without uninstalling to preserve your library. This APK installs beside [PocketFlix 1.3](deliverables/PocketFlix-1.3.apk), [PocketFlix 1.2](deliverables/PocketFlix-1.2.apk), [PocketFlix 1.1.1](deliverables/PocketFlix-1.1.1.apk), [PocketFlix 1.0](deliverables/PocketFlix.apk) and [PocketFlix Feeds 1.1](deliverables/PocketFlix-Feeds.apk), rather than overwriting them, because the previous build's signing key is not available. Your original library stays intact. Re-select any local videos you want in the new app. Stop the old server before using the same port in the new app.

## Install and watch

1. Open `PocketFlix-1.3.2.apk` on the **host phone**. If Android prompts, allow **Install unknown apps** for the browser or file manager you opened it with. You can turn that permission off after installation.
2. Open **PocketFlix** and allow notifications so the server controls appear in the notification drawer. Connect the phone and viewer devices to the same trusted Wi-Fi.
3. Tap **Upload movies** and choose one or more local videos. Wait for **Library updated**. In **Library → Manage movie**, edit titles, descriptions and categories or remove entries. Removing a movie never deletes the original document.
4. Tap **Start server**, then **Copy website URL**. Open that exact `http://` URL on a viewer device. No viewer app is needed. If the address changes, copy the new one. Guest Wi-Fi with client isolation can prevent connections.
5. Choose a movie. The browser remembers progress for this library address on this browser profile. MP4 with H.264 video and AAC audio is a good first test; MKV, MOV and other containers can contain codecs the browser cannot decode. PocketFlix does not transcode.
6. Leave the dashboard to use the phone normally. Tap **Stop Server** in the notification or dashboard to stop sharing. Force-stop, reboot, power management and vendor task killers can interrupt hosting; reopen the app to restart if necessary.

**Access:** By default, devices on the accepted LAN can read the library. Stop the server to change the port or set a password in **Settings**. The wildcard IPv4 listener checks the actual local interface, private IPv4 destination, on-subnet private peer address and HTTP Host. It does not configure port forwarding. Never forward the port or use untrusted Wi-Fi: plain HTTP does not encrypt movies, passwords or session cookies. Restarting the server invalidates sessions.

**Storage:** The index and extracted thumbnails are local. The app normally retains read access to the selected documents; it copies a video into private app storage only if the provider cannot supply persistent seekable access. Moving an original or revoking access can require removing and re-adding the entry. Dashboard storage is total indexed media size, not app disk usage. Streaming devices counts distinct client IPs with active transfers or recent playback heartbeats.

## Build and verification

The delivered APK was built with Gradle 8.10.2 using `tools/offline.gradle` (ECJ → DX → Android SDK resource packager → alignment and APK signing). Its v2/v3 signatures and ZIP alignment were verified. **SHA-256:** `7832f3a8dc376c181b3816bbb1b2d4561477599fc08e1f36df71af72d702e991`.

For maintainers, the root Android Gradle Plugin project uses JDK 17 and Android SDK 34 (`./gradlew assembleDebug`). The application uses native Java Android views, SQLite, SAF, a `specialUse` foreground service and vendored NanoHTTPD 2.3.1. The feed edition also bundles the aria2 BitTorrent worker for ARM, ARM64, x86 and x86-64. The build fetches hash-pinned native artifacts via `tools/prepare-aria2.sh` (bash, curl and sha256sum). Attribution and license are included in the APK. `app.pocketflix.direct` is the side-by-side package ID of the distributed offline build.

| Check | Result |
|---|---|
| Torrent metadata/file index handling, empty selection, single episodes, packs, invalid paths | Passed (`tests/TorrentCheck.java`) |
| Live on-device torrent transfer and completed-file import | **Not verified**; no Android device available |
| Gradle packaging, manifest inspection, signature and alignment | Passed |
| HTTP 200/206/416, HEAD, MIME type, open-ended/suffix/bounded ranges and cleanup | Passed using `tests/HttpCheck.java` and the production media-response code over real sockets |
| 48 requests across eight workers | Passed |
| Play an MP4, seek forward and backward, reopen and resume in two Chromium pages | Passed using the shipped web client, a host-side fixture catalog and the production streaming response |
| Android installation, SAF import, two-device Wi-Fi access, Activity dismissal and notification stop | **Not verified**; no Android device or emulator was available |

For device acceptance, run steps 1–4 above with an MP4, play and seek from two Wi-Fi clients, dismiss the dashboard from Recents, seek again on both clients, then stop from the notification and confirm a fresh request to the website no longer connects. Buffered video may play briefly after stopping.
