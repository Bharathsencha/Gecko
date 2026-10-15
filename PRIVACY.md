# Privacy Policy

Effective date: 15 October 2026

Gecko is an Android web browser. This policy explains what Gecko stores, what it sends over the network on its own, and what it never does. It covers the Gecko app only. Websites you visit have their own policies.

## The short version

- Gecko has no accounts, no analytics, no advertising, no crash reporting and no servers of its own.
- Everything Gecko keeps is stored on your phone. Nothing is uploaded to the developer.
- Android cloud backup is turned off for Gecko, so your browsing data is not copied into your Google backups.
- Private tabs keep their data in memory only and erase it when they close.

## What is stored on your phone

| Data | When it is kept | How to remove it |
| :--- | :--- | :--- |
| Browsing history (page title, address, time) | Normal tabs only, never private tabs | Settings > Clear browsing data, the History page, or "Clear data when leaving" |
| Open tabs (address, title, zoom, desktop mode) | Normal tabs only, so they can be restored | Close the tabs, or clear browsing data |
| Cookies, site data and cache | Kept by the browser engine for normal tabs. Private tabs keep them in memory only | Settings > Clear browsing data |
| Bookmarks and favourites | Until you delete them | Bookmarks page, or Edit on the start page |
| Saved passwords | Only when you choose to save one | Settings > Saved passwords |
| Settings, per-site settings, start-page designs | Until changed or deleted | Settings, or uninstall the app |
| App PIN | Only a keyed check value, never the PIN itself | Change it in Settings, or uninstall the app |
| Downloaded files | In your phone's Downloads folder | Delete them from the Downloads page or your file manager |

Saved passwords are encrypted with AES-256-GCM. The key is created and held in the phone's secure hardware (Android Keystore) and cannot be exported, so the encrypted data is useless off the device. Opening the passwords page needs your fingerprint, your phone's screen lock or the app PIN.

Uninstalling Gecko deletes all of the above except downloaded files.

## What Gecko sends over the network on its own

Besides loading the pages you open, Gecko makes these requests:

| Purpose | Service contacted | What it learns |
| :--- | :--- | :--- |
| Ad and tracker blocking lists (uBlock Origin) | The filter list hosts uBlock Origin uses, for example EasyList | That a uBlock Origin list was downloaded, and your IP address |
| Extra blocking rules, checked at most once a day | raw.githubusercontent.com (this project's repository) | Your IP address |
| Tracking protection lists of the browser engine | Mozilla | Your IP address |
| Keeping the video downloader working, checked at most once a day | GitHub (yt-dlp releases) | Your IP address |
| Searches typed in the address bar | The search engine you chose in Settings (Google by default) | Your search, as with any browser |
| Site icons in history, bookmarks, tabs and favourites | The site itself (`/favicon.ico`) | That you have that site saved |
| Video and audio downloads | The site the media comes from, only after you open the download sheet | That you requested the media |

### VPN

The VPN is off until you turn it on.

- **Tor:** traffic goes through the Tor network. To show your exit address, Gecko asks check.torproject.org through Tor, then looks up that exit address's country at ipinfo.io. Only the public Tor exit address is sent, never yours.
- **Free relay:** Gecko downloads public relay lists from api.proxyscrape.com and from the proxifly list on GitHub, then tests relays by requesting ipinfo.io through them. Free relays are run by unknown third parties. They can see the sites you connect to, and the content of any page that is not HTTPS. Use Tor for anything sensitive.

When the VPN is on, downloads and the video downloader use the same route as pages.

## What Gecko does not do

- No analytics, telemetry or usage statistics. The browser engine's telemetry is switched off.
- No crash reports.
- No advertising and no advertising identifiers.
- No accounts, sync or cloud storage.
- No sale or sharing of data. The developer never receives your data.

## Permissions

| Permission | Why |
| :--- | :--- |
| Internet, network state | To load pages |
| Notifications | To show download progress and media controls |
| Foreground service (media playback, data sync), wake lock | To keep audio playing and downloads running when the app is in the background |

Gecko does not ask for location, contacts, camera, microphone or storage permissions. If a website asks for something like location or camera, the browser engine handles it, and it is off unless you allow it.

## Children

Gecko is a general web browser. It does not knowingly collect anything from anyone, including children.

## Changes

Changes to this policy are published in this file in the project repository, with a new effective date.

## Contact

Open an issue at https://github.com/Bharathsencha/Gecko/issues.
