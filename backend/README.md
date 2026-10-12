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

- **Login protection (tokens).** Every endpoint except `register.php` and
  `login.php` needs a login token. Those two hand one out (`kt_` + 64 hex
  characters); the app sends it as `Authorization: Bearer <token>` (or
  `X-Auth-Token`). The server stores only its SHA-256 (table `auth_tokens`),
  so a database leak doesn't leak logins. Tokens last 90 days (renewed when
  used), each user keeps their newest 10 (one per phone), and
  `logout.php` ends one (or all, with `"allDevices": true`).
  - The server works out WHO is asking from the token and never trusts an
    email in the request body; a body email that isn't the token's account is
    refused (403 `ACCOUNT_MISMATCH`). Teachers can only touch their own
    classes, students only classes they belong to (403 otherwise).
  - Status codes: `401` no valid token (`code: AUTH_REQUIRED`), `403` wrong
    role / not yours, `429` too many attempts (`RATE_LIMITED`), `426` plain
    HTTP refused (`HTTPS_REQUIRED`). Everything else keeps the old
    "HTTP 200 + `success:false`" convention.
  - Rate limits (table `login_attempts`): 8 wrong passwords per email and 100
    per IP per 15 minutes; 30 sign-ups per IP per hour. Unknown emails take
    as long as real ones to answer, so login can't be used to find out which
    emails have accounts.
  - The auth tables are in `schema.sql`, and also created automatically on
    first use, so upgrading an existing database needs no manual step.
  - **Apache:** `api/.htaccess` passes the `Authorization` header through to
    PHP. **nginx / PHP-FPM:** add `fastcgi_param HTTP_AUTHORIZATION
    $http_authorization;` to the PHP location (the app also sends
    `X-Auth-Token` as a fallback).
  - **Settings** (environment variables, all optional; defaults are the
    XAMPP values in `config/database.php`): `KONEKTED_DB_HOST`,
    `KONEKTED_DB_PORT`, `KONEKTED_DB_NAME`, `KONEKTED_DB_USER`,
    `KONEKTED_DB_PASS`; `KONEKTED_DB_SSLMODE=require` encrypts the database
    connection (needed for a hosted database such as Supabase; see
    `supabase/SUPABASE_SETUP.md`); `KONEKTED_REQUIRE_HTTPS=1` refuses plain HTTP (use it
    on a real server); `KONEKTED_TRUST_PROXY=1` trusts `X-Forwarded-For` /
    `X-Forwarded-Proto` from a reverse proxy or ngrok (only set it when one
    really is in front, otherwise anyone can fake their address).
    `KONEKTED_REGISTER_MAX_PER_IP=N` sets how many sign-ups one address may make per hour (default 30;
    raise it when many phones share one WiFi, e.g. a class test). On Apache you can set any of these in
    `api/.htaccess` with a `SetEnv NAME value` line (a commented example is there). **Behind ngrok, turn
    `KONEKTED_TRUST_PROXY` on**, or every phone counts as one visitor.
  - **Test it:** `C:\xampp\php\php.exe backend\tests\api_test.php` builds a
    throwaway database and web server, and checks every endpoint both as the
    app uses it and as an attacker would (no login, someone else's login, the
    wrong role, another person's class...). It never touches real data.
  - **Known limits:** sign-up doesn't verify the email address, so anyone can
    claim an email that has no account yet; there is no password reset yet;
    `get_quizzes.php` still returns correct answers (to class members) and
    unpublished drafts, since the app grades on the phone.
- **Passwords.** `register.php` / `login.php` store and check a
  `password_hash()` of each password (never the password itself), so a
  reinstalled or second phone can log in. Old app versions (before login
  tokens) can no longer sync against this server; reinstall the current app.
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
  `config/` in the server config) yourself.- **Use HTTPS for anything beyond local testing.** ngrok's free tier already gives you HTTPS for testing; a real deployment should terminate TLS (a proper certificate, e.g. via Let's Encrypt) rather than serving plain HTTP.

