<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$studentEmail = trim($_GET['studentEmail'] ?? '');

if ($studentEmail === '') {
    send_json(false, 'Missing studentEmail parameter.');
}

try {
    $pdo = get_db_connection();

    $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ? AND role = 'Student'");
    $stmt->execute([$studentEmail]);
    $userRow = $stmt->fetch();
    if (!$userRow) {
        send_json(true, 'No classes found.', ['classes' => []]);
        return;
    }
    $studentId = (int) $userRow['id'];

    $stmt = $pdo->prepare('
        SELECT c.id, c.class_name, c.subject, c.section,
               c.school_year, c.class_code,
               t.full_name, t.email
        FROM classes c
        INNER JOIN class_members cm ON c.id = cm.class_id
        INNER JOIN users t ON c.teacher_id = t.id
        WHERE cm.student_id = ?
        ORDER BY cm.joined_at DESC
    ');
    $stmt->execute([$studentId]);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $classes = [];
    foreach ($rows as $row) {
        $classes[] = [
            'serverId'    => (int) $row['id'],
            'className'   => $row['class_name'],
            'subject'     => $row['subject'],
            'section'     => $row['section'],
            'schoolYear'  => $row['school_year'],
            'classCode'   => $row['class_code'],
            'teacherName' => $row['full_name'],
            'teacherEmail'=> $row['email'],
        ];
    }

    send_json(true, 'OK', ['classes' => $classes]);
} catch (Throwable $e) {
    error_log('get_student_classes.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server.', null, 500);
}
