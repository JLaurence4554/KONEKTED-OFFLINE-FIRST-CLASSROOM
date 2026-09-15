<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$classCode    = strtoupper(trim($input['classCode'] ?? ''));
$studentEmail = strtolower(trim($input['studentEmail'] ?? ''));

if ($classCode === '' || $studentEmail === '') {
    send_json(false, 'Missing required fields (classCode, studentEmail).');
}

try {
    $pdo = get_db_connection();

    // Find the class by code
    $stmt = $pdo->prepare('SELECT id FROM classes WHERE class_code = ?');
    $stmt->execute([$classCode]);
    $classRow = $stmt->fetch();
    if (!$classRow) {
        send_json(false, 'Class not found.');
    }
    $classId = (int) $classRow['id'];

    // Find the student by email
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = \'Student\'');
    $stmt->execute([$studentEmail]);
    $studentRow = $stmt->fetch();
    if (!$studentRow) {
        send_json(false, 'Student not found.');
    }
    $studentId = (int) $studentRow['id'];

    // Update join_requests status to APPROVED (if a pending request exists)
    $stmt = $pdo->prepare(
        'UPDATE join_requests SET status = \'APPROVED\', resolved_at = NOW()
         WHERE class_id = ? AND student_id = ? AND status = \'PENDING\''
    );
    $stmt->execute([$classId, $studentId]);

    // Insert class_members if not already a member
    $stmt = $pdo->prepare('SELECT id FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);
    if (!$stmt->fetch()) {
        $stmt = $pdo->prepare('INSERT INTO class_members (class_id, student_id) VALUES (?, ?)');
        $stmt->execute([$classId, $studentId]);
    }

    send_json(true, 'Student approved and added to class.');
} catch (Throwable $e) {
    error_log('approve_join_request.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server.', null, 500);
}
