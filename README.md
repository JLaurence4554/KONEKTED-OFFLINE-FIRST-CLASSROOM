# ClassroomApp - Online Sync

**KONEKTED** -- Offline-First Classroom Learning System with Online Sync

This is the active build folder for the ClassroomApp project.

## Quick Start

### Prerequisites
- Android Studio installed
- PostgreSQL running with `classroom_sync` database
- XAMPP (or equivalent) for PHP backend

### Build & Run
1. Open this folder in Android Studio
2. Set JAVA_HOME in terminal: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
3. Run: `./gradlew assembleDebug`
4. Click Run to deploy to device/emulator

### Backend Setup
1. Copy `backend/` folder to `C:\xampp\htdocs\classroom-api\`
2. Enable PostgreSQL in XAMPP (`php.ini`: uncomment `extension=pdo_pgsql` and `extension=pgsql`)
3. Restart Apache
4. Update `backend/config/database.php` with your PostgreSQL password
5. Test: `http://localhost/classroom-api/api/find_class.php?code=TEST123`

## Project Structure
```
ClassroomApp/
├── app/src/main/java/com/malapay/classroom/
│   ├── data/          # Room database, DAOs, entities, repositories
│   ├── sync/          # SyncManager, ApiClient, network layer
│   ├── ui/            # Activities, Fragments, Adapters
│   └── util/          # Helpers (SessionManager, PasswordUtil, etc.)
├── app/build.gradle   # Build config with BASE_URL per build type
└── backend/           # PHP API + PostgreSQL schema
```

## Build Types

| Build type | `BASE_URL`                            | Use case                         | Build command                  |
|------------|---------------------------------------|----------------------------------|--------------------------------|
| `debug`    | `http://192.168.0.10/classroom-api/` | Same-LAN testing                | `./gradlew assembleDebug`      |
| `internet` | Your ngrok HTTPS URL                  | Internet/cross-network testing   | `./gradlew assembleInternet`   |
| `release`  | Placeholder (replace before ship)     | Production deployment            | `./gradlew assembleRelease`    |

### Internet testing (capstone demo)

1. Start ngrok: `ngrok http 80`
2. Copy the HTTPS URL (e.g. `https://abc123.ngrok-free.app`)
3. Edit `app/build.gradle` and update the `internet` build type:
   ```groovy
   internet {
       buildConfigField "String", "BASE_URL", '"https://abc123.ngrok-free.app/classroom-api/"'
   }
   ```
4. Build: `./gradlew assembleInternet`
5. Install APK from `app/build/outputs/apk/internet/`

See `backend/README.md` for full ngrok setup instructions.

## Last Updated
August 29, 2026
