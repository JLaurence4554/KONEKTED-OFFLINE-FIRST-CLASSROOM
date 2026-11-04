<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$lessonId    = (int) ($input['lessonId'] ?? 0);

// The teacher is the logged-in account (see "Login protection" in common.php);
// a teacherEmail in the request is only checked against it.
$pdo = open_authenticated('Teacher', $auth);
assert_same_account($auth, $input['teacherEmail'] ?? '');

if ($lessonId === 0) {
    send_json(false, 'Missing required fields.');
}

try {
    ensure_attachments_dir();

    // Only the owning teacher may delete the lesson.
    $stmt = $pdo->prepare(
        'SELECT l.id, l.attachment_path, l.class_id
         FROM lessons l
         JOIN classes c ON c.id = l.class_id
         WHERE l.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$lessonId, $auth['id']]);
    $lesson = $stmt->fetch();
    if (!$lesson) {
        send_json(false, 'Lesson not found or not owned by you.');
    }

    // Collect the stored files (Supabase Storage when configured, otherwise
    // this server's disk) now, but remove them only after the commit so a
    // failed DB delete can't leave rows pointing at missing files.
    $filesToRemove = [];
    if (!empty($lesson['attachment_path'])) {
        $filesToRemove[] = $lesson['attachment_path'];
    }
    $stmt = $pdo->prepare('SELECT file_path FROM lesson_attachments WHERE lesson_id = ? AND file_path IS NOT NULL');
    $stmt->execute([$lessonId]);
    foreach ($stmt->fetchAll() as $row) {
        if (!empty($row['file_path'])) {
            $filesToRemove[] = $row['file_path'];
        }
    }

    // The lesson's homework goes with it (its rows cascade; the students' stored files don't).
    ensure_homework_schema($pdo);
    $stmt = $pdo->prepare(
        'SELECT hs.file_path FROM homework_submissions hs
         JOIN homework h ON h.id = hs.homework_id
         WHERE h.lesson_id = ? AND hs.file_path IS NOT NULL'
    );
    $stmt->execute([$lessonId]);
    foreach ($stmt->fetchAll() as $row) {
        if (!empty($row['file_path'])) {
            $filesToRemove[] = $row['file_path'];
        }
    }

    // quizzes/questions/quiz_results don't cascade on the DB side, so a lesson
    // that has any quizzes can't be deleted until they are - dependents first.
    $pdo->beginTransaction();
    $pdo->prepare(
        'DELETE FROM quiz_results WHERE quiz_id IN (SELECT id FROM quizzes WHERE lesson_id = ?)'
    )->execute([$lessonId]);
    $pdo->prepare(
        'DELETE FROM questions WHERE quiz_id IN (SELECT id FROM quizzes WHERE lesson_id = ?)'
    )->execute([$lessonId]);
    $pdo->prepare('DELETE FROM quizzes WHERE lesson_id = ?')->execute([$lessonId]);
    $pdo->prepare('DELETE FROM homework WHERE lesson_id = ?')->execute([$lessonId]); // hand-ins cascade
    $pdo->prepare('DELETE FROM lessons WHERE id = ?')->execute([$lessonId]);
    $pdo->commit();

    foreach ($filesToRemove as $path) {
        remove_stored_file($path);
    }

    send_json(true, 'Lesson deleted.', null);
} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('delete_lesson.php error: ' . $e->getMessage());
    send_json(false, 'Unable to delete the lesson.', null, 500);
}
