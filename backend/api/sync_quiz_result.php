<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$quizId        = (int) ($input['quizId'] ?? 0);
$score         = (int) ($input['score'] ?? 0);
$correctCount  = (int) ($input['correctCount'] ?? 0);
$totalQuestions = (int) ($input['totalQuestions'] ?? 0);

// The result belongs to the logged-in student (see "Login protection" in
// common.php); a studentEmail in the request is only checked against it.
$pdo = open_authenticated('Student', $auth);
assert_same_account($auth, $input['studentEmail'] ?? '');

if ($quizId === 0) {
    send_json(false, 'Missing required fields.');
}

try {
    $studentId = $auth['id'];

    // Verify the quiz exists, and that this student belongs to its class
    $stmt = $pdo->prepare(
        'SELECT c.id AS class_id FROM quizzes q
         JOIN lessons l ON l.id = q.lesson_id
         JOIN classes c ON c.id = l.class_id
         WHERE q.id = ?'
    );
    $stmt->execute([$quizId]);
    $quizClass = $stmt->fetch();
    if (!$quizClass) {
        send_json(false, 'Quiz not found.');
    }
    if (class_role($pdo, $auth, (int) $quizClass['class_id']) === null) {
        send_forbidden('You are not a member of the class this quiz belongs to.');
    }

    // Upsert: if already submitted, update; otherwise insert
    $stmt = $pdo->prepare('SELECT id FROM quiz_results WHERE quiz_id = ? AND student_id = ?');
    $stmt->execute([$quizId, $studentId]);
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare(
            'UPDATE quiz_results SET score = ?, correct_count = ?, total_questions = ?
             WHERE id = ?'
        );
        $stmt->execute([$score, $correctCount, $totalQuestions, $existing['id']]);
        send_json(true, 'Result updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO quiz_results (quiz_id, student_id, score, correct_count, total_questions)
         VALUES (?, ?, ?, ?, ?)'
    );
    $stmt->execute([$quizId, $studentId, $score, $correctCount, $totalQuestions]);
    send_json(true, 'Result synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_quiz_result.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
