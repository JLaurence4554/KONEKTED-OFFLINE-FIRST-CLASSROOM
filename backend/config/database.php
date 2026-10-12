<?php
// PostgreSQL connection settings.
// A default PostgreSQL install uses user 'postgres' with the password
// you set during installation, on localhost port 5432.
//
// Every setting below can also be supplied as an environment variable
// (KONEKTED_DB_HOST, KONEKTED_DB_PORT, KONEKTED_DB_NAME, KONEKTED_DB_USER,
// KONEKTED_DB_PASS, KONEKTED_DB_SSLMODE), which wins over the value written here. On a real
// server, set them in the web server's environment instead of keeping a
// password in this file, and use a database user that only owns this
// database (not the "postgres" superuser).
define('DB_HOST', getenv('KONEKTED_DB_HOST') ?: 'localhost');
define('DB_PORT', getenv('KONEKTED_DB_PORT') ?: '5432');
define('DB_NAME', getenv('KONEKTED_DB_NAME') ?: 'classroom_sync');
define('DB_USER', getenv('KONEKTED_DB_USER') ?: 'postgres');
define('DB_PASS', getenv('KONEKTED_DB_PASS') !== false ? getenv('KONEKTED_DB_PASS') : 'Password123'); // Set your PostgreSQL password here
// Encrypted connection to the database. Leave it unset for a database on the same
// computer (XAMPP). A hosted database such as Supabase needs KONEKTED_DB_SSLMODE=require.
// Only the values PostgreSQL knows are accepted; anything else is ignored.
$konektedSslMode = getenv('KONEKTED_DB_SSLMODE') ?: '';
define('DB_SSLMODE', in_array($konektedSslMode, ['disable', 'allow', 'prefer', 'require', 'verify-ca', 'verify-full'], true) ? $konektedSslMode : '');

// Seconds allowed for one attempt to reach the database. libpq's own default is
// the operating system's, which on an address that never answers runs past a
// minute; every request opens its own connection (see get_db_connection()) and
// the built-in server has only a few workers (see backend/Dockerfile), so one
// attempt that never gives up would take the whole API down with it.
// Override with KONEKTED_DB_CONNECT_TIMEOUT.
define('DB_CONNECT_TIMEOUT', (int) (getenv('KONEKTED_DB_CONNECT_TIMEOUT') ?: 10));

// Login protection (see api/common.php). Every endpoint except login.php and
// register.php needs the token the app receives when it logs in.
//   KONEKTED_REQUIRE_HTTPS=1  refuse plain-HTTP requests. Turn this on in
//                             production, where the site has a real certificate
//                             (or sits behind a proxy that sets
//                             X-Forwarded-Proto).
//   KONEKTED_TRUST_PROXY=1    take the visitor's address from
//                             X-Forwarded-For (only when a proxy you control,
//                             e.g. nginx, sets it) so login attempts are
//                             counted per real visitor instead of per proxy.
//   KONEKTED_REGISTER_MAX_PER_IP=N   sign-ups allowed per address per hour (default 30).
//                             Raise it for a class or a test session where many
//                             phones share one WiFi (they look like one address).
// A setting can come from a real environment variable, or from Apache's
// "SetEnv KONEKTED_... 1" line (e.g. in api/.htaccess), which PHP sees in $_SERVER.
function konekted_flag(string $name): bool {
    return getenv($name) === '1' || (($_SERVER[$name] ?? '') === '1');
}
// A number setting, same two sources; falls back to the default when unset or not a positive whole number.
function konekted_int(string $name, int $default): int {
    $raw = getenv($name);
    if ($raw === false) {
        $raw = $_SERVER[$name] ?? '';
    }
    return ctype_digit((string) $raw) && (int) $raw > 0 ? (int) $raw : $default;
}
define('REQUIRE_HTTPS', konekted_flag('KONEKTED_REQUIRE_HTTPS'));
define('TRUST_PROXY_HEADERS', konekted_flag('KONEKTED_TRUST_PROXY'));

// Server-side storage for the files the app uploads (lesson attachments and
// profile pictures). They are only ever served back through
// api/download_attachment.php, so the web server must NEVER serve this folder
// directly: on a XAMPP setup it sits inside the document root, and a file that
// kept a script extension (.php) would otherwise run as code just by opening
// its address. On Apache, ensure_attachments_dir() drops a deny-all .htaccess
// here; on nginx add `location /attachments/ { deny all; }` (see
// backend/README.md). common.php also limits uploads to harmless extensions.
define('ATTACHMENTS_DIR', __DIR__ . '/../attachments');

// Optional home for uploaded files: Supabase Storage instead of this server's
// disk. Set all three environment variables and uploads, downloads and
// deletes switch to the Supabase project's private "attachments" bucket, so
// files survive redeploys and work on hosts with a temporary disk (Render,
// Railway). Left unset, the server disk above is used exactly as before.
//   KONEKTED_SUPABASE_URL          e.g. https://abcdefgh.supabase.co (Settings -> API -> Project URL)
//   KONEKTED_SUPABASE_STORAGE_KEY  the service_role key (Settings -> API) - server-side
//                                  only; never commit it or send it to the app
//   KONEKTED_STORAGE_BUCKET        optional, defaults to "attachments"
define('SUPABASE_URL', rtrim((string) (getenv('KONEKTED_SUPABASE_URL') ?: ''), '/'));
define('SUPABASE_STORAGE_KEY', (string) (getenv('KONEKTED_SUPABASE_STORAGE_KEY') ?: ''));
define('STORAGE_BUCKET', (string) (getenv('KONEKTED_STORAGE_BUCKET') ?: 'attachments'));

/** True when uploads are kept in Supabase Storage rather than on this server's disk. */
function storage_enabled(): bool {
    return SUPABASE_URL !== '' && SUPABASE_STORAGE_KEY !== '';
}

/**
 * Ensures the attachments directory exists and is closed off from direct web
 * access. Safe to call on every request.
 */
function ensure_attachments_dir(): void {
    if (!is_dir(ATTACHMENTS_DIR)) {
        if (!mkdir(ATTACHMENTS_DIR, 0755, true) && !is_dir(ATTACHMENTS_DIR)) {
            throw new RuntimeException('Could not create attachments directory: ' . ATTACHMENTS_DIR);
        }
    }
    write_attachments_guard(ATTACHMENTS_DIR);
}

/**
 * Puts a deny-all .htaccess in the given folder (Apache) unless one is already
 * there, so nothing in it can be requested - or run - directly.
 */
function write_attachments_guard(string $dir): void {
    $guard = $dir . '/.htaccess';
    if (is_file($guard)) {
        return;
    }
    $rules = "# Files in this folder are only ever served through api/download_attachment.php.\n"
           . "# Created automatically by ensure_attachments_dir(); please leave it in place.\n"
           . "Require all denied\n";
    //noinspection PhpUsageOfSuppressOperator
    @file_put_contents($guard, $rules);
}

/**
 * Opens (and returns) a PDO connection to PostgreSQL.
 * Throws PDOException on failure — every endpoint wraps its DB work
 * in try/catch and turns that into the "Unable to connect to the server.
 * Please try again." response.
 */
function get_db_connection(): PDO {
    // Bounded, so a database that is unreachable fails this one request
    // quickly instead of holding a worker open until the OS gives up.
    $dsn = 'pgsql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME
         . ';connect_timeout=' . DB_CONNECT_TIMEOUT;
    if (DB_SSLMODE !== '') {
        $dsn .= ';sslmode=' . DB_SSLMODE;
    }
    return new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
}
