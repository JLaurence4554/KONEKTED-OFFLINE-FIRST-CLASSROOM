<?php
/**
 * router.php - lets the KONEKTED API run on PHP's built-in web server.
 *
 * Render / Railway / Fly run plain `php -S` with NO Apache, which means the
 * .htaccess files in api/ and config/ are completely IGNORED there. This file
 * does their security job instead, and is referenced by render.yaml:
 *
 *     php -S 0.0.0.0:$PORT router.php
 *
 * Only two things are exposed:
 *   /                      a tiny health page (used by Render's health check)
 *   /api/<endpoint>.php    the app's own API files, executed normally
 * Everything else answers 404 - notably config/ (holds the database password),
 * supabase/ (holds .env.local), tests/ and attachments/ (uploaded files must
 * only leave through api/download_attachment.php, never be served directly).
 */
declare(strict_types=1);

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';

// The app authenticates with "Authorization: Bearer <token>" (X-Auth-Token as a
// fallback). php -S passes the header through, but CGI-style setups rename it;
// restore it so api/common.php sees it (same job as api/.htaccess on Apache).
if (!isset($_SERVER['HTTP_AUTHORIZATION']) && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
    $_SERVER['HTTP_AUTHORIZATION'] = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
}

// Health page for Render's health check (and a human sanity check in a browser).
if ($path === '/') {
    header('Content-Type: text/plain; charset=utf-8');
    echo "KONEKTED API is running. Endpoints live under /api/.";
    return true;
}

// Only names matching the app's own endpoint files are ever executed.
if (preg_match('#^/api/[A-Za-z0-9_]+\.php$#', $path) && is_file(__DIR__ . $path)) {
    require __DIR__ . $path;
    return true;
}

// Everything else - including the folders and files listed in the docblock.
http_response_code(404);
header('Content-Type: application/json; charset=utf-8');
echo json_encode(['success' => false, 'message' => 'Not found.', 'data' => null], JSON_UNESCAPED_SLASHES);
return true;
