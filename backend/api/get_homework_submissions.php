<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher: everyone's hand-in for one homework. Students who have not handed
// in are simply not in the list (the app compares with the class roster).

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$pdo = open_authenticated('Teacher', $auth);

$homeworkId = (int) ($_GET['homeworkId'] ?? 0);
if ($homeworkId === 0) {
    send_json(false, 'homeworkId is required.');
}

try {
    ensure_homework_schema($pdo);

    $stmt = $pdo->prepare('SELECT class_id FROM homework WHERE id = ?');
    $stmt->execute([$homeworkId]);
    $homework = $stmt->fetch();
    if (!$homework || class_role($pdo, $auth, (int) $homework['class_id']) !== 'owner') {
        send_forbidden('You do not own this homework.');
    }

    $stmt = $pdo->prepare(
        'SELECT hs.id, hs.note, hs.file_path, hs.file_name, hs.mime_type, hs.submitted_at,
                hs.grade, hs.feedback, hs.graded_at, u.full_name, u.email
         FROM homework_submissions hs JOIN users u ON u.id = hs.student_id
         WHERE hs.homework_id = ? ORDER BY hs.submitted_at DESC'
    );
    $stmt->execute([$homeworkId]);

    $result = [];
    foreach ($stmt->fetchAll() as $s) {
        $result[] = [
            'id'           => (int) $s['id'],
            'studentName'  => $s['full_name'],
            'studentEmail' => $s['email'],
            'note'         => $s['note'],
            'fileName'     => $s['file_name'],
            'mimeType'     => $s['mime_type'],
            'fileUrl'      => !empty($s['file_path']) ? public_file_url($s['file_path']) : null,
            'submittedAt'  => (int) $s['submitted_at'],
            'grade'        => $s['grade'] === null ? null : (int) $s['grade'],
            'feedback'     => $s['feedback'],
            'gradedAt'     => $s['graded_at'] === null ? null : (int) $s['graded_at'],
        ];
    }

    send_json(true, 'Submissions fetched.', ['submissions' => $result]);
} catch (Throwable $e) {
    error_log('get_homework_submissions.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
