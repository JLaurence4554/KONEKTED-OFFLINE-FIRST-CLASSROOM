<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher: score (0-100) and written feedback for one student's hand-in. Only
// ever updates an existing hand-in, and only for the class's own teacher - the
// student-side endpoint can never carry a grade.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$homeworkId   = (int) ($input['homeworkId'] ?? 0);
$studentEmail = strtolower(trim($input['studentEmail'] ?? ''));
$grade        = $input['grade'] ?? null;
$feedback     = trim($input['feedback'] ?? '');

$pdo = open_authenticated('Teacher', $auth);

if ($homeworkId === 0 || $studentEmail === '' || $grade === null) {
    send_json(false, 'Missing required fields (homeworkId, studentEmail, grade).');
}
if (!is_numeric($grade) || (string) (int) $grade !== (string) $grade) {
    send_json(false, 'The grade must be a whole number.');
}
$grade = (int) $grade;
if ($grade < 0 || $grade > 100) {
    send_json(false, 'The grade must be between 0 and 100.');
}
if (mb_strlen($feedback) > 1000) {
    send_json(false, 'The feedback is too long.');
}

try {
    ensure_homework_schema($pdo);

    $stmt = $pdo->prepare('SELECT class_id FROM homework WHERE id = ?');
    $stmt->execute([$homeworkId]);
    $homework = $stmt->fetch();
    if (!$homework || class_role($pdo, $auth, (int) $homework['class_id']) !== 'owner') {
        send_forbidden('You do not own this homework.');
    }

    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = \'Student\'');
    $stmt->execute([$studentEmail]);
    $student = $stmt->fetch();
    if (!$student) {
        send_json(false, 'Student not found.');
    }

    $stmt = $pdo->prepare('SELECT id FROM homework_submissions WHERE homework_id = ? AND student_id = ?');
    $stmt->execute([$homeworkId, (int) $student['id']]);
    $submission = $stmt->fetch();
    if (!$submission) {
        send_json(false, 'This student has not handed in this homework yet.');
    }

    $stmt = $pdo->prepare('UPDATE homework_submissions SET grade = ?, feedback = ?, graded_at = ? WHERE id = ?');
    $stmt->execute([$grade, $feedback, now_ms(), (int) $submission['id']]);

    send_json(true, 'Grade saved.', ['serverId' => (int) $submission['id']]);
} catch (Throwable $e) {
    error_log('grade_homework_submission.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
