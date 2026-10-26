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
    $scheme = request_scheme();
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
 * Used for OTHER people who show up in a request (e.g. the student a teacher
 * marks present) and haven't signed up yet: their row exists purely so
 * attendance/memberships have someone to point their foreign keys at,
 * mirroring the same "synced placeholder profile" idea used on the Android
 * side. It has no password, so nobody can log in as it until that person
 * registers (register.php claims the row). Whoever is asking is never
 * looked up this way - that comes from the login token (see below).
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

// ---------------------------------------------------------------------------
// Supabase Storage (optional home for uploads - see KONEKTED_SUPABASE_* in
// config/database.php). Flat object names only (the unique names below), so
// the database columns and the app need no change when the switch is flipped.
// Uses PHP's HTTP stream wrapper, so no curl extension is required.
// ---------------------------------------------------------------------------

/**
 * Header lines that authenticate a Storage request with KONEKTED_SUPABASE_STORAGE_KEY.
 *  - Legacy keys (the long "eyJ..." service_role JWT) work as "Authorization: Bearer" and as "apikey".
 *  - New secret keys ("sb_secret_...") are NOT JWTs: Supabase wants them in the "apikey" header only,
 *    and refuses them as a Bearer token (every upload then fails with 401/403 and nothing is stored).
 * Ends with "\r\n" so more header lines can follow.
 */
function storage_auth_headers(): string {
    $headers = 'apikey: ' . SUPABASE_STORAGE_KEY . "\r\n";
    if (strpos(SUPABASE_STORAGE_KEY, 'sb_') !== 0) {
        $headers .= 'Authorization: Bearer ' . SUPABASE_STORAGE_KEY . "\r\n";
    }
    return $headers;
}

/** First bytes of a Storage error reply (Supabase's own explanation, e.g. "Invalid Compact JWS"), safe to log. */
function storage_error_detail($body): string {
    if (!is_string($body) || $body === '') {
        return '';
    }
    return ': ' . substr(preg_replace('/\s+/', ' ', $body), 0, 200);
}

/** HTTPS URL of one object in the private bucket. */
function storage_object_url(string $name): string {
    return SUPABASE_URL . '/storage/v1/object/' . rawurlencode(STORAGE_BUCKET) . '/' . rawurlencode($name);
}

/** Create the bucket once per request if it is missing (best effort: "already exists" is the happy path). */
function storage_ensure_bucket(): void {
    static $done = false;
    if ($done) {
        return;
    }
    $done = true;
    $ctx = stream_context_create(['http' => [
        'method'  => 'POST',
        'header'  => storage_auth_headers() . "Content-Type: application/json\r\n",
        'content' => json_encode(['name' => STORAGE_BUCKET, 'public' => false]),
        'ignore_errors' => true,
        'timeout' => 15,
    ]]);
    //noinspection PhpUsageOfSuppressOperator
    @file_get_contents(SUPABASE_URL . '/storage/v1/bucket', false, $ctx);
}

/**
 * Copy a local file into Supabase Storage under $name (an existing object of
 * that name is replaced). Throws on failure, so an upload is never reported
 * as saved when it isn't.
 */
function storage_put(string $name, string $localPath): void {
    storage_ensure_bucket();
    $bytes = file_get_contents($localPath);
    if ($bytes === false) {
        throw new RuntimeException('storage_put: cannot read ' . $localPath);
    }
    $ctx = stream_context_create(['http' => [
        'method'  => 'POST',
        'header'  => storage_auth_headers()
                   . "Content-Type: application/octet-stream\r\n"
                   . "x-upsert: true\r\n",
        'content' => $bytes,
        'ignore_errors' => true,
        'timeout' => 30,
    ]]);
    //noinspection PhpUsageOfSuppressOperator
    $result = @file_get_contents(storage_object_url($name), false, $ctx);
    $status = (int) (explode(' ', $http_response_header[0] ?? ' ')[1] ?? 0);
    if ($result === false || $status < 200 || $status >= 300) {
        throw new RuntimeException('storage_put failed (HTTP ' . $status . ')' . storage_error_detail($result));
    }
}

