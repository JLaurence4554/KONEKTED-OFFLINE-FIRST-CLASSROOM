<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Server-side counterpart to UserRepository.register() on Android. The app
// already creates the account locally and lets the person straight in
// (offline-first) - this call puts the same account on the server so it can
// be recovered later via login.php (a reinstall, a different device), and it
// returns the login TOKEN every other endpoint now requires (see "Login
// protection" in common.php).
//
// The app also calls this at login for an account that only exists on the
// phone (created offline, or before the server knew about it) - it has the
// password the person just typed, which is what makes the server account.
//
// Thirty or more sign-ups an hour from one address are refused (HTTP 429).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$fullName = trim($input['fullName'] ?? '');
$email    = strtolower(trim($input['email'] ?? ''));
$password = (string) ($input['password'] ?? '');
$role     = trim($input['role'] ?? '');

if ($fullName === '' || $email === '' || $password === '' || !in_array($role, ['Teacher', 'Student'], true)) {
    send_json(false, 'Missing or invalid required fields.');
}
if (strlen($password) < 6) {
    send_json(false, 'Password must be at least 6 characters.');
}

try {
    $pdo = get_db_connection();
    throttle_or_die($pdo, 'register', $email);

    $stmt = $pdo->prepare('SELECT id, password_hash FROM users WHERE email = ?');
    $stmt->execute([$email]);
    $existing = $stmt->fetch();

    // PHP's own password hashing (bcrypt via PASSWORD_DEFAULT) - independent
    // of the Android app's on-device PBKDF2 hash. These are two separate
    // stores for two separate purposes (offline-first local login vs.
    // server-backed recovery), so there's no need for them to match schemes.
    $hash = password_hash($password, PASSWORD_DEFAULT);

    if ($existing) {
        if (!empty($existing['password_hash'])) {
            // A real account already claimed this email - can't silently
            // take it over just because someone posts a matching email.
            record_attempt($pdo, 'register', $email, false);
            send_json(false, 'An account with this email already exists.');
        }
        // A placeholder identity row (email/name cache only, no password) -
        // claim it instead of inserting a second row, since email is UNIQUE.
        $stmt = $pdo->prepare('UPDATE users SET full_name = ?, role = ?, password_hash = ? WHERE id = ?');
        $stmt->execute([$fullName, $role, $hash, $existing['id']]);
        $userId = (int) $existing['id'];
    } else {
        $stmt = $pdo->prepare('INSERT INTO users (full_name, email, role, password_hash) VALUES (?, ?, ?, ?)');
        $stmt->execute([$fullName, $email, $role, $hash]);
        $userId = (int) $pdo->lastInsertId();
    }

    record_attempt($pdo, 'register', $email, true);
    send_json(true, 'Account registered.', [
        'serverId' => $userId,
        'token'    => issue_auth_token($pdo, $userId),
    ]);
} catch (Throwable $e) {
    error_log('register.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
