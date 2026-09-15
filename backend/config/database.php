<?php
// PostgreSQL connection settings.
// A default PostgreSQL install uses user 'postgres' with the password
// you set during installation, on localhost port 5432.
define('DB_HOST', 'localhost');
define('DB_PORT', '5432');
define('DB_NAME', 'classroom_sync');
define('DB_USER', 'postgres');
define('DB_PASS', 'Password123'); // Set your PostgreSQL password here

// Server-side storage for lesson attachment files uploaded by the app.
// Files live under this directory (relative to the backend root) and are
// served back to clients through download_attachment.php. The path is
// app-private in the sense that it is outside the web root only when the
// backend is deployed with attachments/ outside the document root; for a
// XAMPP/local deployment this sits alongside the API files and is guarded
// by the .htaccess-style deny in common.php's send_json + the fact that
// download_attachment.php is the only sanctioned access path.
define('ATTACHMENTS_DIR', __DIR__ . '/../attachments');

/**
 * Ensures the attachments directory exists. Safe to call on every request.
 */
function ensure_attachments_dir(): void {
    if (!is_dir(ATTACHMENTS_DIR)) {
        if (!mkdir(ATTACHMENTS_DIR, 0755, true) && !is_dir(ATTACHMENTS_DIR)) {
            throw new RuntimeException('Could not create attachments directory: ' . ATTACHMENTS_DIR);
        }
    }
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
