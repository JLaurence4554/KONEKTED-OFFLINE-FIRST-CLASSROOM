<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher: create (or edit) a homework for one of their classes.
// clientKey is made by the teacher's phone: sending the same homework twice
// (the reply was lost) finds the first one instead of posting it again, and
// sending it again with changed text edits it.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$classId      = (int) ($input['classId'] ?? 0);
$lessonId     = (int) ($input['lessonId'] ?? 0);
$title       = trim($input['title'] ?? '');
$instructions = trim($input['instructions'] ?? '');
$dueAt        = isset($input['dueAt']) && $input['dueAt'] !== null ? (int) $input['dueAt'] : null;
$createdAt    = (int) ($input['createdAt'] ?? 0);
$clientKey    = trim($input['clientKey'] ?? '');

$pdo = open_authenticated('Teacher', $auth);

if ($classId === 0 || $title === '' || $clientKey === '' || strlen($clientKey) > 64) {
    send_json(false, 'Missing required fields.');
}
if (mb_strlen($title) > 120 || mb_strlen($instructions) > 2000) {
    send_json(false, 'The homework is too long.');
}
if ($dueAt !== null && $dueAt <= 0) {
    $dueAt = null;
}
if ($createdAt <= 0) {
    $createdAt = now_ms();
}

try {
    ensure_homework_schema($pdo);

    if (class_role($pdo, $auth, $classId) !== 'owner') {
        send_forbidden('You do not own this class.');
    }

    // The lesson (if any) must be one of this class's lessons.
    $lessonValue = null;
    if ($lessonId > 0) {
        $stmt = $pdo->prepare('SELECT id FROM lessons WHERE id = ? AND class_id = ?');
        $stmt->execute([$lessonId, $classId]);
        if (!$stmt->fetch()) {
            send_json(false, 'That lesson is not in this class.');
        }
        $lessonValue = $lessonId;
    }

    $stmt = $pdo->prepare('SELECT id FROM homework WHERE class_id = ? AND client_key = ?');
    $stmt->execute([$classId, $clientKey]);
    $existing = $stmt->fetch();
    if ($existing) {
        $stmt = $pdo->prepare('UPDATE homework SET title = ?, instructions = ?, due_at = ?, lesson_id = ? WHERE id = ?');
        $stmt->execute([$title, $instructions, $dueAt, $lessonValue, $existing['id']]);
        send_json(true, 'Homework updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO homework (class_id, lesson_id, teacher_id, title, instructions, due_at, client_key, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $stmt->execute([$classId, $lessonValue, $auth['id'], $title, $instructions, $dueAt, $clientKey, $createdAt]);
    send_json(true, 'Homework posted.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_homework.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
