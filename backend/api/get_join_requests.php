<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

// The teacher is the logged-in account (see "Login protection" in common.php);
// a teacherEmail parameter is only checked against it.
$pdo = open_authenticated('Teacher', $auth);
assert_same_account($auth, $_GET['teacherEmail'] ?? '');

try {
    $teacherId = $auth['id'];

    $stmt = $pdo->prepare('
        SELECT jr.id, jr.class_id, jr.student_id,
               u.full_name AS student_name, u.email AS student_email, c.class_name, c.class_code,
               jr.status, jr.requested_at
        FROM join_requests jr
        INNER JOIN classes c ON jr.class_id = c.id
        INNER JOIN users u ON jr.student_id = u.id
        WHERE c.teacher_id = ? AND jr.status = \'PENDING\'
        ORDER BY jr.requested_at DESC
    ');
    $stmt->execute([$teacherId]);
    $rows = $stmt->fetchAll();

    // Map to camelCase for Gson and fix timestamp
    $requests = [];
    foreach ($rows as $row) {
        $ts = strtotime($row['requested_at']);
        $requests[] = [
            'requestId'  => (int) $row['id'],
            'classId'    => (int) $row['class_id'],
            'studentId'  => (int) $row['student_id'],
            'studentName'=> $row['student_name'],
            'studentEmail'=> $row['student_email'],
            'className'  => $row['class_name'],
            'classCode'  => $row['class_code'],
            'status'     => $row['status'],
            'requestedAt'=> $ts !== false ? $ts * 1000 : 0,
        ];
    }

    send_json(true, count($requests) . ' pending request(s).', ['requests' => $requests]);
} catch (Throwable $e) {
    error_log('get_join_requests.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
