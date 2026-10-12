<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$attachmentId = (int) ($input['attachmentId'] ?? 0);

// The teacher is the logged-in account (see "Login protection" in common.php);
// a teacherEmail in the request is only checked against it.
$pdo = open_authenticated('Teacher', $auth);
assert_same_account($auth, $input['teacherEmail'] ?? '');

if ($attachmentId === 0) {
    send_json(false, 'Missing required fields.');
}

try {

    // Ownership check the same way sync_lesson_attachment.php does, walking
    // attachment -> lesson -> class -> teacher, so one teacher can't delete
    // another's attachment just by guessing an id.
    $stmt = $pdo->prepare(
        'SELECT la.id, la.file_path FROM lesson_attachments la
         JOIN lessons l ON l.id = la.lesson_id
         JOIN classes c ON c.id = l.class_id
         WHERE la.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$attachmentId, $auth['id']]);
    $attachment = $stmt->fetch();
    if (!$attachment) {
        // Already gone, or never existed, or not owned by this teacher -
        // either way there's nothing left to delete. Not an error: the
        // caller's intent (this attachment should not exist) is satisfied.
        send_json(true, 'Attachment already removed.');
    }

    if (!empty($attachment['file_path'])) {
        remove_stored_file($attachment['file_path']);
    }

    $stmt = $pdo->prepare('DELETE FROM lesson_attachments WHERE id = ?');
    $stmt->execute([$attachmentId]);
    send_json(true, 'Attachment deleted.');
} catch (Throwable $e) {
    error_log('delete_lesson_attachment.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
