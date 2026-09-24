# Classroom Sync Backend

PHP + PostgreSQL REST API backing the Android app's online Join Class flow.
Three endpoints, matching the capstone spec's "REQUIRED API OPERATIONS":

| Endpoint                  | Method | Purpose                                   |
|----------------------------|--------|--------------------------------------------|
| `api/sync_class.php`       | POST   | Teacher uploads/updates a class            |
| `api/find_class.php`       | GET    | Student looks up a class by code           |
| `api/join_class.php`       | POST   | Student membership is recorded server-side |

## 1. XAMPP setup

1. Copy this whole `backend/` folder into your XAMPP `htdocs/` directory —
   e.g. `C:\xampp\htdocs\classroom-api\` (Windows) or
   `/Applications/XAMPP/htdocs/classroom-api/` (macOS). The folder name you
   choose here (`classroom-api` in these instructions) becomes part of the
   URL the Android app calls.
2. Start **Apache** and **MySQL** from the XAMPP control panel.
3. Open phpMyAdmin (`http://localhost/phpmyadmin`) and run `schema.sql`
   (Import tab, or paste it into the SQL tab) to create the
   `classroom_sync` database and its three tables.
4. If your MySQL isn't the XAMPP default (root / no password), edit
   `config/database.php` and set `DB_USER`/`DB_PASS` to match. Nothing else
   needs to change for local testing.
5. Sanity check: visit `http://localhost/classroom-api/api/find_class.php?code=TEST`
   in a browser. You should get back
   `{"success":false,"message":"Class code not found.","data":null}` — that
   means PHP, the DB connection, and the endpoint are all working.

## 2. Making it reachable over the INTERNET (ngrok)

This is the part the capstone test scenario actually depends on, and it's
worth being explicit about: **`http://localhost/...` or your machine's LAN
IP (e.g. `192.168.1.23`) will only ever work for devices on the same
network as your computer.** For a student on a different Wi-Fi network (or
mobile data) to reach this backend, it needs a publicly-reachable address.

### Step-by-step: ngrok (recommended for capstone demos)

ngrok tunnels your local XAMPP server to a public HTTPS URL — free, instant,
and perfect for demos.

1. **Install ngrok**: download from https://ngrok.com and run the installer.

2. **Get your authtoken**: sign up (free), then copy your authtoken from
   https://dashboard.ngrok.com/get-started/your-authtoken

3. **Set the authtoken** (run once):
   ```
   ngrok config add-authtoken YOUR_AUTHTOKEN_HERE
   ```

4. **Start the tunnel** (while XAMPP Apache is running on port 80):
   ```
   ngrok http 80
   ```
   You'll see output like:
   ```
   Forwarding  https://abc123.ngrok-free.app -> http://localhost
   ```
   Copy that `https://...ngrok-free.app` URL.

5. **Test it in a browser**: visit
   `https://abc123.ngrok-free.app/classroom-api/api/find_class.php?code=TEST`
   You should get back a JSON response — that confirms the tunnel works.

6. **Update the Android app's BASE_URL**: open `ClassroomApp/app/build.gradle`
   and replace `YOUR-NGROK-ID` in the `internet` build type with your actual
   ngrok subdomain:
   ```groovy
   internet {
       buildConfigField "String", "BASE_URL", '"https://abc123.ngrok-free.app/classroom-api/"'
   }
   ```

7. **Build the internet variant**:
   ```
   cd ClassroomApp
   ./gradlew assembleInternet
   ```

8. **Install on your device**: the APK is at
   `app/build/outputs/apk/internet/app-internet-debug.apk`
   Install it via `adb install` or transfer to your phone.

### ngrok URL changes on restart

Each time you stop and restart ngrok, you get a **new URL**. When that
happens:
1. Copy the new URL from the ngrok terminal
2. Update `BASE_URL` in `build.gradle` (the `internet` build type)
3. Rebuild: `./gradlew assembleInternet`
4. Reinstall the APK

