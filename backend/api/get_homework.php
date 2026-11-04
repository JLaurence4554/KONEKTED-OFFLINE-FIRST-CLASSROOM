<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// The homework of one class, newest first. Any logged-in person may ask, but
// only the class's teacher or one of its students gets an answer. A student
// also gets THEIR OWN hand-in for each homework (never a classmate's).

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$pdo = open_authenticated(null, $auth);

$classId = (int) ($_GET['classId'] ?? 0);
if ($classId === 0) {
    send_json(false, 'classId is required.');
}

try {
    ensure_homework_schema($pdo);

    $role = class_role($pdo, $auth, $classId);
    if ($role === null) {
        send_forbidden('You do not have access to this class.');
    }

    $stmt = $pdo->prepare(
        'SELECT id, class_id, lesson_id, title, instructions, due_at, created_at
         FROM homework WHERE class_id = ? ORDER BY id DESC'
    );
    $stmt->execute([$classId]);
    $rows = $stmt->fetchAll();

    // This student's own hand-ins, by homework id.
    $mine = [];
    if ($role === 'member' && count($rows) > 0) {
        $stmt = $pdo->prepare(
            'SELECT hs.homework_id, hs.id, hs.note, hs.file_path, hs.file_name, hs.mime_type,
                    hs.submitted_at, hs.grade, hs.feedback, hs.graded_at
             FROM homework_submissions hs JOIN homework h ON h.id = hs.homework_id
             WHERE h.class_id = ? AND hs.student_id = ?'
        );
        $stmt->execute([$classId, $auth['id']]);
        foreach ($stmt->fetchAll() as $s) {
            $mine[(int) $s['homework_id']] = [
                'id'          => (int) $s['id'],
                'note'        => $s['note'],
                'fileName'    => $s['file_name'],
                'mimeType'    => $s['mime_type'],
                'fileUrl'     => !empty($s['file_path']) ? public_file_url($s['file_path']) : null,
                'submittedAt' => (int) $s['submitted_at'],
                'grade'       => $s['grade'] === null ? null : (int) $s['grade'],
                'feedback'    => $s['feedback'],
                'gradedAt'    => $s['graded_at'] === null ? null : (int) $s['graded_at'],
            ];
        }
    }

    $result = [];
    foreach ($rows as $row) {
        $id = (int) $row['id'];
        $result[] = [
            'id'           => $id,
            'classId'      => (int) $row['class_id'],
            'lessonId'     => $row['lesson_id'] === null ? null : (int) $row['lesson_id'],
            'title'       => $row['title'],
            'instructions' => $row['instructions'],
            'dueAt'        => $row['due_at'] === null ? null : (int) $row['due_at'],
            'createdAt'    => (int) $row['created_at'],
            'submission'   => $mine[$id] ?? null,
        ];
    }

    send_json(true, 'Homework fetched.', ['homework' => $result]);
} catch (Throwable $e) {
    error_log('get_homework.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
