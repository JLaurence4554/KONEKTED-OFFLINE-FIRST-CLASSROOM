<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Ends this phone's login: the token it was using stops working at once, so
// a lost or handed-on phone can't keep reading the account's data after the
// person logs out. {"allDevices": true} ends every phone's login for the
// account instead (e.g. after a password change).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$pdo = open_authenticated(null, $auth);
$input = read_json_body();

try {
    if (!empty($input['allDevices'])) {
        $stmt = $pdo->prepare('DELETE FROM auth_tokens WHERE user_id = ?');
        $stmt->execute([$auth['id']]);
    } else {
        $stmt = $pdo->prepare('DELETE FROM auth_tokens WHERE id = ?');
        $stmt->execute([$auth['token_id']]);
    }
    send_json(true, 'Logged out.');
} catch (Throwable $e) {
    error_log('logout.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
