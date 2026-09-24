<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Logs in with email + password and hands the app its login TOKEN - the secret
// every other endpoint now requires (see "Login protection" in common.php).
//
// UserRepository.login() on Android checks the local (offline) account first,
// then calls this to get the token, or - when there's no local account at
// all (a reinstalled app, a new phone) - to recover the account from here.
//
// A wrong email and a wrong password get the same answer and take the same
// time, and after too many failures in a row the email is locked for a few
// minutes (HTTP 429), so passwords can't be guessed by trying them one after
// another.

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
    throttle_or_die($pdo, 'login', $email);

    $stmt = $pdo->prepare('SELECT id, full_name, email, role, password_hash, profile_picture FROM users WHERE email = ?');
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    // A NULL password_hash means this row is only an identity cache (see
    // schema.sql), never an actual registered account - treat the same as
    // "not found" rather than letting anyone log into someone else's name.
    // Either way a hash is checked, so the reply takes the same time.
    $hasAccount = $user && !empty($user['password_hash']);
    $passwordOk = password_verify($password, $hasAccount ? $user['password_hash'] : DUMMY_PASSWORD_HASH);
    if (!$hasAccount || !$passwordOk) {
        record_attempt($pdo, 'login', $email, false);
        send_json(false, 'Invalid email or password.');
    }

    forgive_failures($pdo, $email);
    $token = issue_auth_token($pdo, (int) $user['id']);

    send_json(true, 'Login successful.', [
        'serverId' => (int) $user['id'],
        'fullName' => $user['full_name'],
        'email'    => $user['email'],
        'role'     => $user['role'],
        // Every other call has to carry this in "Authorization: Bearer <token>".
        'token'    => $token,
        // So a reinstall or a new phone gets the account's photo back too.
        'profilePictureUrl' => !empty($user['profile_picture'])
            ? public_file_url($user['profile_picture'])
            : null,
    ]);
} catch (Throwable $e) {
    error_log('login.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
