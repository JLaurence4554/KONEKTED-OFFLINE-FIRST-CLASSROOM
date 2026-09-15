<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Teacher-side counterpart to get_student_classes.php. Lets a teacher's
// classes (and their rosters) come back after a reinstall/new device, since
// recovering the login account alone (see login.php) doesn't restore any
// locally-created content on its own - this is what actually does that for
// classes. Returns each class's enrolled students inline (rather than a
// second endpoint per class) to keep this a single round trip.

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$teacherEmail = trim($_GET['teacherEmail'] ?? '');

if ($teacherEmail === '') {
    send_json(false, 'Missing teacherEmail parameter.');
}

try {
    $pdo = get_db_connection();

    $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ? AND role = 'Teacher'");
    $stmt->execute([$teacherEmail]);
    $teacherRow = $stmt->fetch();
    if (!$teacherRow) {
        send_json(true, 'No classes found.', ['classes' => []]);
        return;
    }
    $teacherId = (int) $teacherRow['id'];

    $stmt = $pdo->prepare('
        SELECT id, class_name, subject, section, school_year, class_code
        FROM classes
        WHERE teacher_id = ?
        ORDER BY created_at DESC
    ');
    $stmt->execute([$teacherId]);
    $classRows = $stmt->fetchAll();

    $memberStmt = $pdo->prepare('
        SELECT u.full_name, u.email
        FROM class_members cm
        INNER JOIN users u ON cm.student_id = u.id
        WHERE cm.class_id = ?
        ORDER BY cm.joined_at ASC
    ');

    $classes = [];
    foreach ($classRows as $row) {
        $classId = (int) $row['id'];

        $memberStmt->execute([$classId]);
        $memberRows = $memberStmt->fetchAll();
        $students = [];
        foreach ($memberRows as $m) {
            $students[] = [
                'fullName' => $m['full_name'],
                'email'    => $m['email'],
            ];
        }

        $classes[] = [
            'serverId'   => $classId,
            'className'  => $row['class_name'],
            'subject'    => $row['subject'],
            'section'    => $row['section'],
            'schoolYear' => $row['school_year'],
            'classCode'  => $row['class_code'],
            'students'   => $students,
        ];
    }

    send_json(true, 'OK', ['classes' => $classes]);
} catch (Throwable $e) {
    error_log('get_teacher_classes.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server.', null, 500);
}
