<?php
// PostgreSQL connection settings.
// A default PostgreSQL install uses user 'postgres' with the password
// you set during installation, on localhost port 5432.
//
// Every setting below can also be supplied as an environment variable
// (KONEKTED_DB_HOST, KONEKTED_DB_PORT, KONEKTED_DB_NAME, KONEKTED_DB_USER,
// KONEKTED_DB_PASS), which wins over the value written here. On a real
// server, set them in the web server's environment instead of keeping a
// password in this file, and use a database user that only owns this
// database (not the "postgres" superuser).
define('DB_HOST', getenv('KONEKTED_DB_HOST') ?: 'localhost');
define('DB_PORT', getenv('KONEKTED_DB_PORT') ?: '5432');
define('DB_NAME', getenv('KONEKTED_DB_NAME') ?: 'classroom_sync');
define('DB_USER', getenv('KONEKTED_DB_USER') ?: 'postgres');
define('DB_PASS', getenv('KONEKTED_DB_PASS') !== false ? getenv('KONEKTED_DB_PASS') : 'Password123'); // Set your PostgreSQL password here

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
define('REQUIRE_HTTPS', getenv('KONEKTED_REQUIRE_HTTPS') === '1');
define('TRUST_PROXY_HEADERS', getenv('KONEKTED_TRUST_PROXY') === '1');

// Server-side storage for the files the app uploads (lesson attachments and
// profile pictures). They are only ever served back through
// api/download_attachment.php, so the web server must NEVER serve this folder
// directly: on a XAMPP setup it sits inside the document root, and a file that
// kept a script extension (.php) would otherwise run as code just by opening
// its address. On Apache, ensure_attachments_dir() drops a deny-all .htaccess
// here; on nginx add `location /attachments/ { deny all; }` (see
// backend/README.md). common.php also limits uploads to harmless extensions.
define('ATTACHMENTS_DIR', __DIR__ . '/../attachments');

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
    $dsn = 'pgsql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME;
    return new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
}