## 5. Deploying for real on Render (replacing ngrok)

ngrok changes its URL every restart. For a permanent address, the same
`backend/` folder runs on Render (free plan) and talks to the Supabase
database (see `supabase/SUPABASE_SETUP.md`):

1. Push this repository to GitHub.
2. On https://dashboard.render.com: **New +** -> **Web Service** -> connect
   the repo. Render picks up `render.yaml` (repo root) automatically - PHP
   has no native runtime on Render, so a small Docker container
   (`backend/Dockerfile`) runs `php -S 0.0.0.0:$PORT router.php` inside
   `backend/`.
3. When asked, fill the three secret settings: `KONEKTED_DB_HOST`,
   `KONEKTED_DB_USER`, `KONEKTED_DB_PASS` (Supabase dashboard -> **Connect**
   -> **Session pooler**).
4. After the first deploy, put the service URL in
   `ClassroomApp/app/build.gradle` (internet build type, keep the trailing
   `/api/`) and rebuild: `./gradlew assembleInternet`.

`router.php` replaces the `.htaccess` rules (Render has no Apache): only `/`
and `/api/<endpoint>.php` are served; `config/`, `supabase/`, `tests/` and
`attachments/` answer 404. Known free-plan limits: the service sleeps after
~15 minutes of no visits (the first phone to open the app waits ~1 minute for
it to wake), and uploaded files are lost on every redeploy (see the `disks:`
comment in `render.yaml`).

## 6. Deploying on Railway (always awake - for real users)

Same container as Render: `railway.json` at the repo root points Railway at
`backend/Dockerfile`. About $5 a month, never sleeps, no keep-alive tricks -
the right choice once real people use the app instead of a demo.

1. Push this repository to GitHub.
2. On https://railway.com: **New Project** -> **Deploy from GitHub repo** ->
   pick the repo. Railway reads `railway.json` and builds the image.
3. Open the service -> **Variables** and set:

   | Name | Value |
   |---|---|
   | `KONEKTED_DB_HOST`, `KONEKTED_DB_PORT`, `KONEKTED_DB_NAME`, `KONEKTED_DB_USER`, `KONEKTED_DB_PASS` | Supabase dashboard -> **Connect** -> **Session pooler** |
   | `KONEKTED_DB_SSLMODE` | `require` |
   | `KONEKTED_REQUIRE_HTTPS` | `1` |
   | `KONEKTED_TRUST_PROXY` | `1` |
   | `KONEKTED_REGISTER_MAX_PER_IP` | `200` |
   | `KONEKTED_SUPABASE_URL` | Supabase -> **Settings** -> **API** -> Project URL (`https://xxxx.supabase.co`) |
   | `KONEKTED_SUPABASE_STORAGE_KEY` | same page -> **service_role** key (a secret - never commit it) |

4. Service -> **Networking** -> **Generate Domain** -> copy the HTTPS URL.
5. Put the URL in `ClassroomApp/app/build.gradle` (internet build type, keep
   the trailing `/api/`) and rebuild: `./gradlew assembleInternet`.

With the two `KONEKTED_SUPABASE_*` variables set, every uploaded file goes to
the Supabase Storage bucket `attachments` (created automatically) instead of
the container's disk - so lesson files and profile pictures survive every
redeploy, and no Railway volume is needed. Leaving those two variables out
keeps files on the server disk, exactly like XAMPP.

The container starts PHP's built-in server with **four worker processes**
(`PHP_CLI_SERVER_WORKERS` in `backend/Dockerfile`). That server answers one
request at a time by default, so a single slow request makes every other one
queue behind it - and the app sends several calls at once whenever a dashboard
opens, which showed up as bursts of HTTP 499 in Railway's **Network Logs** (499
means the caller gave up before the server answered, here the app's own
10-second timeout). Raise the Railway variable `PHP_CLI_SERVER_WORKERS` on a
plan with more CPU; leave it unset to keep the default of 4.
