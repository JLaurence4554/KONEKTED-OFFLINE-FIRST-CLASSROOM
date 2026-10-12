# Moving the KONEKTED database to Supabase

Supabase is an online PostgreSQL database. It replaces the database on your laptop. It does **not** run the PHP files, so a PHP host is still needed (see "What is still needed" at the bottom).

You do **part A** (accounts and clicking on websites). Everything in the project is already prepared.

## Fast path: apply the schema from this folder (no SQL Editor needed)

If the Supabase project already exists, fill in `backend/supabase/.env.local`
with your **Session pooler** details (Supabase dashboard -> **Connect** button),
then run:

    cd backend/supabase
 npm install      (once)
    npm run apply   (safe to re-run; applies schema_supabase.sql and verifies)

`npm run check` only verifies, changing nothing. The file `.env.local` is
git-ignored because it contains the password - never commit or share it.

(Sections A-C below describe the same result the manual way.)

## A. Create the database (about 10 minutes)

1. Go to https://supabase.com and sign up (the "Start your project" button). Signing in with GitHub is fine.
2. Click **New project**.
   - **Name:** `konekted`
   - **Database password:** make a strong one and **save it somewhere safe**. You cannot read it again later, only reset it.
   - **Region:** pick **Southeast Asia (Singapore)**. It is the closest to the Philippines.
   - Plan: **Free**.
3. Wait a minute or two until the project is ready.
4. In the left menu open **SQL Editor** and click **New query**.
5. Open the file `backend/supabase/schema_supabase.sql` from this project, select everything (Ctrl + A), copy (Ctrl + C), paste it in the editor, and press **Run**. Run it **once**. It should say *Success. No rows returned*.
6. Check: left menu **Table Editor**. You should see 12 tables (users, classes, class_members, lessons, ...). A small "RLS" mark on each is the lock. That is correct.

## B. Get the connection details

1. At the top of the Supabase page click **Connect**.
2. Choose **Session pooler** (not "Direct connection"). The session pooler works on every network, including ones without IPv6.
3. You will see a host, a port, a user and a database. They look like this (yours will be different):

| Setting | Example |
|---|---|
| host | `aws-0-ap-southeast-1.pooler.supabase.com` |
| port | `5432` |
| user | `postgres.abcdefghijklmnop` (note the dot and your project code) |
| database | `postgres` |
| password | the one you chose in step A2 |

## C. Test the connection from this laptop

Open PowerShell in the project folder and run these lines, using your own values:

```powershell
$env:KONEKTED_DB_HOST = "aws-0-ap-southeast-1.pooler.supabase.com"
$env:KONEKTED_DB_PORT = "5432"
$env:KONEKTED_DB_NAME = "postgres"
$env:KONEKTED_DB_USER = "postgres.abcdefghijklmnop"
$env:KONEKTED_DB_PASS = "your-password"
$env:KONEKTED_DB_SSLMODE = "require"
C:\xampp\php\php.exe backend\supabase\check_connection.php
```

You want to see: `Connection is encrypted`, `All 12 KONEKTED tables are there` and `OK`. These settings only last in that PowerShell window. Nothing is saved on the laptop.

## D. The settings your PHP host needs

Whichever host runs the PHP files (Render, Railway, ...), give it these environment variables:

| Name | Value |
|---|---|
| `KONEKTED_DB_HOST` | the host from step B |
| `KONEKTED_DB_PORT` | `5432` |
| `KONEKTED_DB_NAME` | `postgres` |
| `KONEKTED_DB_USER` | the user from step B (with the dot) |
| `KONEKTED_DB_PASS` | your database password |
| `KONEKTED_DB_SSLMODE` | `require` |
| `KONEKTED_REQUIRE_HTTPS` | `1` |
| `KONEKTED_TRUST_PROXY` | `1` |

Never put the password in a file that goes to GitHub.

## Good to know

- **Free plan limits:** 500 MB database, 1 GB file storage. A free project is **paused after 1 week with no activity**. If it is paused, open the Supabase dashboard and click **Restore** before a demo. Check supabase.com/pricing for the current numbers.
- **Your existing accounts and classes** live in the database on your laptop. A new Supabase database starts empty. Moving the data over is possible (ask for it). The phones also keep their own copies and sync them back up.
- **Why the tables are locked:** Supabase publishes tables on the internet through its own API. KONEKTED does not use that API, so `schema_supabase.sql` locks every table for it. Do not turn the lock off and do not share the project's secret keys.
- If `backend/schema.sql` changes in the future, the same change is needed in `backend/supabase/schema_supabase.sql`.

## What is still needed

1. **A place to run the PHP files** - Render is prepared: `render.yaml` at the repository root plus `backend/router.php` (see backend/README.md, section 5). You still push the repo to GitHub and connect it on render.com.
2. **Uploaded files** (lesson attachments, profile pictures): done - set `KONEKTED_SUPABASE_URL` and `KONEKTED_SUPABASE_STORAGE_KEY` and every upload goes to the Supabase Storage bucket `attachments` instead of the PHP server's disk (works on Render and Railway without losing files on redeploys). Without those two settings, files stay on the server disk like on XAMPP.
3. **The app's server address** (`BASE_URL` in `ClassroomApp/app/build.gradle`) changed to the new PHP address, then rebuild the app.
