<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input     = read_json_body();
$classId   = (int) ($input['classId'] ?? 0);
$title     = trim($input['title'] ?? '');
$message   = trim($input['message'] ?? '');
$clientKey = trim($input['clientKey'] ?? '');

// Only the logged-in teacher of the class may post (see "Login protection" in common.php).
$pdo = open_authenticated('Teacher', $auth);

if ($classId === 0 || $title === '' || $message === '' || $clientKey === '' || strlen($clientKey) > 64) {
    send_json(false, 'Missing required fields.');
}
if (mb_strlen($title) > 120 || mb_strlen($message) > 1000) {
    send_json(false, 'The announcement is too long.');
}

try {
    ensure_announcements_schema($pdo);

    if (class_role($pdo, $auth, $classId) !== 'owner') {
        send_forbidden('You do not own this class.');
    }

    // Sent before (the reply never reached the phone)? Answer with the first one.
    $stmt = $pdo->prepare('SELECT id FROM announcements WHERE class_id = ? AND client_key = ?');
    $stmt->execute([$classId, $clientKey]);
    $existing = $stmt->fetch();
    if ($existing) {
        send_json(true, 'Announcement already posted.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO announcements (class_id, teacher_id, title, message, client_key)
         VALUES (?, ?, ?, ?, ?)'
    );
    $stmt->execute([$classId, $auth['id'], $title, $message, $clientKey]);
    send_json(true, 'Announcement posted.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_announcement.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
