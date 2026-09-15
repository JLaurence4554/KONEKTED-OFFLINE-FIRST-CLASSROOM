<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$lessonId = (int) ($_GET['lessonId'] ?? 0);
if ($lessonId === 0) {
    send_json(false, 'lessonId is required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare(
        'SELECT id, lesson_id, title, instructions, time_limit_minutes, passing_score, status, created_at
         FROM quizzes WHERE lesson_id = ? ORDER BY created_at DESC'
    );
    $stmt->execute([$lessonId]);
    $quizzes = $stmt->fetchAll();

    $result = [];
    foreach ($quizzes as $quiz) {
        $qStmt = $pdo->prepare(
            'SELECT id, quiz_id, question, question_type, choice_a, choice_b, choice_c, choice_d,
                    correct_answer, answer_text, order_index
             FROM questions WHERE quiz_id = ? ORDER BY order_index ASC'
        );
        $qStmt->execute([$quiz['id']]);
        $quiz['questions'] = $qStmt->fetchAll();
        $result[] = $quiz;
    }

    send_json(true, 'Quizzes fetched.', ['quizzes' => $result]);
} catch (Throwable $e) {
    error_log('get_quizzes.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