/** Best-effort delete of one object (a missing object is already the wanted state). */
function storage_delete(string $name): void {
    $ctx = stream_context_create(['http' => [
        'method'  => 'DELETE',
        'header'  => storage_auth_headers(),
        'ignore_errors' => true,
        'timeout' => 15,
    ]]);
    //noinspection PhpUsageOfSuppressOperator
    @file_get_contents(storage_object_url($name), false, $ctx);
}

/**
 * Fetch an object: ['body' => string, 'contentType' => string], or null when
 * no object of that name exists.
 */
function storage_get(string $name): ?array {
    $ctx = stream_context_create(['http' => [
        'method'  => 'GET',
        'header'  => storage_auth_headers(),
        'ignore_errors' => true,
        'timeout' => 30,
    ]]);
    //noinspection PhpUsageOfSuppressOperator
    $body = @file_get_contents(storage_object_url($name), false, $ctx);
    $status = (int) (explode(' ', $http_response_header[0] ?? ' ')[1] ?? 0);
    if ($body === false || $status === 404) {
        return null;
    }
    if ($status < 200 || $status >= 300) {
        throw new RuntimeException('storage_get failed (HTTP ' . $status . ')' . storage_error_detail($body));
    }
    $contentType = 'application/octet-stream';
    foreach ($http_response_header ?? [] as $headerLine) {
        if (stripos($headerLine, 'content-type:') === 0) {
            $contentType = trim(substr($headerLine, 13));
            break;
        }
    }
    return ['body' => $body, 'contentType' => $contentType];
}

/**
 * Remove a file wherever it lives: Supabase Storage when that is enabled,
 * and always this server's disk (harmlessly absent when storage is used).
 * The name is checked first, so it can never escape the attachments folder.
 */
