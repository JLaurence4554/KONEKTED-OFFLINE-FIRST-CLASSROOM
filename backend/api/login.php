<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Server-backed account recovery: UserRepository.login() on Android tries
// the local (offline) database first, and only calls this endpoint when
// there's no local match - i.e. a reinstalled app, or a new device - so an
// account registered via register.php isn't lost with the local install.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$email    = strtolower(trim($input['email'] ?? ''));
$password = (string) ($input['password'] ?? '');

if ($email === '' || $password === '') {
    send_json(false, 'Email and password are required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare('SELECT id, full_name, email, role, password_hash FROM users WHERE email = ?');
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    // A NULL password_hash means this row is only an identity cache (see
    // schema.sql), never an actual registered account - treat the same as
    // "not found" rather than letting anyone log into someone else's name.
    if (!$user || empty($user['password_hash']) || !password_verify($password, $user['password_hash'])) {
        send_json(false, 'Invalid email or password.');
    }

    send_json(true, 'Login successful.', [
        'serverId' => (int) $user['id'],
        'fullName' => $user['full_name'],
        'email'    => $user['email'],
        'role'     => $user['role'],
    ]);
} catch (Throwable $e) {
    error_log('login.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
