<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Server-side counterpart to UserRepository.updateProfile() on Android.
// The app updates its own local row first (offline-first, same as every
// other entity in this project) and calls this only to keep the
// server-backed copy in sync, so a name change follows the account through
// login.php's account-recovery path too (reinstall, new device).
//
// It changes the logged-in account's name (see "Login protection" in
// common.php); an email in the request is only checked against that account.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$pdo = open_authenticated(null, $auth);

$input    = read_json_body();
$fullName = trim($input['fullName'] ?? '');
assert_same_account($auth, $input['email'] ?? '');

if ($fullName === '') {
    send_json(false, 'Full name is required.');
}

try {
    $stmt = $pdo->prepare('UPDATE users SET full_name = ? WHERE id = ?');
    $stmt->execute([$fullName, $auth['id']]);

    send_json(true, 'Profile updated.', ['serverId' => $auth['id']]);
} catch (Throwable $e) {
    error_log('update_profile.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
