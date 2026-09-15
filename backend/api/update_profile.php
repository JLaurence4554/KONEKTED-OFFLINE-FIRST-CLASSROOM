<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Server-side counterpart to UserRepository.updateProfile() on Android.
// The app updates its own local row first (offline-first, same as every
// other entity in this project) and calls this only to keep the
// server-backed copy in sync, so a name change follows the account through
// login.php's account-recovery path too (reinstall, new device).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input    = read_json_body();
$email    = strtolower(trim($input['email'] ?? ''));
$fullName = trim($input['fullName'] ?? '');

if ($email === '' || $fullName === '') {
    send_json(false, 'Email and full name are required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare('SELECT id, password_hash FROM users WHERE email = ?');
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    // Same rule login.php uses: a NULL password_hash means this row is only
    // an identity cache (see schema.sql), never an actual registered
    // account - nothing to update.
    if (!$user || empty($user['password_hash'])) {
        send_json(false, 'Account not found.');
    }

    $stmt = $pdo->prepare('UPDATE users SET full_name = ? WHERE id = ?');
    $stmt->execute([$fullName, $user['id']]);

    send_json(true, 'Profile updated.', ['serverId' => (int) $user['id']]);
} catch (Throwable $e) {
    error_log('update_profile.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
