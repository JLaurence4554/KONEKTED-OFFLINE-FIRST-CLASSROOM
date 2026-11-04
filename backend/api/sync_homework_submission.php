<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Student: hand in (or replace) work for a homework - multipart POST with the
// fields homeworkId, studentEmail, note, submittedAt (ms) and an optional
// "file" (max 20 MB). One hand-in per student: sending again replaces it, until
// the teacher has graded it. The student is the logged-in account; the grade
// and feedback can never be set from here (grade_homework_submission.php).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$maxBytes = 20 * 1024 * 1024;

// A body bigger than post_max_size arrives with $_POST and $_FILES both empty.
if (empty($_POST) && empty($_FILES) && (int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) {
    send_json(false, 'The file is too large (max 20 MB).');
}

$pdo = open_authenticated('Student', $auth);
assert_same_account($auth, $_POST['studentEmail'] ?? '');

$homeworkId  = (int) ($_POST['homeworkId'] ?? 0);
$note        = trim($_POST['note'] ?? '');
$submittedAt = (int) ($_POST['submittedAt'] ?? 0);
if ($submittedAt <= 0 || $submittedAt > now_ms() + 60000) {
    $submittedAt = now_ms();
}

$hasFile = isset($_FILES['file']) && $_FILES['file']['error'] !== UPLOAD_ERR_NO_FILE;
if ($hasFile) {
    $err = $_FILES['file']['error'];
    if ($err === UPLOAD_ERR_INI_SIZE || $err === UPLOAD_ERR_FORM_SIZE || $_FILES['file']['size'] > $maxBytes) {
        send_json(false, 'The file is too large (max 20 MB).');
    }
    if ($err !== UPLOAD_ERR_OK) {
        send_json(false, 'The file could not be uploaded. Please try again.');
    }
}

if ($homeworkId === 0 || (!$hasFile && $note === '')) {
    send_json(false, 'Missing required fields.');
}
if (mb_strlen($note) > 1000) {
    send_json(false, 'The note is too long.');
}

$newName = null;
try {
    ensure_homework_schema($pdo);
    ensure_attachments_dir();

    $stmt = $pdo->prepare('SELECT id, class_id FROM homework WHERE id = ?');
    $stmt->execute([$homeworkId]);
    $homework = $stmt->fetch();
    if (!$homework || class_role($pdo, $auth, (int) $homework['class_id']) !== 'member') {
        send_forbidden('You do not have access to this homework.');
    }

    $stmt = $pdo->prepare(
        'SELECT id, file_path, file_name, mime_type, grade FROM homework_submissions
         WHERE homework_id = ? AND student_id = ?'
    );
    $stmt->execute([$homeworkId, $auth['id']]);
    $existing = $stmt->fetch();
    if ($existing && $existing['grade'] !== null) {
        send_json(false, 'This homework was already graded, so it can no longer be changed.');
    }

    $fileName = $existing['file_name'] ?? null;
    $mime     = $existing['mime_type'] ?? null;
    $path     = $existing['file_path'] ?? null;
    if ($hasFile) {
        // Trust the file's actual bytes, not the type the client claimed.
        $mime     = finfo_file(finfo_open(FILEINFO_MIME_TYPE), $_FILES['file']['tmp_name']) ?: 'application/octet-stream';
        $fileName = substr(basename($_FILES['file']['name']), 0, 200);
        $newName  = store_uploaded_attachment($_FILES['file']['tmp_name'], $fileName);
        $path     = $newName;
    }

    if ($existing) {
        $stmt = $pdo->prepare(
            'UPDATE homework_submissions
             SET note = ?, file_path = ?, file_name = ?, mime_type = ?, submitted_at = ?
             WHERE id = ?'
        );
        $stmt->execute([$note, $path, $fileName, $mime, $submittedAt, $existing['id']]);
        $serverId = (int) $existing['id'];
    } else {
        $stmt = $pdo->prepare(
            'INSERT INTO homework_submissions (homework_id, student_id, note, file_path, file_name, mime_type, submitted_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([$homeworkId, $auth['id'], $note, $path, $fileName, $mime, $submittedAt]);
        $serverId = (int) $pdo->lastInsertId();
    }

    // Only now that the row points at the new file, drop the one it replaced.
    $old = $existing['file_path'] ?? null;
    if ($newName !== null && !empty($old) && $old !== $newName && strpos($old, '/') === false
            && strpos($old, '\\') === false && strpos($old, '..') === false) {
        remove_stored_file($old);
    }

    send_json(true, 'Homework handed in.', [
        'serverId' => $serverId,
        'fileUrl'  => !empty($path) ? public_file_url($path) : null,
    ]);
} catch (Throwable $e) {
    // Don't leave a stored file behind that no row points to.
    if ($newName !== null) {
        remove_stored_file($newName);
    }
    error_log('sync_homework_submission.php error: ' . $e->getMessage());
    send_json(false, 'Unable to save your homework. Please try again.', null, 500);
}
