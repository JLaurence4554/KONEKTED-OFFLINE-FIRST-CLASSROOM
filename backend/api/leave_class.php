<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$classCode   = strtoupper(trim($input['classCode'] ?? ''));

// The student is the logged-in account (see "Login protection" in common.php).
$pdo = open_authenticated('Student', $auth);
assert_same_account($auth, $input['studentEmail'] ?? '');

if ($classCode === '') {
    send_json(false, 'Missing required fields (classCode).');
}

try {
    $studentId = $auth['id'];

    // Find the class by its unique code (matches what the app generates)
    $stmt = $pdo->prepare('SELECT id FROM classes WHERE class_code = ?');
    $stmt->execute([$classCode]);
    $classRow = $stmt->fetch();
    if (!$classRow) {
        send_json(false, 'Class not found.');
    }
    $classId = (int) $classRow['id'];

    // Check if the student is actually a member of this class
    $stmt = $pdo->prepare('SELECT id FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);
    if (!$stmt->fetch()) {
        send_json(false, 'You are not a member of this class.');
    }

    // Remove the membership
    $stmt = $pdo->prepare('DELETE FROM class_members WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);

    // Also clean up any join requests for this student/class pair
    $stmt = $pdo->prepare('DELETE FROM join_requests WHERE class_id = ? AND student_id = ?');
    $stmt->execute([$classId, $studentId]);

    send_json(true, 'Successfully left the class.');
} catch (Throwable $e) {
    error_log('leave_class.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
