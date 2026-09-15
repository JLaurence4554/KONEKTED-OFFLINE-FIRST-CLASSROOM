<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Marks one student's attendance status for one class on one date. Upserts
// by (class_id, student_id, date) - the same natural key the local unique
// index uses - so re-marking a student the same day updates the one row
// instead of creating a duplicate.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input        = read_json_body();
$classId      = (int) ($input['classId'] ?? 0);
$studentName  = trim($input['studentName'] ?? '');
$studentEmail = trim($input['studentEmail'] ?? '');
$status       = trim($input['status'] ?? '');
$date         = trim($input['date'] ?? '');
$teacherEmail = trim($input['teacherEmail'] ?? '');

$validStatuses = ['Present', 'Absent', 'Late', 'Excused'];
if ($classId === 0 || $studentEmail === '' || $date === '' || $teacherEmail === '' || !in_array($status, $validStatuses, true)) {
    send_json(false, 'Missing or invalid fields.');
}

try {
    $pdo = get_db_connection();

    // Verify the teacher owns this class
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = ? AND role = ?');
    $stmt->execute([strtolower($teacherEmail), 'Teacher']);
    $teacher = $stmt->fetch();
    if (!$teacher) {
        send_json(false, 'Teacher not found.');
    }

    $stmt = $pdo->prepare('SELECT id FROM classes WHERE id = ? AND teacher_id = ?');
    $stmt->execute([$classId, $teacher['id']]);
    if (!$stmt->fetch()) {
        send_json(false, 'You do not own this class.');
    }

    // The student may be new to this backend (first attendance record ever
    // synced for them) - same lookup-or-create identity used by
    // join_class.php / sync_join_request.php / sync_class.php.
    $studentId = get_or_create_user($pdo, $studentName, $studentEmail, 'Student');

    $stmt = $pdo->prepare('SELECT id FROM attendance WHERE class_id = ? AND student_id = ? AND date = ?');
    $stmt->execute([$classId, $studentId, $date]);
    $existing = $stmt->fetch();

    if ($existing) {
        $stmt = $pdo->prepare('UPDATE attendance SET status = ? WHERE id = ?');
        $stmt->execute([$status, $existing['id']]);
        send_json(true, 'Attendance updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO attendance (class_id, student_id, status, date) VALUES (?, ?, ?, ?)'
    );
    $stmt->execute([$classId, $studentId, $status, $date]);
    send_json(true, 'Attendance recorded.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_attendance.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
