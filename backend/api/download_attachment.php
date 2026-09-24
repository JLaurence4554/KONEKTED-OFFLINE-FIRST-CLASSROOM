<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Hands one stored file (a lesson attachment or a profile picture) to a
// logged-in person who is allowed to have it - see may_download_file() in
// common.php. Before this, anyone holding the address could download any
// file. The app sends its login token in the Authorization header when it
// downloads (FileUtil on Android).

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$pdo = open_authenticated(null, $auth);

$file = $_GET['file'] ?? '';

if ($file === '' || preg_match('/[\/\\\\]/', $file) || strpos($file, '..') !== false) {
    send_json(false, 'Invalid file parameter.', null, 400);
}

try {
    ensure_attachments_dir();

    if (!may_download_file($pdo, $auth, $file)) {
        send_forbidden('You do not have access to this file.');
    }

    $fullPath = ATTACHMENTS_DIR . '/' . $file;
    if (!is_file($fullPath)) {
        send_json(false, 'Attachment not found.', null, 404);
    }

    // Determine MIME safely. Fall back to the stored mime when available.
    $mime = finfo_file(finfo_open(FILEINFO_MIME_TYPE), $fullPath);
    if ($mime === false || $mime === 'application/x-empty') {
        $mime = 'application/octet-stream';
    }

    header('Content-Type: ' . $mime);
    header('Content-Disposition: attachment; filename*=UTF-8\'\'' . rawurlencode(basename($fullPath)));
    header('Content-Length: ' . (int) filesize($fullPath));
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');

    // Disable output buffering so the file is streamed directly.
    if (ob_get_level()) {
        ob_end_clean();
    }

    readfile($fullPath);
    exit;
} catch (Throwable $e) {
    error_log('download_attachment.php error: ' . $e->getMessage());
    send_json(false, 'Unable to download the attachment.', null, 500);
}
