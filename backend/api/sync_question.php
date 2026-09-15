<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$quizId         = (int) ($input['quizId'] ?? 0);
$questionText   = trim($input['question'] ?? '');
$questionType   = strtoupper(trim($input['questionType'] ?? 'MULTIPLE_CHOICE'));
$choiceA        = trim($input['choiceA'] ?? '');
$choiceB        = trim($input['choiceB'] ?? '');
$choiceC        = trim($input['choiceC'] ?? '');
$choiceD        = trim($input['choiceD'] ?? '');
$correctAnswer  = strtoupper(trim($input['correctAnswer'] ?? ''));
$answerText     = isset($input['answerText']) ? trim($input['answerText']) : null;
$orderIndex     = (int) ($input['orderIndex'] ?? 0);
$teacherEmail   = trim($input['teacherEmail'] ?? '');

if ($quizId === 0 || $questionText === '' || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

// Validate question type
if (!in_array($questionType, ['MULTIPLE_CHOICE', 'IDENTIFICATION', 'ESSAY'])) {
    $questionType = 'MULTIPLE_CHOICE';
}

// Validate correct_answer for MCQ only
if ($questionType === 'MULTIPLE_CHOICE' && !in_array($correctAnswer, ['A', 'B', 'C', 'D'])) {
    send_json(false, 'Invalid correct answer.');
}

try {
    $pdo = get_db_connection();

    // Verify the teacher owns the quiz's lesson's class
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare(
        'SELECT q.id FROM quizzes q
         INNER JOIN lessons l ON q.lesson_id = l.id
         INNER JOIN classes c ON l.class_id = c.id
         WHERE q.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$quizId, $teacher['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'Quiz not found or you do not own it.');
    }

    // Upsert: check if question with same text+quiz exists (update) or insert new
    $stmt = $pdo->prepare('SELECT id FROM questions WHERE quiz_id = ? AND question = ?');
    $stmt->execute([$quizId, $questionText]);
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare(
            'UPDATE questions SET question_type = ?, choice_a = ?, choice_b = ?, choice_c = ?, choice_d = ?,
             correct_answer = ?, answer_text = ?, order_index = ? WHERE id = ?'
        );
        $stmt->execute([$questionType, $choiceA, $choiceB, $choiceC, $choiceD,
                         $correctAnswer, $answerText, $orderIndex, $existing['id']]);
        send_json(true, 'Question updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO questions (quiz_id, question, question_type, choice_a, choice_b, choice_c, choice_d,
         correct_answer, answer_text, order_index)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $stmt->execute([$quizId, $questionText, $questionType, $choiceA, $choiceB, $choiceC, $choiceD,
                     $correctAnswer, $answerText, $orderIndex]);
    send_json(true, 'Question synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_question.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
