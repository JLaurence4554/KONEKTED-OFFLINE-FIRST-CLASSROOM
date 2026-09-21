<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$lessonId    = (int) ($input['lessonId'] ?? 0);
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($lessonId === 0 || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    ensure_attachments_dir();
    $pdo = get_db_connection();

    // Only the owning teacher may delete the lesson.
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare(
        'SELECT l.id, l.attachment_path, l.class_id
         FROM lessons l
         JOIN classes c ON c.id = l.class_id
         WHERE l.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$lessonId, $teacher['id']]);
    $lesson = $stmt->fetch();
    if (!$lesson) {
        send_json(false, 'Lesson not found or not owned by you.');
    }

    // Remove the server-side attachment file if one exists.
    if (!empty($lesson['attachment_path']) && file_exists(ATTACHMENTS_DIR . '/' . $lesson['attachment_path'])) {
        //noinspection PhpUsageOfSuppressOperator
        @unlink(ATTACHMENTS_DIR . '/' . $lesson['attachment_path']);
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
    $pdo->prepare('DELETE FROM lessons WHERE id = ?')->execute([$lessonId]);
    $pdo->commit();

    send_json(true, 'Lesson deleted.', null);
} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('delete_lesson.php error: ' . $e->getMessage());
    send_json(false, 'Unable to delete the lesson.', null, 500);
}
