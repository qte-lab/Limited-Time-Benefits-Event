# Gift Activity Center

A multi-platform limited-time benefits event management system with Android client support.

## Project Overview

This project provides a comprehensive solution for managing and displaying limited-time promotional activities across multiple platforms. It consists of two main components:

- **Backend (Go)**: RESTful API server for data management and APK distribution
- **Android App**: Native Android application built with Jetpack Compose

## Features

### Backend API
- RESTful API endpoints for activities and markdown content
- APK file management and distribution
- Changelog management for version updates
- Static file serving with gzip compression
- Data caching mechanism (60 seconds)

### Android Application
- Jetpack Compose UI with Miuix design system
- Three main screens: Home, Answers, Settings
- Automatic update checking and download
- Multi-language support
- Theme switching (Light/Dark/System)
- Markdown content rendering
- Responsive layout for different screen sizes

## Project Structure

```
gift/
├── android/              # Android application
│   ├── app/
│   │   └── src/
│   │       └── main/
│   │           ├── java/com/chronie/gift/
│   │           │   ├── data/           # Data managers
│   │           │   │   ├── DownloadManager.kt
│   │           │   │   ├── LanguageManager.kt
│   │           │   │   ├── LicenseInfo.kt
│   │           │   │   ├── TabManager.kt
│   │           │   │   ├── ThemeManager.kt
│   │           │   │   └── UpdateChecker.kt
│   │           │   ├── ui/             # UI components
│   │           │   │   └── GiftApp.kt
│   │           │   ├── MainActivity.kt
│   │           │   └── GiftApplication.kt
│   │           └── res/                # Resources
│   └── gradle/
├── server/               # Data directory (legacy)
│   └── data/             # Activity and changelog data
├── server-go/            # Go backend API
│   ├── main.go           # Server entry point with routing
│   ├── handlers.go       # API handlers
│   ├── cache.go          # Cache implementation
│   ├── go.mod            # Go module definition
│   └── gift-server.exe   # Pre-built executable
└── .gitignore
```

## Technology Stack

### Backend
- **Runtime**: Go 1.26.4
- **Standard Library**: net/http, encoding/json, os, path/filepath, sync
- **Features**:
  - Built-in HTTP server
  - Gzip compression middleware
  - In-memory caching with TTL (60 seconds)

### Android Application
- **Language**: Kotlin 2.4.0
- **UI Framework**: Jetpack Compose
- **Design System**: Miuix KMP 0.9.3
- **Key Libraries**:
  - Ktor Client 3.5.1 - HTTP client
  - Coil 3.5.0 - Image loading
  - Kotlinx Serialization - JSON serialization
  - Miuix KMP - Design components (ui, preference, icons, blur)
  - Navigation Compose 2.9.8 - Navigation

## API Endpoints

### Activities
- `GET /api/activities` - Get list of activities

### APK Management
- `GET /api/download_apk` - Get list of available APKs with version info
- `GET /api/download_apk/:filename` - Download specific APK file

### Markdown Content
- `GET /api/outdate-test/markdown` - Get list of markdown files
- `GET /api/outdate-test/markdown/:filename` - Get content of specific markdown file

### API Response Format

All API responses follow a unified format:

```json
{
  "success": true,
  "data": [...]
}
```

### APK Info Response

The `/api/download_apk` endpoint returns additional version information:

```json
{
  "success": true,
  "data": ["app-release.apk"],
  "latest": "app-release.apk",
  "latestSize": "15.5",
  "versionCode": 100,
  "versionName": "1.0.0",
  "changelog": {
    "en": "Version notes",
    "zh-cn": "版本说明"
  }
}
```

## Installation

### Prerequisites
- Go 1.26.4 or higher
- Android Studio (for Android development)
- JDK 11 or higher

### Backend Setup

1. Navigate to the Go server directory:
   ```bash
   cd server-go
   ```

2. Build the server:
   ```bash
   go build -o gift-server.exe
   ```

3. Start the server:
   ```bash
   ./gift-server.exe
   ```

The server will start on `http://localhost:3001` by default.

### Android App Setup

1. Open the `android` directory in Android Studio
2. Wait for Gradle sync to complete
3. Build and run the app on an emulator or physical device

### Data Configuration

Create the following data files in `server/data/`:

- `activities.json` - Activity list data
- `changelog.json` - Version changelog
- `outdate-test-markdown/` - Directory for markdown files

Place APK files in `server/apk/` for distribution.

Example `activities.json`:
```json
{
  "activities": [
    {
      "id": "activity-001",
      "title": "Summer Sale",
      "startTime": "2026-07-01 00:00:00",
      "endTime": "2026-07-31 23:59:59",
      "url": "https://example.com/summer-sale",
      "description": "Up to 50% off on selected items",
      "type": "promotion"
    }
  ]
}
```

Example `changelog.json`:
```json
{
  "changelog": {
    "en": "Version notes in English",
    "zh-cn": "Version notes in Chinese Simplified",
    "zh-tw": "Version notes in Chinese Traditional",
    "ja": "Version notes in Japanese"
  }
}
```

## Development

### Backend Development

The backend is a lightweight Go HTTP server. Key files:

- `server-go/main.go` - Main server file with routing and gzip middleware
- `server-go/handlers.go` - API endpoint handlers
- `server-go/cache.go` - In-memory cache implementation

### Android Development

The Android app follows modern Android development practices:

- Use Jetpack Compose for UI
- Follow MVVM architecture patterns
- Implement proper lifecycle management
- Use coroutines for asynchronous operations

## Multi-Language Support

The project supports four languages:
- English (en)
- Chinese Simplified (zh-CN)
- Chinese Traditional (zh-TW)
- Japanese (ja)

### Android App

Language strings are defined in `res/values/` directories:
- `values/` - Default (English)
- `values-zh-rCN/` - Chinese Simplified
- `values-zh-rTW/` - Chinese Traditional
- `values-ja/` - Japanese

## Version Management

### Android App Versioning

The Android app uses a timestamp-based versioning scheme:
- Format: `1.YYYYMMDD.HHMM`
- Example: `1.20260203.0031`

Version is automatically generated during build using system timestamp.

### Version Info API

The Go backend reads version information from `server/apk/output-metadata.json`:

```json
{
  "elements": [
    {
      "outputFile": "app-release.apk",
      "versionCode": 100,
      "versionName": "1.0.0"
    }
  ]
}
```

## Configuration

### Server Port

The server port can be configured via environment variable:
```bash
PORT=3001 ./gift-server.exe
```

### API Base URL

The Android app's API base URL is configured in `UpdateChecker.kt`. The current default URL is `http://192.168.10.6:3001`. Update the `apiBaseUrl` variable to match your server address.

## Building

### Android APK

To build a release APK:

1. Open the project in Android Studio
2. Select Build > Generate Signed Bundle / APK
3. Follow the signing wizard
4. The APK will be generated in `app/release/`

Place the generated APK in `server/apk/` for distribution.

### Go Server

To build the server for different platforms:

```bash
# Windows
go build -o gift-server.exe

# Linux
GOOS=linux GOARCH=amd64 go build -o gift-server

# macOS
GOOS=darwin GOARCH=arm64 go build -o gift-server
```

## License

NO LICENSE, DON'T USE THIS CODE FOR COMMERCIAL PURPOSES.

## Author

Chronie