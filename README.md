<div align="center">

<p><img src="fastlane/metadata/android/en-US/images/icon.png" width="150"></p>
<h2><b>LibreTorrentEE</b></h2>
<h4>Copylefted libre full-featured torrent client for Android, with built-in anti-leech and BTN (BitTorrent Threat Network) support.</h4>


---

## 🚀 About this fork

**LibreTorrentEE** is a fork of [LibreTorrent](https://gitlab.com/proninyaroslav/libretorrent) that adds out-of-the-box **anti-leech protection** and **BTN (BitTorrent Threat Network)** integration. The goal is to protect your seeding ratio and bandwidth against vampires, progress-cheaters and other malicious peers, with **zero external setup** — everything runs inside the app on the device.

Compared to upstream LibreTorrent, this edition adds:

* 🛡️ **[PeerBanHelper](https://github.com/PBH-BTN/PeerBanHelper)-compatible anti-leech engine** (in-process, no external server needed)
* 🌐 **[BTN](https://github.com/PBH-BTN/BTN-Spec) support** — share ban history and swarm data with the BTN community

See the [Enhanced features](#-enhanced-features) section below for details.

## Screenshots

<div align="center">

[<img src="art/screenshots/phone.png" width=160>](art/screenshots/phone.png)
[<img src="art/screenshots/phone_dark.png" width=160>](art/screenshots/phone_dark.png)
[<img src="art/screenshots/create_torrent.png" width=160>](art/screenshots/create_torrent.png)
[<img src="art/screenshots/rss.png" width=160>](art/screenshots/rss.png)
[<img src="art/screenshots/tablet.png" width=480>](art/screenshots/tablet.png)

</div>

## 📋 Features

**[Note for Android 12+]**: Google Play version doesn't have permission to access all files, use another version (F-Droid or direct APK).

* BitTorrent 2.0 and WebTorrent support
* Select which files to download
* Move files while downloading
* Auto-move downloaded files to another folder or external drive
* Stream files, with sequential downloads
* Android TV
* Material design, dark and black theme, and tablet UI
* Customisable network, battery, and UI settings, etc.
* 35+ translations
* Scheduling
* Auto-downloading, with Atom/RSS manager
* Create torrents, with many and big files
* HTTP\S and magnet links
* DHT, PeX, encryption, LSD, UPnP, NAT*PMP, µTP
* IP filtering (eMule dat and PeerGuardian)
* Supports proxy for trackers and peers
* Based on [libtorrent4j](https://github.com/aldenml/libtorrent4j)
* And more

## ✨ Enhanced features

### 🛡️ PeerBanHelper-compatible anti-leech engine

A built-in, always-on detection engine that works **out of the box** — no external server or web API needed. It runs inside the app and inspects every connected peer every few seconds:

* **Anti-Vampire** — bans peers that download large amounts from you while reporting no or negligible progress
* **Progress-cheat detection (PCB)** — catches clients that report fake progress: ratio mismatch, progress rewinds, and excessive client counts
* **Client-name blacklist** — bans peers whose client/user-agent matches a blacklist pattern
* **IP / CIDR blacklist** — bans exact IPs or whole address ranges

All detections are tunable from **Settings → Peer blacklist → PBH tab** (thresholds, ban duration, check interval, per-module toggles).

### 🌐 BTN (BitTorrent Threat Network) support

Implements the [BTN-Spec v2.0.1](https://github.com/PBH-BTN/BTN-Spec) (protocol version 20), connecting to the public Sparkle instance out of the box:

* **Cloud rules** — automatically fetch the community-maintained IP deny/allow lists and peer-identity rules (refreshed on the server's schedule)
* **Ban submission** — share your bans with the BTN network (opt-in, per-spec consent)
* **Swarm submission** — publish anonymous, salted-hash anonymised swarm snapshots (opt-in)
* **Anonymous login** — works with just an installation ID; or link an AppID/AppSecret to receive personalised rules

Configure it under **Settings → Peer blacklist → BTN tab**. Everything is opt-in: no data leaves your device unless you enable the toggles.

### 📊 Live activity log

The **Settings → Peer blacklist → PBH tab** shows a live, scrollable log of every detection, ban, BTN rule refresh and submission — so you always know what the engine is doing and why a peer was banned.

## 🌍 Translations

Help translate the app at [Hosted Weblate](https://hosted.weblate.org/engage/libretorrent/)

![languages](https://hosted.weblate.org/widgets/libretorrent/-/multi-auto.svg)

## 💰 Donations

The development is 100% funded by heroic people like you. If you have problems with payment or you want to donate in another way, contact me at `proninyaroslav@mail.ru`. Thank you!

 - **Bitcoin**: `12isaLkH8nZ4DkFguVFeYrGHqQi7EEgUrM `
 - **USDT TRC20**: `TK79fzUYwRtmANuLjk1Zzhz3hjTaFQbxfg`
 - **Monero**: `48j4Mo7J7t51EeBf35Lpdmehmi9chUwzSXxHrnjpRJ6fPQafPWvSCdFafw3rA5ZRWievfYEDToNso8VppbJf2RVH9cdZmHa`
 - **YooMoney (ЮMoney)**: `410011738561939`
 - **Patreon**: [patreon.com/YaroslavPronin](https://patreon.com/YaroslavPronin)
 - **Boosty**: [boosty.to/yaroslavpronin/donate](https://boosty.to/yaroslavpronin/donate)
 - **Amazon.com eGift Cards**: just choose your amount and type e-mail `proninyaroslav@mail.ru`
in the gift card details [smile.amazon.com/gp/product/B004LLIKVU](https://smile.amazon.com/gp/product/B004LLIKVU)
 - **Liberapay**: [![liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/proninyaroslav/donate)

## 🎉 Contributors

Please see [CONTRIBUTING.md](CONTRIBUTING.md)

#### Developers

* [Yaroslav Pronin](https://gitlab.com/proninyaroslav)

## 🔒 Privacy Policy

Please see our [Privacy Policy](PRIVACY.md).

## 📄 License

[![Large GPLv3 logo with “Free as in Freedom”](https://www.gnu.org/graphics/gplv3-with-text-136x68.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

    Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
    This file is part of LibreTorrent.
    LibreTorrent is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

**LibreTorrentEE** is a fork of LibreTorrent. The anti-leech engine and BTN client are an original, in-process reimplementation of the [PeerBanHelper](https://github.com/PBH-BTN/PeerBanHelper) detection modules and the [BTN-Spec](https://github.com/PBH-BTN/BTN-Spec) protocol — independent code, protocol-compatible, and licensed under the same GPLv3 terms as the rest of this project.