function remove_stored_file(string $name): void {
    if (strpos($name, '..') !== false || strpos($name, '/') !== false || strpos($name, '\\') !== false) {
        return;
    }
    if (storage_enabled()) {
        try {
            storage_delete($name);
        } catch (Throwable $e) {
            error_log('remove_stored_file: ' . $e->getMessage());
        }
    }
    $local = ATTACHMENTS_DIR . '/' . $name;
    if (is_file($local)) {
        //noinspection PhpUsageOfSuppressOperator
        @unlink($local);
    }
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
    if (storage_enabled()) {
        try {
            storage_put($uniqueName, $dest);
        } catch (Throwable $e) {
            // Never keep a half-saved upload: drop the local copy too and let
            // the endpoint report the failure to the app (it keeps its own
            // copy and will retry, offline-first as usual).
            //noinspection PhpUsageOfSuppressOperator
            @unlink($dest);
            throw $e;
        }
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

// ===========================================================================
// Login protection
// ===========================================================================
//
// Before this, every endpoint trusted whatever email the request named, so
// anyone who could reach the server could act as any teacher or student.
// Now login.php / register.php hand the app a random secret TOKEN, and every
// other endpoint starts with open_authenticated(): the caller is whoever the
// token belongs to - an email typed into a request no longer counts.
//
// Tokens are 32 random bytes; only their SHA-256 is stored, so a leaked
// database doesn't leak working tokens. A token lasts 90 days from its last
// use, is deleted by logout.php, and one person can be logged in on several
// phones at once (one token per phone, newest 10 kept).
//
// HTTP status codes: 401 = no valid token (the app asks the person to log in
// again), 403 = logged in but not allowed to do this, 429 = too many login
// attempts. Everything else keeps the old convention (200 + success:false).

const AUTH_TOKEN_DAYS = 90;
const MAX_TOKENS_PER_USER = 10;
const LOGIN_WINDOW_MINUTES = 15;
const LOGIN_MAX_FAILURES_PER_EMAIL = 8;
const LOGIN_MAX_FAILURES_PER_IP = 100;
const REGISTER_WINDOW_MINUTES = 60;
define('REGISTER_MAX_PER_IP', konekted_int('KONEKTED_REGISTER_MAX_PER_IP', 30));

/** A real bcrypt hash of nothing anyone knows: checked against when the email doesn't exist, so a wrong email takes as long as a wrong password. */
const DUMMY_PASSWORD_HASH = '$2y$10$RDxinXMmUDPpZA38LQ0ZM.zxXLS4A8LZUcEI.9mnPOakX7xRA3rf2';

/** "https" or "http" as the visitor reached us (ngrok and other proxies report it in X-Forwarded-Proto). */
function request_scheme(): string {
    if (!empty($_SERVER['HTTP_X_FORWARDED_PROTO'])) {
        return strtolower(trim(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'])[0]));
    }
    if (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') {
        return 'https';
    }
    return 'http';
}

/** With KONEKTED_REQUIRE_HTTPS=1 (see config/database.php), plain-HTTP requests are refused: tokens and passwords must not travel in the clear. */
function enforce_https(): void {
    if (defined('REQUIRE_HTTPS') && REQUIRE_HTTPS && request_scheme() !== 'https') {
        send_json(false, 'HTTPS is required.', ['code' => 'HTTPS_REQUIRED'], 426);
    }
}
enforce_https();

/** The visitor's address, for counting login attempts. X-Forwarded-For is only believed when KONEKTED_TRUST_PROXY=1. */
function client_ip(): string {
    if (defined('TRUST_PROXY_HEADERS') && TRUST_PROXY_HEADERS && !empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $first = trim(explode(',', $_SERVER['HTTP_X_FORWARDED_FOR'])[0]);
        if (filter_var($first, FILTER_VALIDATE_IP)) {
            return $first;
        }
    }
    return (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
}

function send_forbidden(string $message): void {
    send_json(false, $message, ['code' => 'FORBIDDEN'], 403);
}

/**
 * The token sent with the request: "Authorization: Bearer <token>" (or the
 * X-Auth-Token header, for hosts that strip Authorization). Null when missing
 * or not shaped like one of our tokens.
 */
function bearer_token(): ?string {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if ($header === '' && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strcasecmp($name, 'Authorization') === 0) {
                $header = (string) $value;
                break;
            }
        }
    }
    if (preg_match('/^Bearer\s+(\S+)$/i', trim($header), $m)) {
        $token = $m[1];
    } else {
        $token = trim((string) ($_SERVER['HTTP_X_AUTH_TOKEN'] ?? ''));
    }
    return preg_match('/^[A-Za-z0-9_\-]{20,200}$/', $token) ? $token : null;
}

/**
 * Creates the login-protection tables if they're missing. Same statements as
 * in schema.sql, so a server that was updated without re-running the schema
 * still works: the first login creates them.
 */
function ensure_auth_schema(PDO $pdo): void {
    static $done = false;
    if ($done) {
        return;
    }
    $pdo->exec("CREATE TABLE IF NOT EXISTS auth_tokens (
        id BIGSERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        token_hash CHAR(64) NOT NULL UNIQUE,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        last_used_at TIMESTAMP NOT NULL DEFAULT NOW(),
        expires_at TIMESTAMP NOT NULL
    )");
    $pdo->exec("CREATE INDEX IF NOT EXISTS idx_auth_tokens_user ON auth_tokens (user_id)");
    $pdo->exec("CREATE TABLE IF NOT EXISTS login_attempts (
        id BIGSERIAL PRIMARY KEY,
        kind VARCHAR(10) NOT NULL,
        email VARCHAR(190),
        ip VARCHAR(64),
        succeeded BOOLEAN NOT NULL,
        attempted_at TIMESTAMP NOT NULL DEFAULT NOW()
    )");
    $pdo->exec("CREATE INDEX IF NOT EXISTS idx_login_attempts_email ON login_attempts (kind, email, attempted_at)");
    $pdo->exec("CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON login_attempts (kind, ip, attempted_at)");
    $done = true;
}

/** Gives this user a new login token (plain text, shown to the app once; only its hash is kept). */
function issue_auth_token(PDO $pdo, int $userId): string {
    ensure_auth_schema($pdo);
    $pdo->exec('DELETE FROM auth_tokens WHERE expires_at < NOW()');

    $token = 'kt_' . bin2hex(random_bytes(32));
    $stmt = $pdo->prepare(
        "INSERT INTO auth_tokens (user_id, token_hash, expires_at)
         VALUES (?, ?, NOW() + (?::integer * INTERVAL '1 day'))"
    );
    $stmt->execute([$userId, hash('sha256', $token), AUTH_TOKEN_DAYS]);

    // Keep only this person's newest tokens (one per phone they're logged in on).
    $stmt = $pdo->prepare(
        'DELETE FROM auth_tokens WHERE user_id = ? AND id NOT IN (
             SELECT id FROM auth_tokens WHERE user_id = ? ORDER BY id DESC LIMIT ?
         )'
    );
    $stmt->execute([$userId, $userId, MAX_TOKENS_PER_USER]);
    return $token;
}

/** Who the request's token belongs to (id, full_name, email, role, token_id), or null when there's no valid token. */
function authenticate(PDO $pdo): ?array {
    $token = bearer_token();
    if ($token === null) {
        return null;
    }
    try {
        $stmt = $pdo->prepare(
            'SELECT t.id AS token_id, u.id, u.full_name, u.email, u.role
             FROM auth_tokens t
             INNER JOIN users u ON u.id = t.user_id
             WHERE t.token_hash = ? AND t.expires_at > NOW()'
        );
        $stmt->execute([hash('sha256', $token)]);
        $row = $stmt->fetch();
    } catch (PDOException $e) {
        if ($e->getCode() === '42P01') {
            return null; // the auth tables don't exist yet, so nobody has logged in
        }
        throw $e;
    }
    if (!$row) {
        return null;
    }

    // Sliding expiry: every use pushes the token's end date out, written at most once an hour.
    $pdo->prepare(
        "UPDATE auth_tokens SET last_used_at = NOW(), expires_at = NOW() + (?::integer * INTERVAL '1 day')
         WHERE id = ? AND last_used_at < NOW() - INTERVAL '1 hour'"
    )->execute([AUTH_TOKEN_DAYS, $row['token_id']]);

    return [
        'token_id'  => (int) $row['token_id'],
        'id'        => (int) $row['id'],
        'full_name' => $row['full_name'],
        'email'     => strtolower($row['email']),
        'role'      => $row['role'],
    ];
}

/** The logged-in user, or a 401 (no valid token) / 403 (wrong role) reply. $role is 'Teacher', 'Student', or null for either. */
function require_auth(PDO $pdo, ?string $role = null): array {
    $auth = authenticate($pdo);
    if ($auth === null) {
        send_json(false, 'Please log in again.', ['code' => 'AUTH_REQUIRED'], 401);
    }
    if ($role !== null && $auth['role'] !== $role) {
        send_json(false, 'This action is only for ' . strtolower($role) . 's.', ['code' => 'WRONG_ROLE'], 403);
    }
    return $auth;
}

/**
 * What every protected endpoint starts with: opens the database, then makes
 * sure the caller is logged in (and has the given role). Returns the
 * connection and puts the logged-in user in $auth.
 */
function open_authenticated(?string $role, &$auth): PDO {
    try {
        $pdo = get_db_connection();
    } catch (Throwable $e) {
        error_log('database connection failed: ' . $e->getMessage());
        send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
    }
    try {
        $auth = require_auth($pdo, $role);
    } catch (PDOException $e) {
        error_log('authentication failed: ' . $e->getMessage());
        send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
    }
    return $pdo;
}

/**
 * Requests still carry the sender's email (teacherEmail, studentEmail...) from
 * before tokens existed. It's no longer used to decide who is asking, but if
 * one is present and isn't the logged-in account's, something is wrong (a bug,
 * or someone using another person's email with their own token) - refuse.
 */
function assert_same_account(array $auth, $emailInRequest): void {
    $email = strtolower(trim((string) $emailInRequest));
    if ($email !== '' && $email !== $auth['email']) {
        send_json(false, 'This request names a different account than the one you are logged in as.',
            ['code' => 'ACCOUNT_MISMATCH'], 403);
    }
}

/** 'owner' if the logged-in teacher owns this class, 'member' if the logged-in student belongs to it, else null. */
function class_role(PDO $pdo, array $auth, int $classId): ?string {
    if ($auth['role'] === 'Teacher') {
        $stmt = $pdo->prepare('SELECT 1 FROM classes WHERE id = ? AND teacher_id = ?');
        $stmt->execute([$classId, $auth['id']]);
        return $stmt->fetch() ? 'owner' : null;
    }
    $stmt = $pdo->prepare('SELECT 1 FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $auth['id']]);
    return $stmt->fetch() ? 'member' : null;
}

/**
 * Whether the logged-in person may download this stored file. A profile
 * picture may be seen by anyone logged in; a lesson attachment only by its
 * class's teacher and the students in that class. A file nothing points to
 * is nobody's to download.
 */
function may_download_file(PDO $pdo, array $auth, string $storedName): bool {
    $stmt = $pdo->prepare('SELECT 1 FROM users WHERE profile_picture = ?');
    $stmt->execute([$storedName]);
    if ($stmt->fetch()) {
        return true;
    }

    $stmt = $pdo->prepare(
        'SELECT l.class_id FROM lesson_attachments la
         JOIN lessons l ON l.id = la.lesson_id
         WHERE la.file_path = ?'
    );
    $stmt->execute([$storedName]);
    $row = $stmt->fetch();
    if (!$row) {
        // Older lessons kept a single attachment on the lesson row itself.
        $stmt = $pdo->prepare('SELECT class_id FROM lessons WHERE attachment_path = ?');
        $stmt->execute([$storedName]);
        $row = $stmt->fetch();
    }
    return $row ? class_role($pdo, $auth, (int) $row['class_id']) !== null : false;
}

// ---- Limiting login attempts (login.php / register.php) --------------------

function attempts_in_window(PDO $pdo, string $kind, string $column, string $value, int $minutes, bool $failuresOnly): int {
    if (!in_array($column, ['email', 'ip'], true)) {
        throw new InvalidArgumentException('Unknown column.');
    }
    $sql = "SELECT COUNT(*) FROM login_attempts
            WHERE kind = ? AND {$column} = ? AND attempted_at > NOW() - (?::integer * INTERVAL '1 minute')"
         . ($failuresOnly ? ' AND succeeded = FALSE' : '');
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$kind, $value, $minutes]);
    return (int) $stmt->fetchColumn();
}

function record_attempt(PDO $pdo, string $kind, string $email, bool $succeeded): void {
    $stmt = $pdo->prepare('INSERT INTO login_attempts (kind, email, ip, succeeded) VALUES (?, ?, ?, ?)');
    $stmt->execute([$kind, $email, client_ip(), $succeeded ? 't' : 'f']);
}

/** A correct login wipes that email's earlier failures, so someone who mistyped a few times isn't locked out. */
function forgive_failures(PDO $pdo, string $email): void {
    $stmt = $pdo->prepare("DELETE FROM login_attempts WHERE kind = 'login' AND email = ? AND succeeded = FALSE");
    $stmt->execute([$email]);
}

/** 429 when this email (or address) has failed to log in too often lately, or one address is creating too many accounts. */
function throttle_or_die(PDO $pdo, string $kind, string $email): void {
    ensure_auth_schema($pdo);
    $pdo->exec("DELETE FROM login_attempts WHERE attempted_at < NOW() - INTERVAL '1 day'");
    $ip = client_ip();

    if ($kind === 'login') {
        $tooMany = attempts_in_window($pdo, 'login', 'email', $email, LOGIN_WINDOW_MINUTES, true) >= LOGIN_MAX_FAILURES_PER_EMAIL
                || attempts_in_window($pdo, 'login', 'ip', $ip, LOGIN_WINDOW_MINUTES, true) >= LOGIN_MAX_FAILURES_PER_IP;
    } else {
        $tooMany = attempts_in_window($pdo, 'register', 'ip', $ip, REGISTER_WINDOW_MINUTES, false) >= REGISTER_MAX_PER_IP;
    }
    if ($tooMany) {
        send_json(false, 'Too many attempts. Please wait a few minutes and try again.',
            ['code' => 'RATE_LIMITED'], 429);
    }
}
