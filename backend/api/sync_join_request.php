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

    // Find the class by code
    $stmt = $pdo->prepare('SELECT id FROM classes WHERE class_code = ?');
    $stmt->execute([$classCode]);
    $classRow = $stmt->fetch();
    if (!$classRow) {
        send_json(false, 'Class code not found.');
    }
    $classId = (int) $classRow['id'];

    // Find or create the student
    $studentId = get_or_create_user($pdo, $studentName, $studentEmail, 'Student');

    // Check if already a member
    $stmt = $pdo->prepare('SELECT id FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);
    if ($stmt->fetch()) {
        send_json(false, 'You are already a member of this class.');
    }

    // Check if there's already a pending request
    $stmt = $pdo->prepare('SELECT id FROM join_requests WHERE class_id = ? AND student_id = ? AND status = \'PENDING\'');
    $stmt->execute([$classId, $studentId]);
    if ($stmt->fetch()) {
        send_json(false, 'You already have a pending request for this class.');
    }

    // Upsert: if there's an existing denied request, update it to pending
    $stmt = $pdo->prepare('SELECT id FROM join_requests WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare('UPDATE join_requests SET status = \'PENDING\', requested_at = NOW(), resolved_at = NULL WHERE id = ?');
        $stmt->execute([(int) $existing['id']]);
        send_json(true, 'Join request sent.', ['serverId' => (int) $existing['id']]);
    } else {
        $stmt = $pdo->prepare('INSERT INTO join_requests (class_id, student_id, status, requested_at) VALUES (?, ?, \'PENDING\', NOW())');
        $stmt->execute([$classId, $studentId]);
        send_json(true, 'Join request sent.', ['serverId' => (int) $pdo->lastInsertId()]);
    }
} catch (Throwable $e) {
    error_log('sync_join_request.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
