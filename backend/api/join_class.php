<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$classCode    = strtoupper(trim($input['classCode'] ?? ''));
$studentName  = trim($input['studentName'] ?? '');
$studentEmail = trim($input['studentEmail'] ?? '');

if ($classCode === '' || $studentEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    $pdo = get_db_connection();

    $stmt = $pdo->prepare('SELECT id FROM classes WHERE class_code = ?');
    $stmt->execute([$classCode]);
    $classRow = $stmt->fetch();
    if (!$classRow) {
        send_json(false, 'Class code not found.');
    }
    $classId = (int) $classRow['id'];

    $studentId = get_or_create_user($pdo, $studentName, $studentEmail, 'Student');

    $stmt = $pdo->prepare('SELECT id FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);
    if ($stmt->fetch()) {
        send_json(false, 'You are already a member of this class.');
    }

    $stmt = $pdo->prepare('INSERT INTO class_members (class_id, student_id) VALUES (?, ?)');
    $stmt->execute([$classId, $studentId]);

    send_json(true, 'Successfully joined class.');
} catch (Throwable $e) {
    error_log('join_class.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
