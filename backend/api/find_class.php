<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$classCode = strtoupper(trim($_GET['code'] ?? ''));
if ($classCode === '') {
    send_json(false, 'Class code is required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare(
        'SELECT c.id, c.class_name, c.subject, c.section, c.school_year, c.class_code,
                u.full_name AS teacher_name, u.email AS teacher_email
         FROM classes c
         INNER JOIN users u ON c.teacher_id = u.id
         WHERE c.class_code = ?'
    );
    $stmt->execute([$classCode]);
    $row = $stmt->fetch();

    if (!$row) {
        send_json(false, 'Class code not found.');
    }

    send_json(true, 'Class found.', [
        'serverId'     => (int) $row['id'],
        'className'    => $row['class_name'],
        'subject'      => $row['subject'],
        'section'      => $row['section'],
        'schoolYear'   => $row['school_year'],
        'classCode'    => $row['class_code'],
        'teacherName'  => $row['teacher_name'],
        'teacherEmail' => $row['teacher_email'],
    ]);
} catch (Throwable $e) {
    error_log('find_class.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
