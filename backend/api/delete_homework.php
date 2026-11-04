<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher: delete a homework with every hand-in (and the students' stored files).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input      = read_json_body();
$homeworkId = (int) ($input['homeworkId'] ?? 0);

$pdo = open_authenticated('Teacher', $auth);

if ($homeworkId === 0) {
    send_json(false, 'Missing required fields (homeworkId).');
}

try {
    ensure_homework_schema($pdo);
    ensure_attachments_dir();

    $stmt = $pdo->prepare('SELECT class_id FROM homework WHERE id = ?');
    $stmt->execute([$homeworkId]);
    $homework = $stmt->fetch();
    if (!$homework) {
        // Already gone: nothing to delete.
        send_json(true, 'Homework deleted.', null);
    }
    if (class_role($pdo, $auth, (int) $homework['class_id']) !== 'owner') {
        send_forbidden('You do not own this homework.');
    }

    // Collect the stored files now, remove them only after the rows are gone.
    $stmt = $pdo->prepare('SELECT file_path FROM homework_submissions WHERE homework_id = ? AND file_path IS NOT NULL');
    $stmt->execute([$homeworkId]);
    $files = array_filter(array_column($stmt->fetchAll(), 'file_path'));

    $pdo->prepare('DELETE FROM homework WHERE id = ?')->execute([$homeworkId]); // submissions cascade

    foreach ($files as $path) {
        remove_stored_file($path);
    }

    send_json(true, 'Homework deleted.', null);
} catch (Throwable $e) {
    error_log('delete_homework.php error: ' . $e->getMessage());
    send_json(false, 'Unable to delete the homework.', null, 500);
}
