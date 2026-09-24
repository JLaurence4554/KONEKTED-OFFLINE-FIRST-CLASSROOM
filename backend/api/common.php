<?php
header('Content-Type: application/json; charset=utf-8');

/**
 * Every endpoint replies with this same shape, matching Android's
 * ApiEnvelope<T>: {"success": bool, "message": string, "data": ...|null}.
 *
 * Deliberately uses HTTP 200 for every *expected* outcome — including
 * "not found" / "already a member" / "missing fields" — and only a non-200
 * code for a genuine server-side exception. Android's Retrofit client only
 * reliably gets the parsed JSON body via response.body() on a 2xx response;
 * non-2xx bodies land in errorBody() instead, which would mean writing a
 * second parsing path on the Android side for zero benefit. Putting the
 * real signal in the "success" field instead keeps both sides simpler.
 */
function send_json(bool $success, string $message, $data = null, int $httpCode = 200): void {
    http_response_code($httpCode);
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data' => $data,
    ]);
    exit;
}

/**
 * Full download URL (via download_attachment.php) for a file stored in
 * ATTACHMENTS_DIR - lesson attachments and profile pictures.
 *
 * Built from the request's own host rather than HTTP_ORIGIN (a browser-only
 * header the Android app never sends) or REQUEST_URI (a path only - no
 * scheme or host - which Uri.parse() + ACTION_VIEW on Android can't
 * resolve). Honors X-Forwarded-Proto since ngrok terminates HTTPS at its
 * edge and forwards to XAMPP over plain HTTP; without that check the scheme
 * would wrongly come out as "http" even when the app reached this over https.
 */
function public_file_url(string $storedName): string {
    $scheme = 'http';
    if (!empty($_SERVER['HTTP_X_FORWARDED_PROTO'])) {
        $scheme = strtolower(trim(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'])[0]));
    } elseif (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') {
        $scheme = 'https';
    }
    $host = $_SERVER['HTTP_HOST'] ?? ($_SERVER['SERVER_NAME'] ?? 'localhost');
    // SCRIPT_NAME (unlike REQUEST_URI) has no query string to worry about, e.g.
    // "/classroom-api/api/get_lessons.php" -> "/classroom-api".
    $scriptDir = rtrim(preg_replace('#/api/[^/]+$#', '', $_SERVER['SCRIPT_NAME'] ?? ''), '/');
    return $scheme . '://' . $host . $scriptDir . '/api/download_attachment.php?file=' . rawurlencode($storedName);
}

/** Reads and JSON-decodes the request body (for POST endpoints). Empty/invalid body -> empty array. */
function read_json_body(): array {
    $raw = file_get_contents('php://input');
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : [];
}

/**
 * Finds a user by email, or creates a lightweight identity row (name +
 * email + role) if this is the first time this backend has heard of them.
 * This backend has no login/auth of its own — see README.md — so these
 * rows exist purely so classes/memberships have someone to point their
 * foreign keys at, mirroring the same "synced placeholder profile" idea
 * used on the Android side for the same reason.
 */
function get_or_create_user(PDO $pdo, string $fullName, string $email, string $role): int {
    $email = strtolower(trim($email));

    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ?');
    $stmt->execute([$email]);
    $row = $stmt->fetch();
    if ($row) {
        return (int) $row['id'];
    }

    $stmt = $pdo->prepare('INSERT INTO users (full_name, email, role) VALUES (?, ?, ?)');
    $stmt->execute([$fullName !== '' ? $fullName : $email, $email, $role]);
    return (int) $pdo->lastInsertId();
}

/**
 * Move an uploaded temp file into the server attachments directory under a
 * unique name and return the relative path (relative to ATTACHMENTS_DIR) to
 * store in the database. Shared by sync_lesson_attachment.php (and formerly
 * sync_lesson.php, before lesson attachments moved to their own table/
 * endpoint) - lives here rather than in one endpoint file so both can call
 * it without require_once-ing an entire other endpoint's request handling.
 */
function store_uploaded_attachment(string $tmpPath, string $originalName): string {
    ensure_attachments_dir();
    $uniqueName = unique_attachment_name($originalName);
    $dest = ATTACHMENTS_DIR . '/' . $uniqueName;
    if (!move_uploaded_file($tmpPath, $dest)) {
        throw new RuntimeException('Failed to store uploaded attachment.');
    }
    return $uniqueName;
}

/**
 * Extensions an uploaded file may keep. Anything else is stored as ".bin".
 * Before this, any letters-and-digits extension was kept - including ".php",
 * so anyone who knew a teacher's email could upload a script into a folder the
 * web server serves and run it by opening its address. The app is unaffected:
 * it opens a downloaded file using the type kept in the database, not the
 * extension of the stored copy.
 */
const SAFE_ATTACHMENT_EXTENSIONS = [
    'pdf', 'doc', 'docx', 'dot', 'dotx', 'odt', 'rtf', 'txt', 'md', 'csv',
    'ppt', 'pptx', 'pps', 'ppsx', 'odp',
    'xls', 'xlsx', 'ods',
    'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'heif',
    'mp3', 'm4a', 'wav', 'ogg', 'aac',
    'mp4', 'm4v', 'mov', '3gp', 'webm', 'mkv', 'avi',
    'zip',
];

/** The lower-case extension a stored copy of this file may carry ("bin" when it isn't a known-harmless type). */
function safe_attachment_extension(string $originalName): string {
    $extension = strtolower(pathinfo($originalName, PATHINFO_EXTENSION));
    return in_array($extension, SAFE_ATTACHMENT_EXTENSIONS, true) ? $extension : 'bin';
}

/**
 * "<time>_<name>_<random>.<safe extension>". The name keeps letters, digits, "_"
 * and "-" only - dots go too, so "evil.php.jpg" can't leave a ".php" in the
 * middle (some Apache setups run that) - and the random part means two files
 * uploaded in the same second never collide and stored names can't be guessed.
 */
function unique_attachment_name(string $originalName): string {
    $base = preg_replace('/[^a-zA-Z0-9_-]/', '_', pathinfo($originalName, PATHINFO_FILENAME));
    return sprintf('%d_%s_%s.%s',
        time(),
        substr($base, 0, 80),
        bin2hex(random_bytes(4)),
        safe_attachment_extension($originalName)
    );
}
