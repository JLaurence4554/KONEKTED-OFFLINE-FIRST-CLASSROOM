<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$quizId        = (int) ($input['quizId'] ?? 0);
$studentEmail  = trim($input['studentEmail'] ?? '');
$score         = (int) ($input['score'] ?? 0);
$correctCount  = (int) ($input['correctCount'] ?? 0);
$totalQuestions = (int) ($input['totalQuestions'] ?? 0);

if ($quizId === 0 || $studentEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    $pdo = get_db_connection();

    // Find or create the student
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($studentEmail), 'Student']);
    $student = $stmt->fetch();
    if (!$student) {
        // Try as any role (teacher can also submit for testing)
        $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ?');
        $stmt->execute([strtolower($studentEmail)]);
        $student = $stmt->fetch();
        if (!$student) {
            send_json(false, 'Student not found.');
        }
    }
    $studentId = (int) $student['id'];

    // Verify the quiz exists
    $stmt = $pdo->prepare('SELECT id FROM quizzes WHERE id = ?');
    $stmt->execute([$quizId]);
    if (!$stmt->fetch()) {
        send_json(false, 'Quiz not found.');
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
