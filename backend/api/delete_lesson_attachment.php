<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$attachmentId = (int) ($input['attachmentId'] ?? 0);
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($attachmentId === 0 || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    $pdo = get_db_connection();

    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    // Ownership check the same way sync_lesson_attachment.php does, walking
    // attachment -> lesson -> class -> teacher, so one teacher can't delete
    // another's attachment just by guessing an id.
    $stmt = $pdo->prepare(
        'SELECT la.id, la.file_path FROM lesson_attachments la
         JOIN lessons l ON l.id = la.lesson_id
         JOIN classes c ON c.id = l.class_id
         WHERE la.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$attachmentId, $teacher['id']]);
    $attachment = $stmt->fetch();
    if (!$attachment) {
        // Already gone, or never existed, or not owned by this teacher -
        // either way there's nothing left to delete. Not an error: the
        // caller's intent (this attachment should not exist) is satisfied.
        send_json(true, 'Attachment already removed.');
    }

    if (!empty($attachment['file_path'])) {
        $fullPath = ATTACHMENTS_DIR . '/' . $attachment['file_path'];
        if (is_file($fullPath)) {
            unlink($fullPath);
        }
    }

    $stmt = $pdo->prepare('DELETE FROM lesson_attachments WHERE id = ?');
    $stmt->execute([$attachmentId]);
    send_json(true, 'Attachment deleted.');
} catch (Throwable $e) {
    error_log('delete_lesson_attachment.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
