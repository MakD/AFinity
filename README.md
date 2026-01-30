# Afinity - Yet Another Jellyfin Client

<div align="center">
  <img src="https://raw.githubusercontent.com/MakD/AFinity/refs/heads/master/screenshots/Logo/GithubBanner.png" alt="AFinity Banner" width="100%">
  
[![Release](https://img.shields.io/github/v/release/MakD/AFinity?style=for-the-badge&labelColor=000000&color=41A67E)](https://github.com/MakD/AFinity/releases)
[![Stars](https://img.shields.io/github/stars/MakD/AFinity?style=for-the-badge&labelColor=000000&color=7b68ee)](https://github.com/MakD/AFinity/stargazers)
[![Downloads](https://img.shields.io/github/downloads/MakD/AFinity/total?style=for-the-badge&labelColor=000000&color=333446)](https://github.com/MakD/AFinity/releases)
[![License](https://img.shields.io/github/license/MakD/AFinity?style=for-the-badge&labelColor=000000&color=665687)](https://github.com/MakD/AFinity/blob/master/LICENSE.md)
<a href="https://discord.com/channels/1381737066366242896/1422939582533730325"><img alt="Discord" src="https://img.shields.io/badge/AFinity-%20Jellyfin%20Community?&logo=discord&logoColor=white&style=for-the-badge&label=Jellyfin%20Community&labelColor=black&color=5865F2"></a>

[Download Latest Release](https://github.com/MakD/AFinity/releases) • [View Screenshots](#screenshots) • [Report Bug](https://github.com/MakD/AFinity/issues) • [Request Feature](https://github.com/MakD/AFinity/issues)

</div>

## Overview

AFinity is a native Android application that brings your Jellyfin media library to life with a clean, responsive interface. Stream your movies, TV shows, and live TV with hardware-accelerated playback, discover new content through personalised recommendations, and request media directly through Seerr integration.

## Installation

#### GitHub Releases

Download the latest APK [Releases page](https://github.com/MakD/AFinity/releases)

#### Build From Source

```bash
git clone https://github.com/MakD/AFinity.git
cd AFinity
./gradlew assembleRelease
```

## Initial Setup

1. **Launch AFinity** on your Android device
2. **Enter Server Address** - Your Jellyfin server URL (e.g., `http://192.168.1.100:8096`)
3. **Sign In** - Use your Jellyfin credentials
4. **Start Streaming** - Access your entire media library

> **Tip:** For remote access, ensure your Jellyfin server is configured for external connections

### Optional: Seerr Integration

Enable media requests by connecting to your Jellyseerr server:

1. Navigate to **Settings → General**
2. Toggle **Jellyseerr** on
3. Enter your Jellyseerr server URL
4. Choose authentication:
   - **Jellyfin** - Use your Jellyfin credentials
   - **Local** - Use Jellyseerr email/password
5. Tap **Connect**

The Requests tab will appear in your bottom navigation once connected.

## Screenshots

<p align="center">
  <img src="screenshots/home.png" width="30%" alt="Home Page">
  &nbsp;
  <img src="screenshots/movie_details.png" width="30%" alt="Movie Details Page">
  &nbsp;
  <img src="screenshots/show_details.png" width="30%" alt="Show Details Page">
  <img src="screenshots/library.png" width="30%" alt="Library Page">
  &nbsp;
  <img src="screenshots/person.png" width="30%" alt="Favorite Page">
  &nbsp;
  <img src="screenshots/live_tv.png" width="30%" alt="Person Detail Page">
  <img src="screenshots/live_tv_guide.png" width="30%" alt="Library Page">
  &nbsp;
  <img src="screenshots/request_screen.png" width="30%" alt="Favorite Page">
  &nbsp;
  <img src="screenshots/request_prompt.png" width="30%" alt="Person Detail Page">
  <img src="screenshots/player.png" width="92%" alt="Player Screen">
</p>

## Features  
  
| **Category** | **Details** |  
|------------|------------|  
| **Media Playback** | - Hardware-accelerated video playback with LibMPV<br>- Multiple audio and subtitle track support<br>- Customizable subtitle appearance (color, size, position, style)<br>- Resume functionality across sessions<br>- Trickplay navigation with thumbnail previews<br>- Chapter markers and navigation<br>- Media Segments Support (Intro/Outro Skipper)<br>- Multiple video zoom modes (Fit, Zoom, Stretch)<br>- Picture-in-Picture mode with background audio control |  
| **Live TV** | - Watch live television channels<br>- Electronic Program Guide (EPG) with timeline navigation<br>- Browse programs by category (Movies, Sports, News, etc.)<br>- Mark channels as favourites<br>- Real-time program progress updates<br>- Direct stream support for IPTV sources |  
| **Content Discovery** | - Library browsing by content type<br>- Personalized home screen with dynamic recommendations<br>- Genre, studio, and network browsing<br>- Search and filtering capabilities with alphabet scroller<br>- Favorites and watchlist management<br>- Cast and crew information with full filmography<br>- Episode switcher in player for quick navigation |  
| **Seerr Integration** | - Request movies and TV shows with season selection<br>- Browse trending, popular, and upcoming content<br>- Search integration with request filter<br>- Track request status (Pending, Approved, Available)<br>- Real-time request status updates across app<br>- Approve / Reject incoming requests (admin only)<br>- Detailed media information in request dialog (ratings, runtime, cast) |  
| **Interface** | - Material 3 design with system theming<br>- Responsive adaptive layouts for phones, tablets, and foldables<br>- Gesture-based player controls (brightness, volume, seeking)<br>- Dark and light theme support with dynamic colors<br>- Customizable episode layout (horizontal/vertical)<br>- Edge-to-edge display support |  
| **Server Integration** | - Secure authentication with encrypted credential storage<br>- Multi-server & multi-user support with quick session switching<br>- Playback progress synchronization<br>- Multiple quality options<br>- Background library updates and downloads<br>- One-tap login for saved accounts |

## Technical Stack

<div align="center">

![Android 15+](https://img.shields.io/badge/Platform-Android%2015+-3DDC84?style=for-the-badge&labelColor=000000&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=000000)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-3899EC?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=000000)
![Material 3](https://img.shields.io/badge/Design-Material%203-7F8CAA?style=for-the-badge&logo=materialdesign&logoColor=white&labelColor=000000)
![Jellyfin 10.10.x](https://img.shields.io/badge/Server-10.10.x-AA5CC3?style=for-the-badge&logo=jellyfin&logoColor=white&labelColor=000000)

</div>

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Hilt
- **Navigation**: Navigation Compose
- **Media Player**: LibMPV + ExoPlayer
- **Networking**: Retrofit + Jellyfin SDK
- **Image Loading**: Coil with BlurHash
- **Local Storage**: Room
- **Security** - Tink (Encrypted Credentials)
- **Preferences** - Jetpack DataStore

## Roadmap

### Core Features

- [X] Download management for offline viewing
- [ ] Adaptive streaming with quality selection (transcoding support)
- [ ] Chromecast support
- [X] Enhanced accessibility features
- [X] Multi-user profile switching
- [X] Multi-server support

### Player Enhancements

- [X] Picture-in-picture mode
- [X] Advanced subtitle styling options
- [ ] Audio delay adjustment
- [X] Playback speed controls

### UI/UX Improvements

- [X] Tablet-optimized layouts
- [X] Advanced search filters
- [X] Custom library views
- [X] Gesture customisation

### Technical

- [X] Background sync optimisation
- [ ] Cache management
- [X] Network quality detection
- [ ] Performance monitoring

## Contributing

We welcome contributions from the community! Whether you're a developer, designer, or translator, here is how you can help:

### Help Translate
We use **Weblate** to manage translations. You can help translate AFinity into your language directly in your browser without needing any technical knowledge.

1. Visit our [Weblate Project](https://hosted.weblate.org/projects/afinity-yet-another-jellyfin-client/).
2. Select your language (or start a new one).
3. Start translating! Changes are automatically synced back to this repository.

### Code Contributions

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'Add some AmazingFeature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. **Open** a Pull Request

### Development Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Install debug build
./gradlew installDebug
```

## Project Stats

<div align="center">

<a href="https://star-history.com/#MakD/AFinity&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=MakD/AFinity&type=Datee&legend=bottom-right&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=MakD/AFinity&type=Datee&legend=bottom-right" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=MakD/AFinity&type=Date&legend=bottom-right" />
  </picture>
</a>

&nbsp;

![GitHub commit activity](https://img.shields.io/github/commit-activity/w/MakD/Afinity?style=for-the-badge&labelColor=000000&color=05668D)
![GitHub commits since latest release](https://img.shields.io/github/commits-since/Makd/Afinity/latest?sort=date&style=for-the-badge&labelColor=000000&color=465775)
![GitHub Downloads (latest release)](https://img.shields.io/github/downloads/Makd/AFinity/latest/total?sort=date&style=for-the-badge&labelColor=black&color=A3D78A)

[![Issues](https://img.shields.io/github/issues/MakD/AFinity?style=for-the-badge&labelColor=000000&color=e94b3c)](https://github.com/MakD/AFinity/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/MakD/AFinity?style=for-the-badge&labelColor=000000&color=f39c12)](https://github.com/MakD/AFinity/pulls)

[![Translation status](https://hosted.weblate.org/widget/afinity-yet-another-jellyfin-client/afinity/multi-auto.svg)](https://hosted.weblate.org/engage/afinity-yet-another-jellyfin-client/)

</div>

## Acknowledgments

AFinity stands on the shoulders of giants. Special thanks to:

- [Jellyfin](https://jellyfin.org/) - The open source media server that makes it all possible
- [MPV](https://mpv.io/) - Media player engine
- [libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android) by Jarne Demeulemeester - Android MPV integration
- [Seerr](https://github.com/seerr-team/seerr) - Open-source media discovery and request manager for Jellyfin, Plex, and Emby.

## Privacy

Afinity respects your privacy:

- **No tracking or analytics** are collected
- **All data stays local** or with your own Jellyfin server
- **No third-party services** are used without your explicit consent
- **Source code is open** for full transparency

## Disclaimer
**AFinity does not support or condone piracy.** This application is designed solely for streaming media content that you personally own or have legal rights to access. AFinity includes no media content whatsoever. Any references, discussions, or support requests related to piracy or related tools are strictly prohibited and will be removed.

## License

This project is licensed under the **LGPL-3.0 License** - see the [LICENSE](LICENSE.md) file for details.

## Support the Project

AFinity is a personal project developed and maintained in my free time. If the app has improved your media experience, there are a few ways you can help support its growth:

### Community Support
* **Star the Project:** Give us a ⭐ on GitHub—it helps others find the app!
* **Contribute:** AFinity is open-source. Bug reports and Pull Requests are always welcome.
* **Join the Discussion:** Share your feedback or get help on our [Discord](https://discord.com/channels/1381737066366242896/1422939582533730325).

### Buy the Developer a Coffee
If you've spent hours enjoying your library through AFinity and want to show some appreciation, you can support my work here. It helps keep me fueled for those late-night coding sessions!

[![Support on Ko-fi](https://img.shields.io/badge/Support%20on-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white&labelColor=000000)](https://ko-fi.com/m0rph3us)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white&labelColor=000000)](https://buymeacoffee.com/m0rphi)

*Every contribution, whether it's code, a bug report, or a coffee, helps make AFinity better.*

---

**Made with ❤️ for the Jellyfin community**

*AFinity is an independent project and is not affiliated with Jellyfin or Seerr.*  
*Jellyfin is a trademark of the Jellyfin project.*

---

**[Back to Top](#afinity---yet-another-jellyfin-client)**
