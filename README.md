# Afinity - Yet Another Jellyfin Client

<p align="center">
  <img src="https://raw.githubusercontent.com/MakD/AFinity/refs/heads/master/screenshots/Logo/GithubBanner.png" alt="AFinity Banner" width="100%">
</p>
  
[![Release](https://img.shields.io/github/v/release/MakD/AFinity?style=for-the-badge&labelColor=000000&color=41A67E)](https://github.com/MakD/AFinity/releases)
![GitHub commits since latest release](https://img.shields.io/github/commits-since/Makd/Afinity/latest?sort=date&style=for-the-badge&labelColor=000000&color=465775&cacheSeconds=3600)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/MakD/Afinity?style=for-the-badge&labelColor=000000&color=05668D&cacheSeconds=600)
[![Stars](https://img.shields.io/github/stars/MakD/AFinity?style=for-the-badge&labelColor=000000&color=7b68ee)](https://github.com/MakD/AFinity/stargazers)
[![Issues](https://img.shields.io/github/issues/MakD/AFinity?style=for-the-badge&labelColor=000000&color=e94b3c)](https://github.com/MakD/AFinity/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/MakD/AFinity?style=for-the-badge&labelColor=000000&color=f39c12)](https://github.com/MakD/AFinity/pulls)
[![Downloads](https://img.shields.io/github/downloads/MakD/AFinity/total?style=for-the-badge&labelColor=000000&color=333446)](https://github.com/MakD/AFinity/releases)
[![License](https://img.shields.io/github/license/MakD/AFinity?style=for-the-badge&labelColor=000000&color=665687)](https://github.com/MakD/AFinity/blob/master/LICENSE.md)

A native Android client for Jellyfin servers built with Jetpack Compose and Material 3.

## Overview

<p>
<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&labelColor=000000&logo=android&logoColor=white" alt="Android Platform"/> &nbsp;
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=000000" alt="Language"/> &nbsp;
<img src="https://img.shields.io/badge/Framework-Jetpack%20Compose-3899EC?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=000000" alt="Framework"/> &nbsp;
<img src="https://img.shields.io/badge/Design-Material%203-7F8CAA?style=for-the-badge&logo=materialdesign&logoColor=white&labelColor=000000" alt="UI"/> &nbsp;
</p>

Afinity provides a clean, responsive interface for accessing your Jellyfin media library on Android devices. The app focuses on delivering smooth playback performance and an intuitive browsing experience.

## Reach out

<a href="https://discord.com/channels/1381737066366242896/1422939582533730325"><img alt="Discord" src="https://img.shields.io/badge/AFinity-%20Jellyfin%20Community?&logo=discord&logoColor=white&style=for-the-badge&label=Jellyfin%20Community&labelColor=5865F2&color=black"></a>

Connect with us on [Discord](https://discord.gg/uZTjF8c2Vm) and be part of the discussion

## Features

**Media Playback**

- Hardware-accelerated video playback with LibMPV
- Multiple audio and subtitle track support
- Resume functionality across sessions
- Trickplay navigation with thumbnail previews
- Media Segments Support (Intro/Outro Skipper)

**Content Discovery**

- Library browsing by content type
- Search and filtering capabilities
- Favorites management
- Cast and crew information

**Interface**

- Material 3 design with system theming
- Responsive layouts for different screen sizes
- Gesture-based player controls
- Dark and light theme support

**Server Integration**

- Secure authentication
- Playback progress synchronization
- Multiple quality options
- Background library updates

## Screenshots

<p align="center">
  <img src="screenshots/home.png" width="30%">
  &nbsp;
  <img src="screenshots/movie_details.png" width="30%">
  &nbsp;
  <img src="screenshots/show_details.png" width="30%">
  &nbsp;
  <img src="screenshots/library.png" width="30%">
  &nbsp;
  <img src="screenshots/watchlist.png" width="30%">
  &nbsp;
  <img src="screenshots/person.png" width="30%">
  &nbsp;
  <img src="screenshots/player.png" width="60%">
</p>

## Installation

**Requirements**

- Android 14+ (API level 35)
- Jellyfin server 10.8+

**Download Options**

#### GitHub Releases

Download the latest APK from our [Releases page](https://github.com/MakD/AFinity/releases)

#### From Source

```bash
git clone https://github.com/MakD/AFinity.git
cd AFinity
./gradlew assembleRelease
```

## Setup

1. Install and launch Afinity
2. Enter your Jellyfin server address (e.g., `http://192.168.1.100:8096`)
3. Sign in with your credentials
4. Access your media library

For remote access, ensure your Jellyfin server is configured for external connections.

## Technical Details

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with Repository pattern
- **DI**: Hilt
- **Navigation**: Navigation Compose
- **Player**: LibMPV
- **Networking**: Retrofit + Jellyfin SDK
- **Images**: Coil with BlurHash
- **Storage**: Room

## Development

**Building**

```bash
./gradlew build
./gradlew test
./gradlew installDebug
```

**Contributing**

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Roadmap

### Core Features

- [X] Download management for offline viewing
- [ ] Adaptive streaming with quality selection (transcoding support)
- [ ] Chromecast support
- [ ] Enhanced accessibility features
- [ ] Multi-user profile switching
- [ ] Multi-server support

### Player Enhancements

- [X] Picture-in-picture mode
- [X] Advanced subtitle styling options
- [ ] Audio delay adjustment
- [X] Playback speed controls

### UI/UX Improvements

- [X] Tablet-optimized layouts
- [X] Advanced search filters
- [X] Custom library views
- [X] Gesture customization

### Technical

- [ ] Background sync optimization
- [ ] Cache management
- [ ] Network quality detection
- [ ] Performance monitoring

## Acknowledgments

- [Jellyfin](https://jellyfin.org/) - Open source media server
- [MPV](https://mpv.io/) - Media player engine
- [libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android) by Jarne Demeulemeester - Android MPV integration

## Privacy

Afinity respects your privacy:

- **No tracking or analytics** are collected
- **All data stays local** or with your own Jellyfin server
- **No third-party services** are used without your explicit consent
- **Source code is open** for full transparency

## Disclaimer
AFinity does not support or condone piracy. The app is only for streaming media you personally own. It includes no media content, and any references or support requests related to piracy or related tools are strictly prohibited.

## License

LGPL-3.0 License - see [LICENSE](LICENSE.md) for details.

---

**Made with ❤️ for the Jellyfin community**

_Afinity is not affiliated with Jellyfin. Jellyfin is a trademark of the Jellyfin project._
