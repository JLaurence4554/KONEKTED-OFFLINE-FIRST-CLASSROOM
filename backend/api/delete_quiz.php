<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$quizId       = (int) ($input['quizId'] ?? 0);
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($quizId === 0 || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

$pdo = null;
try {
    $pdo = get_db_connection();

    // Only the teacher who owns the quiz's class may delete it.
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare(
        'SELECT q.id
         FROM quizzes q
         JOIN lessons l ON l.id = q.lesson_id
         JOIN classes c ON c.id = l.class_id
         WHERE q.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$quizId, $teacher['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'Quiz not found or not owned by you.');
    }

    // None of the foreign keys cascade on the DB side, so a plain
    // "DELETE FROM quizzes" fails once the quiz has questions or student
    // results - remove the dependents first, in one transaction.
    $pdo->beginTransaction();
    $pdo->prepare('DELETE FROM quiz_results WHERE quiz_id = ?')->execute([$quizId]);
    $pdo->prepare('DELETE FROM questions WHERE quiz_id = ?')->execute([$quizId]);
    $pdo->prepare('DELETE FROM quizzes WHERE id = ?')->execute([$quizId]);
    $pdo->commit();

    send_json(true, 'Quiz deleted.', null);
} catch (Throwable $e) {
    if ($pdo !== null && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('delete_quiz.php error: ' . $e->getMessage());
    send_json(false, 'Unable to delete the quiz.', null, 500);
}
