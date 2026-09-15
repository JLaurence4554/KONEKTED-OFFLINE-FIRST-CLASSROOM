<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$quizId = (int) ($_GET['quizId'] ?? 0);
if ($quizId === 0) {
    send_json(false, 'quizId is required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare(
        'SELECT r.id, r.quiz_id, r.student_id, r.score, r.correct_count, r.total_questions,
                r.submitted_at, u.full_name AS student_name, u.email AS student_email
         FROM quiz_results r
         INNER JOIN users u ON r.student_id = u.id
         WHERE r.quiz_id = ? ORDER BY r.submitted_at DESC'
    );
    $stmt->execute([$quizId]);
    $results = $stmt->fetchAll();

    send_json(true, 'Results fetched.', ['results' => $results]);
} catch (Throwable $e) {
    error_log('get_quiz_results.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
