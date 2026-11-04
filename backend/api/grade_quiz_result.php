<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher-side grading endpoint - the missing half of essay grading.
// EssayGradingActivity on Android saves the teacher's score override
// (quiz_results.graded_score) locally first (offline-first, like every
// other write in this project) and calls this so the grade reaches the
// student's phone and the teacher's other devices. The student-facing
// sync_quiz_result.php deliberately can NOT carry the grade: it runs as
// the Student, and graded_score is teacher-owned - a student must never
// be able to set their own override.
//
// It only ever updates an existing result row: a result can only exist
// once the student has submitted (their phone pushes it via
// sync_quiz_result.php, offline-first).

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$quizId       = (int) ($input['quizId'] ?? 0);
$studentEmail = strtolower(trim($input['studentEmail'] ?? ''));
$gradedScore  = $input['gradedScore'] ?? null;

// The teacher is the logged-in account (see "Login protection" in
// common.php); a teacherEmail in the request is only checked against it.
$pdo = open_authenticated('Teacher', $auth);
assert_same_account($auth, $input['teacherEmail'] ?? '');

if ($quizId === 0 || $studentEmail === '' || $gradedScore === null) {
    send_json(false, 'Missing required fields (quizId, studentEmail, gradedScore).');
}
if (!is_numeric($gradedScore) || (string) (int) $gradedScore !== (string) $gradedScore) {
    send_json(false, 'gradedScore must be a whole number.');
}
$gradedScore = (int) $gradedScore;
if ($gradedScore < 0 || $gradedScore > 100) {
    send_json(false, 'gradedScore must be between 0 and 100.');
}

try {
    // Only the teacher who owns the quiz's class may grade its results.
    $stmt = $pdo->prepare(
        'SELECT q.id FROM quizzes q
         JOIN lessons l ON l.id = q.lesson_id
         JOIN classes c ON c.id = l.class_id
         WHERE q.id = ? AND c.teacher_id = ?'
    );
    $stmt->execute([$quizId, $auth['id']]);
    if (!$stmt->fetch()) {
        send_forbidden('Quiz not found or not owned by you.');
    }

    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = \'Student\'');
    $stmt->execute([$studentEmail]);
    $student = $stmt->fetch();
    if (!$student) {
        send_json(false, 'Student not found.');
    }

    $stmt = $pdo->prepare('SELECT id FROM quiz_results WHERE quiz_id = ? AND student_id = ?');
    $stmt->execute([$quizId, (int) $student['id']]);
    $result = $stmt->fetch();
    if (!$result) {
        send_json(false, 'This student has not submitted the quiz yet.');
    }

    $stmt = $pdo->prepare('UPDATE quiz_results SET graded_score = ? WHERE id = ?');
    $stmt->execute([$gradedScore, (int) $result['id']]);

    send_json(true, 'Grade saved.', ['serverId' => (int) $result['id']]);
} catch (Throwable $e) {
    error_log('grade_quiz_result.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
