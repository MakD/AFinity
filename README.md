# Afinity - Yet Another Jellyfin Client

<div align="center">
  <img src="https://raw.githubusercontent.com/MakD/AFinity/refs/heads/master/screenshots/Logo/GithubBanner.png" alt="AFinity Banner" width="100%">
  
[![Release](https://img.shields.io/github/v/release/MakD/AFinity?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjIyIDIwIDY0IDY0Ij48cGF0aCBmaWxsPSIjZmZmZmZmIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0yMy4xNiw2OS45NmMwLjA0LC0zLjM3IDAuNjMsLTYuMTEgMS40NywtOC44YzEuNjIsLTUuMiAzLjk4LC0xMC4wNiA2LjY0LC0xNC43OWMyLjQzLC00LjM0IDUuMDksLTguNTIgOC4yMywtMTIuMzljMi4zLC0yLjgzIDQuODEsLTUuNDYgNy44NiwtNy41MWMxLjUzLC0xLjAzIDMuMTYsLTEuODIgNC45OSwtMi4xOGMyLjQ0LC0wLjQ4IDQuNjgsMC4xNSA2LjgxLDEuMjljMi43NywxLjQ5IDUuMDMsMy42MSA3LjE1LDUuOWM0LjEyLDQuNDcgNy4zOSw5LjU1IDEwLjQxLDE0LjhjMi40MSw0LjE4IDQuNTIsOC41IDYuMTMsMTMuMDZjMS4wOCwzLjA4IDEuODksNi4yMyAyLDkuNTFjMC4wOSwyLjUgLTAuMjUsNC45MSAtMS43NCw3LjAyYy0xLjMxLDEuODcgLTMuMTQsMy4wOCAtNS4xNSw0LjA1Yy0zLjMsMS41OCAtNi44MiwyLjQzIC0xMC40MSwyLjk0Yy05LjA4LDEuMjkgLTE4LjE3LDEuMzQgLTI3LjI1LDAuMDFjLTMuOTEsLTAuNTcgLTcuNzIsLTEuNTcgLTExLjIyLC0zLjQ3Yy0yLjAyLC0xLjA5IC0zLjc2LC0yLjUyIC00LjgxLC00LjYyQzIzLjQsNzMuMTEgMjMuMTcsNzEuMjkgMjMuMTYsNjkuOTZ6TTU0LjM1LDM1LjE3YzcuMTIsNC4yNiAxMi45OSw5Ljc3IDE4LjUyLDE1LjY5Yy0wLjA1LC0wLjE5IC0wLjEyLC0wLjM2IC0wLjIxLC0wLjUxYy0xLjQ1LC0yLjQgLTIuODQsLTQuODQgLTQuMzUsLTcuMmMtMi40MiwtMy43NyAtNS4xOSwtNy4yNyAtOC40NiwtMTAuMzVjLTEuMDQsLTAuOTggLTIuMTQsLTEuOSAtMy40NiwtMi40OWMtMS43MywtMC43OCAtMy4xOCwtMC40IC00LjE4LDEuMjFjLTAuNTYsMC45IC0xLjA0LDEuODcgLTEuMzcsMi44NmMtMS42NCw0LjkyIC0yLjI4LDEwLjAyIC0yLjUsMTUuMThjLTAuMTcsMy45NSAtMC4wNSw3LjkgMC4zNCwxMS44NGMwLjAzLDAuMyAtMC4wMiwwLjQ4IC0wLjMxLDAuNjRjLTEuNDgsMC44MyAtMi45NCwxLjY5IC00LjQxLDIuNTNjLTAuMTMsMC4wNyAtMC4yNiwwLjEyIC0wLjQ4LDAuMjJjLTAuNjksLTUuMjMgLTAuNzMsLTEwLjQyIC0wLjQ3LC0xNS42OGMtMC4xMSwwLjA4IC0wLjE0LDAuMSAtMC4xNiwwLjEyYy0wLjA1LDAuMTMgLTAuMDksMC4yNSAtMC4xMywwLjM4Yy0xLjcyLDUuNjMgLTIuOTQsMTEuMzQgLTMuMjYsMTcuMjNjLTAuMDEsMC4yNiAtMC4wOCwwLjQyIC0wLjM1LDAuNTJjLTEuNTUsMC41OSAtMy4xLDEuMiAtNC42NCwxLjhjLTAuMTcsMC4wNiAtMC4zNCwwLjEgLTAuNTksMC4xOGMwLjAyLC00LjExIDAuNTQsLTguMDggMS4zLC0xMi4wM2MwLjc2LC0zLjk0IDEuODEsLTcuODEgMi45NiwtMTEuNjdjLTAuMTUsMC4xMiAtMC4yNiwwLjI1IC0wLjM0LDAuNGMtMC43OCwxLjM1IC0xLjU4LDIuNjkgLTIuMzQsNC4wNmMtMi42NSw0LjgxIC00Ljk0LDkuNzkgLTYuMjYsMTUuMTVjLTAuMzQsMS4zNiAtMC40OCwyLjc4IC0wLjYxLDQuMTdjLTAuMDgsMC44OCAwLjEyLDEuNzYgMC42NywyLjVjMC42NywwLjkgMS42NywxLjI0IDIuNzIsMS4yYzEuMzIsLTAuMDYgMi42NSwtMC4xOCAzLjk0LC0wLjQ3YzMuNzQsLTAuODUgNy4yOSwtMi4yNSAxMC43NSwtMy44OGM0LjQzLC0yLjA5IDguNTksLTQuNjMgMTIuNTgsLTcuNDZjMC4zOCwtMC4yNyAwLjY1LC0wLjI4IDEuMDUsLTAuMDRjMS4zNiwwLjgzIDIuNzUsMS42MSA0LjEzLDIuNDFjMC4xNSwwLjA5IDAuMywwLjE5IDAuNTIsMC4zMmMtNC4yMSwzLjIzIC04LjcxLDUuODYgLTEzLjMzLDguMzFjMC4yMSwwLjAyIDAuMzgsLTAuMDEgMC41NiwwLjA5YzUuNywtMS4zNCAxMS4yNiwtMy4xMiAxNi41MSwtNS43OGMwLjI4LC0wLjE0IDAuNDcsLTAuMTIgMC43MSwwLjA3YzAuOTYsMC43OSAxLjk1LDEuNTUgMi45MiwyLjMzYzAuNDUsMC4zNiAwLjg4LDAuNzQgMS40LDEuMTdjLTcuMTYsNC4wMSAtMTQuODMsNi4zMSAtMjIuNjYsOC4xN2MwLjIsMC4wOCAwLjM4LDAuMSAwLjU2LDAuMDljMy40NSwtMC4xIDYuOTEsLTAuMSAxMC4zNSwtMC4zMmMzLjkyLC0wLjI1IDcuNzgsLTAuOTQgMTEuNTMsLTIuMTJjMS4yOSwtMC40MSAyLjU0LC0wLjg5IDMuNjIsLTEuNzRjMS4yNywtMC45OSAxLjYsLTIuMTkgMS4wNSwtMy43MWMtMC40NSwtMS4yMyAtMS4yNSwtMi4yMyAtMi4xLC0zLjE4Yy0zLjAxLC0zLjM0IC02LjUyLC02LjEgLTEwLjIzLC04LjU5Yy0zLjc3LC0yLjUzIC03Ljc0LC00LjcyIC0xMS44NywtNi41OWMtMC4zMiwtMC4xNSAtMC41LC0wLjMxIC0wLjUsLTAuNjljMC4wMiwtMS42MyAwLjAxLC0zLjI2IDAuMDEsLTAuODhjMCwtMC4xOSAwLjAyLC0wLjM5IDAuMDMsLTAuNjdjNC45MSwyLjA0IDkuNDQsNC42MiAxMy44Nyw3LjRjLTAuMDQsLTAuMTQgLTAuMTEsLTAuMjMgLTAuMTksLTAuMzFjLTQuMDYsLTQuMzUgLTguNDMsLTguMzMgLTEzLjQzLC0xMS41OWMtMC4yNiwtMC4xNyAtMC4zNCwtMC4zNCAtMC4yOCwtMC42NmMwLjE1LC0wLjc4IDAuMjQsLTEuNTcgMC4zNywtMi4zNkM1NC4wMSwzNy4zIDU0LjE3LDM2LjMgNTQuMzUsMzUuMTd6Ii8+PC9zdmc+&labelColor=000000&color=41A67E)](https://github.com/MakD/AFinity/releases)
[![Stars](https://img.shields.io/github/stars/MakD/AFinity?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0iI2ZmZmZmZiI+PHBhdGggZD0iTTguMjQzIDcuMzRsLTYuMzggLjkyNWwtLjExMyAuMDIzYTEgMSAwIDAgMCAtLjQ0IDEuNjg0bDQuNjIyIDQuNDk5bC0xLjA5IDYuMzU1bC0uMDEzIC4xMWExIDEgMCAwIDAgMS40NjQgLjk0NGw1LjcwNiAtM2w1LjY5MyAzbC4xIC4wNDZhMSAxIDAgMCAwIDEuMzUyIC0xLjFsLTEuMDkxIC02LjM1NWw0LjYyNCAtNC41bC4wNzggLS4wODVhMSAxIDAgMCAwIC0uNjMzIC0xLjYybC02LjM4IC0uOTI2bC0yLjg1MiAtNS43OGExIDEgMCAwIDAgLTEuNzk0IDBsLTIuODUzIDUuNzh6IiAvPjwvc3ZnPg==&logoColor=ffffff&labelColor=000000&color=7b68ee)](https://github.com/MakD/AFinity/stargazers)
[![Downloads](https://img.shields.io/github/downloads/MakD/AFinity/total?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgLTk2MCA5NjAgOTYwIiBmaWxsPSJ3aGl0ZSI+PHBhdGggZD0iTTIwMC0xNjBoNTYwcTE3IDAgMjguNSAxMS41VDgwMC0xMjBxMCAxNy0xMS41IDI4LjVUNzYwLTgwSDIwMHEtMTcgMC0yOC41LTExLjVUMTYwLTEyMHEwLTE3IDExLjUtMjguNVQyMDAtMTYwWm0yODAtMTA1cS05IDAtMTcuNS00VDQ0OC0yODFMMjUwLTUzNXEtMTUtMjAtNC00Mi41dDM2LTIyLjVoNzh2LTI0MHEwLTE3IDExLjUtMjguNVQ0MDAtODgwaDE2MHExNyAwIDI4LjUgMTEuNVQ2MDAtODQwdjI0MGg3OHEyNSAwIDM2IDIyLjV0LTQgNDIuNUw1MTItMjgxcS02IDgtMTQuNSAxMnQtMTcuNSA0WiIvPjwvc3ZnPg==&logoColor=ffffff&labelColor=000000&color=333446)](https://github.com/MakD/AFinity/releases)
[![License](https://img.shields.io/github/license/MakD/AFinity?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgLTk2MCA5NjAgOTYwIiBmaWxsPSJ3aGl0ZSI+PHBhdGggZD0iTTQ4MC00NDBxLTUwIDAtODUtMzV0LTM1LTg1cTAtNTAgMzUtODV0ODUtMzVxNTAgMCA4NSAzNXQzNSA4NXEwIDUwLTM1IDg1dC04NSAzNVptMCAzMjBMMjkzLTU4cS0yMCA3LTM2LjUtNVQyNDAtOTV2LTI1NHEtMzgtNDItNTktOTZ0LTIxLTExNXEwLTEzNCA5My0yMjd0MjI3LTkzcTEzNCAwIDIyNyA5M3Q5MyAyMjdxMCA2MS0yMSAxMTV0LTU5IDk2djI1NHEwIDIwLTE2LjUgMzJUNjY3LTU4bC0xODctNjJabTAtMjAwcTEwMCAwIDE3MC03MHQ3MC0xNzBxMC0xMDAtNzAtMTcwdC0xNzAtNzBxLTEwMCAwLTE3MCA3MHQtNzAgMTcwcTAgMTAwIDcwIDE3MHQxNzAgNzBaIi8+PC9zdmc+&logoColor=ffffff&labelColor=000000&color=665687)](https://github.com/MakD/AFinity/blob/master/LICENSE.md)
<a href="https://discord.com/channels/1381737066366242896/1422939582533730325"><img alt="Discord" src="https://img.shields.io/badge/AFinity-%20Jellyfin%20Community?&logo=discord&logoColor=white&style=for-the-badge&label=Jellyfin%20Community&labelColor=black&color=5865F2"></a>

[Download](#installation) • [View Screenshots](#screenshots) • [Report Bug](https://github.com/MakD/AFinity/issues) • [Request Feature](https://github.com/MakD/AFinity/issues)

</div>

## Overview

​AFinity is a native Android application that brings your Jellyfin media library to life with a clean, responsive interface. Stream your movies, TV shows, and live TV with hardware-accelerated playback, and discover new content through personalised recommendations. Beyond video, AFinity extends your experience with optional Audiobookshelf integration for audiobooks and podcasts, and direct media requests via Jellyseerr.

## Installation

<p align="center">
  <a href="https://github.com/MakD/AFinity/releases/latest"><img src="https://img.shields.io/badge/Get%20it%20on-GitHub-181717?style=for-the-badge&logo=github&labelColor=000000" alt="Get it on GitHub"></a>&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.makd.afinity%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FMakD%2FAFinity%22%2C%22author%22%3A%22MakD%22%2C%22name%22%3A%22AFinity%22%2C%22supportFixedAPKURL%22%3Afalse%7D"><img src="https://img.shields.io/badge/Get%20it%20on-Obtainium-4A148C?style=for-the-badge&logo=obtainium&logoColor=white&labelColor=000000" alt="Get it on Obtainium"></a>
</p>

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
  <img src="screenshots/audiobookshelf_screen.png" width="30%" alt="Audiobookshelf Page">
  &nbsp;
  <img src="screenshots/book_detail.png" width="30%" alt="Audiobook Detail Page">
  &nbsp;
  <img src="screenshots/audiobook_player.png" width="30%" alt="Audiobook Player Screen">
  <img src="screenshots/player.png" width="92%" alt="Player Screen">
</p>

## Features  
  
| **Category** | **Details** |  
|------------|------------|  
| **Media Playback** | - Hardware-accelerated video playback with LibMPV<br>- Multiple audio and subtitle track support<br>- Customizable subtitle appearance (color, size, position, style)<br>- Resume functionality across sessions<br>- Trickplay navigation with thumbnail previews<br>- Chapter markers and navigation<br>- Media Segments Support (Intro/Outro Skipper)<br>- Multiple video zoom modes (Fit, Zoom, Stretch)<br>- Picture-in-Picture mode with background audio control |  
| **Live TV** | - Watch live television channels<br>- Electronic Program Guide (EPG) with timeline navigation<br>- Browse programs by category (Movies, Sports, News, etc.)<br>- Mark channels as favourites<br>- Real-time program progress updates<br>- Direct stream support for IPTV sources |  
| **Content Discovery** | - Library browsing by content type<br>- Personalized home screen with dynamic recommendations<br>- Genre, studio, and network browsing<br>- Search and filtering capabilities with alphabet scroller<br>- Favorites and watchlist management<br>- Cast and crew information with full filmography<br>- Episode switcher in player for quick navigation |  
| **Seerr Integration** *(Optional)* | - Request movies and TV shows with season selection<br>- Browse trending, popular, and upcoming content<br>- Search integration with request filter<br>- Track request status (Pending, Approved, Available)<br>- Real-time request status updates across app<br>- Approve / Reject incoming requests (admin only)<br>- Detailed media information in request dialog (ratings, runtime, cast) |  
| **Audiobookshelf Integration** *(Optional)* | - Connect to Audiobookshelf servers for audiobook and podcast playback<br>- Browse libraries with tabbed navigation (Home, Series, Libraries)<br>- Genre-based discovery and series collections<br>- Background audio playback with media notifications and lock screen controls<br>- Sleep timer and adjustable playback speed<br>- Chapter navigation with progress syncing<br>- Persistent mini-player for navigation during playback<br>- Integration with main search |
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

![GitHub commit activity](https://img.shields.io/github/commit-activity/w/MakD/Afinity?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cG9seWxpbmUgcG9pbnRzPSIyMiAxMiAxOCAxMiAxNSAyMSA5IDMgNiAxMiAyIDEyIi8+PC9zdmc+&labelColor=000000&color=05668D)
![GitHub commits since latest release](https://img.shields.io/github/commits-since/Makd/Afinity/latest?sort=date&style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IndoaXRlIiBzdHJva2Utd2lkdGg9IjIiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgY2xhc3M9ImZlYXRoZXIgZmVhdGhlci1naXQtY29tbWl0Ij48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSI0Ij48L2NpcmNsZT48bGluZSB4MT0iMS4wNSIgeTE9IjEyIiB4Mj0iNyIgeTI9IjEyIj48L2xpbmU+PGxpbmUgeDE9IjE3LjAxIiB5MT0iMTIiIHgyPSIyMi45NiIgeTI9IjEyIj48L2xpbmU+PC9zdmc+&labelColor=000000&color=465775)
![GitHub Downloads (latest release)](https://img.shields.io/github/downloads/Makd/AFinity/latest/total?sort=date&style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IndoaXRlIiBzdHJva2Utd2lkdGg9IjIiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgY2xhc3M9ImZlYXRoZXIgZmVhdGhlci1kb3dubG9hZC1jbG91ZCI+PHBvbHlsaW5lIHBvaW50cz0iOCAxNyAxMiAyMSAxNiAxNyI+PC9wb2x5bGluZT48bGluZSB4MT0iMTIiIHkxPSIxMiIgeDI9IjEyIiB5Mj0iMjEiPjwvbGluZT48cGF0aCBkPSJNMjAuODggMTguMDlBNSA1IDAgMCAwIDE4IDloLTEuMjZBOCA4IDAgMSAwIDMgMTYuMjkiPjwvcGF0aD48L3N2Zz4=&labelColor=black&color=A3D78A)

[![Issues](https://img.shields.io/github/issues/MakD/AFinity?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBmaWxsPSIjZmZmZmZmIiBkPSJNMTIgMWM2LjA3NSAwIDExIDQuOTI1IDExIDExcy00LjkyNSAxMS0xMSAxMVMxIDE4LjA3NSAxIDEyIDUuOTI1IDEgMTIgMVpNMi41IDEyYTkuNSA5LjUgMCAwIDAgOS41IDkuNSA5LjUgOS41IDAgMCAwIDkuNS05LjVBOS41IDkuNSAwIDAgMCAxMiAyLjUgOS41IDkuNSAwIDAgMCAyLjUgMTJabTkuNSAyYTIgMiAwIDEgMS0uMDAxLTMuOTk5QTIgMiAwIDAgMSAxMiAxNFoiLz48L3N2Zz4=&logoColor=ffffff&labelColor=000000&color=e94b3c)](https://github.com/MakD/AFinity/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/MakD/AFinity?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IndoaXRlIiBzdHJva2Utd2lkdGg9IjIiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgY2xhc3M9ImZlYXRoZXIgZmVhdGhlci1naXQtcHVsbC1yZXF1ZXN0Ij48Y2lyY2xlIGN4PSIxOCIgY3k9IjE4IiByPSIzIj48L2NpcmNsZT48Y2lyY2xlIGN4PSI2IiBjeT0iNiIgcj0iMyI+PC9jaXJjbGU+PHBhdGggZD0iTTEzIDZoM2EyIDIgMCAwIDEgMiAydjciPjwvcGF0aD48bGluZSB4MT0iNiIgeTE9IjkiIHgyPSI2IiB5Mj0iMjEiPjwvbGluZT48L3N2Zz4=&labelColor=000000&color=f39c12)](https://github.com/MakD/AFinity/pulls)

</div>

[![Translation status](https://hosted.weblate.org/widget/afinity-yet-another-jellyfin-client/afinity/multi-auto.svg)](https://hosted.weblate.org/engage/afinity-yet-another-jellyfin-client/)

## Acknowledgments

AFinity stands on the shoulders of giants. Special thanks to:

- [Jellyfin](https://jellyfin.org/) - The open source media server that makes it all possible
- [MPV](https://mpv.io/) - Media player engine
- [libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android) by Jarne Demeulemeester - Android MPV integration
- [Seerr](https://github.com/seerr-team/seerr) - Open-source media discovery and request manager for Jellyfin, Plex, and Emby.
- [Audiobookshelf](https://github.com/advplyr/audiobookshelf) - Open-source self-hosted audiobook and podcast server.

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

*AFinity is an independent project and is not affiliated with Jellyfin, Audiobookshelf or Seerr.*  
*Jellyfin is a trademark of the Jellyfin project.*

---

**[Back to Top](#afinity---yet-another-jellyfin-client)**
