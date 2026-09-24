<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// One call per attachment - a lesson can have any number of these, added
// one at a time from the app's "+ Add Attachment" flow (see
// LessonRepository.pushLessonAttachment on the Android side). Mirrors
// sync_lesson.php's old dual-mode shape: multipart when a file is attached
// (IMAGE/FILE), plain JSON when it's just a link (VIDEO_LINK).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

// The teacher is the logged-in account (see "Login protection" in common.php);
// checked first, so a caller without a login gets a 401 whatever else is wrong.
$pdo = open_authenticated('Teacher', $auth);

$hasFile = isset($_FILES['file']) && $_FILES['file']['error'] === UPLOAD_ERR_OK;

if ($hasFile) {
    $lessonId     = (int) ($_POST['lessonId'] ?? 0);
    $type         = trim($_POST['type'] ?? '');
    $name         = trim($_POST['name'] ?? '');
    $teacherEmail = trim($_POST['teacherEmail'] ?? '');
    $mimeType     = trim($_FILES['file']['type'] ?: 'application/octet-stream');
} else {
    $input        = read_json_body();
    $lessonId     = (int) ($input['lessonId'] ?? 0);
    $type         = trim($input['type'] ?? '');
    $name         = trim($input['name'] ?? '');
    $teacherEmail = trim($input['teacherEmail'] ?? '');
    $mimeType     = null;
}

// A teacherEmail in the request is only checked against the logged-in account.
assert_same_account($auth, $teacherEmail);

if ($lessonId === 0 || !in_array($type, ['IMAGE', 'FILE', 'VIDEO_LINK'], true)
        || $name === '') {
    send_json(false, 'Missing or invalid required fields.');
}
if ($type === 'VIDEO_LINK' && $hasFile) {
    send_json(false, 'A video link should not include a file.');
}
if ($type !== 'VIDEO_LINK' && !$hasFile) {
    send_json(false, 'A file is required for this attachment type.');
}

try {
    // Verify the teacher owns the class this lesson belongs to.
    $stmt = $pdo->prepare(
        'SELECT l.id FROM lessons l
         JOIN classes c ON c.id = l.class_id
         WHERE l.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$lessonId, $auth['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'You do not own this lesson.');
    }

    $filePath = null;
    if ($hasFile) {
        ensure_attachments_dir();
        $filePath = store_uploaded_attachment($_FILES['file']['tmp_name'], $name);
    }

    $stmt = $pdo->prepare(
        'INSERT INTO lesson_attachments (lesson_id, type, file_path, name, mime_type)
         VALUES (?, ?, ?, ?, ?)'
    );
    $stmt->execute([$lessonId, $type, $filePath, $name, $mimeType]);
    send_json(true, 'Attachment synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_lesson_attachment.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
