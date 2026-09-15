<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Plain text-fields sync only - image/file/video attachments moved to their
// own table (lesson_attachments) and endpoint (sync_lesson_attachment.php)
// as part of the multi-attachment redesign, so a lesson can carry any
// number of attachments of any mix of types instead of exactly one image +
// one file + one video link. This endpoint no longer accepts multipart
// bodies at all - every caller sends plain JSON now.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$classId      = (int) ($input['classId'] ?? 0);
$lessonId     = (int) ($input['lessonId'] ?? 0);
$title        = trim($input['title'] ?? '');
$body         = trim($input['body'] ?? '');
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($classId === 0 || $title === '' || $body === '' || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    $pdo = get_db_connection();

    // Verify the teacher owns this class
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare('SELECT id FROM classes WHERE id = ? AND teacher_id = ?');
    $stmt->execute([$classId, $teacher['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'You do not own this class.');
    }

    // Upsert: prefer matching by the lesson's own server id when the client
    // already knows it (an edit of a previously-synced lesson) - this is the
    // only way to correctly target the right row if the title itself changed.
    // Fall back to matching by class_id+title only for a genuinely new lesson
    // (lessonId == 0), so a retried create doesn't produce a duplicate row.
    if ($lessonId > 0) {
        $stmt = $pdo->prepare('SELECT id FROM lessons WHERE id = ? AND class_id = ?');
        $stmt->execute([$lessonId, $classId]);
    } else {
        $stmt = $pdo->prepare('SELECT id FROM lessons WHERE class_id = ? AND title = ?');
        $stmt->execute([$classId, $title]);
    }
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare('UPDATE lessons SET title = ?, body = ? WHERE id = ?');
        $stmt->execute([$title, $body, $existing['id']]);
        send_json(true, 'Lesson updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    // subject is still NOT NULL on the lessons table - send an empty string
    // rather than migrating the column away for one now-unused field.
    $stmt = $pdo->prepare(
        'INSERT INTO lessons (class_id, title, body, subject) VALUES (?, ?, ?, ?)'
    );
    $stmt->execute([$classId, $title, $body, '']);
    send_json(true, 'Lesson synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_lesson.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