### Alternative: Real deployment (stable URL)

For a permanent setup (no URL changes, no rebuilds needed), deploy this
same `backend/` folder on any PHP+PostgreSQL host reachable from the
internet (a school server, shared hosting, a small VPS, etc.). Point
`BASE_URL` at that domain and use HTTPS in production (see Security Notes
below).

## 3. Android configuration

The app has three build types configured in `app/build.gradle`:

| Build type | `BASE_URL`                            | Use case                         |
|------------|---------------------------------------|----------------------------------|
| `debug`    | `http://192.168.0.10/classroom-api/` | Same-LAN testing                |
| `internet` | Your ngrok HTTPS URL                  | Internet/cross-network testing   |
| `release`  | Placeholder (replace before ship)     | Production deployment            |

**For internet testing (capstone demo):**

1. Edit `ClassroomApp/app/build.gradle` and update the `internet` build
   type's `BASE_URL` with your ngrok URL (see Section 2 above).
2. Build: `./gradlew assembleInternet`
3. Install the APK from `app/build/outputs/apk/internet/`

**For same-machine LAN testing:**

1. Build: `./gradlew assembleDebug`
2. Install the APK from `app/build/outputs/apk/debug/`

The URL must end with a trailing `/` and include the `api/`-less path up to
(but not including) the endpoint filenames — Retrofit appends
`sync_class.php`, `find_class.php`, `join_class.php` to whatever you put
here, so if your backend folder is `classroom-api/`, `BASE_URL` should be
`.../classroom-api/api/`.

If you're testing over plain HTTP (no TLS certificate on your dev/XAMPP
setup), the app's manifest has `android:usesCleartextTraffic="true"` so
Android doesn't silently block the request — see the note in
`AndroidManifest.xml` about removing this once you deploy behind HTTPS.
ngrok's free tier already provides HTTPS, so this only matters for
the `debug` (LAN) build type.

## 4. Security notes (read before deploying anywhere real)

- **No authentication on this API yet.** Anyone who can reach these
  endpoints and knows (or guesses) a class code can query it, and anyone
  can claim any name/email when joining. This was out of scope for "Class
  + Join sync" per the spec, but it's a real gap — before any real
  deployment, add an auth layer (e.g. an API key header checked in
  `common.php`, or proper token-based auth tied to the app's existing
  login) so these endpoints aren't open to the public internet
  unprotected.
- **Passwords.** `register.php` / `login.php` store and check a
  `password_hash()` of each password (never the password itself), so a
  reinstalled or second phone can log in. Every endpoint is still unprotected
  beyond "the request names an email that exists" (see the first note).
- **Uploaded files must never be served or run directly.** The
  `attachments/` folder (lesson files and profile pictures) sits inside the
  document root on XAMPP. It is only meant to be read through
  `api/download_attachment.php`. `ensure_attachments_dir()` writes a deny-all
  `.htaccess` there (Apache), and uploads are limited to a list of harmless
  extensions in `common.php` (anything else is stored as `.bin`), because a
  file stored as `.php` used to be runnable by opening its address. On
  nginx, add `location /attachments/ { deny all; }` yourself, and better still
  move the folder outside the document root (`ATTACHMENTS_DIR` in
  `config/database.php`).
- **`config/database.php` has an `.htaccess` denying direct HTTP access**
  to the whole `config/` folder, so the credentials file can't be fetched
  directly even if someone knows/guesses its URL. This only works on
  Apache with `.htaccess` enabled (XAMPP's default) — if you deploy on
  nginx or another server, replicate the equivalent block (deny access to
  `config/` in the server config) yourself.
- **Use HTTPS for anything beyond local testing.** ngrok's free tier
  already gives you HTTPS for testing; a real deployment should terminate
  TLS (a proper certificate, e.g. via Let's Encrypt) rather than serving
  plain HTTP.
