<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$classId      = (int) ($input['classId'] ?? 0);
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($classId === 0 || $teacherEmail === '') {
    send_json(false, 'Missing required fields (classId, teacherEmail).');
}

$pdo = null;

try {
    ensure_attachments_dir();
    $pdo = get_db_connection();

    // Only the owning teacher may delete the class.
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare('SELECT id FROM classes WHERE id = ? AND teacher_id = ?');
    $stmt->execute([$classId, $teacher['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'Class not found or not owned by you.');
    }

    $pdo->beginTransaction();

    // Remove attachment files for every lesson in this class before the rows go away.
    $stmt = $pdo->prepare('SELECT attachment_path FROM lessons WHERE class_id = ? AND attachment_path IS NOT NULL');
    $stmt->execute([$classId]);
    foreach ($stmt->fetchAll() as $row) {
        if (!empty($row['attachment_path'])) {
            $path = ATTACHMENTS_DIR . '/' . $row['attachment_path'];
            if (file_exists($path)) {
                //noinspection PhpUsageOfSuppressOperator
                @unlink($path);
            }
        }
    }

    // Cascade delete in FK-safe order. None of these foreign keys cascade on
    // the DB side, so a plain "DELETE FROM classes" fails whenever the class
    // has any real content (lessons, quizzes, roster, ...) in it - this is
    // done explicitly instead, deepest dependents first:
    //   quiz_results -> questions -> quizzes -> lessons
    //   attendance / join_requests / class_members -> classes
    $pdo->prepare(
        'DELETE FROM quiz_results WHERE quiz_id IN (
            SELECT q.id FROM quizzes q JOIN lessons l ON l.id = q.lesson_id WHERE l.class_id = ?
        )'
    )->execute([$classId]);

    $pdo->prepare(
        'DELETE FROM questions WHERE quiz_id IN (
            SELECT q.id FROM quizzes q JOIN lessons l ON l.id = q.lesson_id WHERE l.class_id = ?
        )'
    )->execute([$classId]);

    $pdo->prepare(
        'DELETE FROM quizzes WHERE lesson_id IN (SELECT id FROM lessons WHERE class_id = ?)'
    )->execute([$classId]);

    $pdo->prepare('DELETE FROM lessons WHERE class_id = ?')->execute([$classId]);
    $pdo->prepare('DELETE FROM attendance WHERE class_id = ?')->execute([$classId]);
    $pdo->prepare('DELETE FROM join_requests WHERE class_id = ?')->execute([$classId]);
    $pdo->prepare('DELETE FROM class_members WHERE class_id = ?')->execute([$classId]);
    $pdo->prepare('DELETE FROM classes WHERE id = ?')->execute([$classId]);

    $pdo->commit();

    send_json(true, 'Class deleted.', null);
} catch (Throwable $e) {
    if ($pdo !== null && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('delete_class.php error: ' . $e->getMessage());
    send_json(false, 'Unable to delete the class.', null, 500);
}
