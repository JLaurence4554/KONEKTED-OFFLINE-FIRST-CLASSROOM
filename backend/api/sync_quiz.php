<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$lessonId     = (int) ($input['lessonId'] ?? 0);
$title        = trim($input['title'] ?? '');
$instructions = trim($input['instructions'] ?? '');
$timeLimit    = isset($input['timeLimitMinutes']) ? (int) $input['timeLimitMinutes'] : null;
$passingScore = (int) ($input['passingScore'] ?? 0);
$status       = trim($input['status'] ?? 'Draft');

// The teacher is the logged-in account (see "Login protection" in common.php);
// a teacherEmail in the request is only checked against it.
$pdo = open_authenticated('Teacher', $auth);
assert_same_account($auth, $input['teacherEmail'] ?? '');

if ($lessonId === 0 || $title === '') {
    send_json(false, 'Missing required fields.');
}

try {
    // Find the lesson and verify the teacher owns its class
    $stmt = $pdo->prepare(
        'SELECT l.id, l.class_id FROM lessons l
         INNER JOIN classes c ON l.class_id = c.id
         WHERE l.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$lessonId, $auth['id']]);
    $lesson = $stmt->fetch();
    if (!$lesson) {
        send_json(false, 'Lesson not found or you do not own it.');
    }

    // Upsert: check if quiz with same title+lesson exists
    $stmt = $pdo->prepare('SELECT id FROM quizzes WHERE lesson_id = ? AND title = ?');
    $stmt->execute([$lessonId, $title]);
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare(
            'UPDATE quizzes SET instructions = ?, time_limit_minutes = ?, passing_score = ?, status = ?
             WHERE id = ?'
        );
        $stmt->execute([$instructions, $timeLimit, $passingScore, $status, $existing['id']]);
        send_json(true, 'Quiz updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO quizzes (lesson_id, title, instructions, time_limit_minutes, passing_score, status)
         VALUES (?, ?, ?, ?, ?, ?)'
    );
    $stmt->execute([$lessonId, $title, $instructions, $timeLimit, $passingScore, $status]);
    send_json(true, 'Quiz synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_quiz.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
