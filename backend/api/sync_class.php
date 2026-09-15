<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$input = read_json_body();
$className   = trim($input['className'] ?? '');
$subject     = trim($input['subject'] ?? '');
$section     = trim($input['section'] ?? '');
$schoolYear  = trim($input['schoolYear'] ?? '');
$classCode   = strtoupper(trim($input['classCode'] ?? ''));
$teacherName = trim($input['teacherName'] ?? '');
$teacherEmail = trim($input['teacherEmail'] ?? '');

if ($className === '' || $classCode === '' || $teacherEmail === '') {
    send_json(false, 'Missing required fields.');
}

try {
    $pdo = get_db_connection();
    $teacherId = get_or_create_user($pdo, $teacherName, $teacherEmail, 'Teacher');

    $stmt = $pdo->prepare('SELECT id, teacher_id FROM classes WHERE class_code = ?');
    $stmt->execute([$classCode]);
    $existing = $stmt->fetch();

    if ($existing) {
        if ((int) $existing['teacher_id'] !== $teacherId) {
            // Codes are generated client-side (see ClassCodeGenerator) and
            // are astronomically unlikely to collide across two different
            // teachers, but the server is the last line of defense since
            // it's the one place that can see every teacher's codes.
            send_json(false, 'This class code is already in use by another teacher.');
        }
        $stmt = $pdo->prepare(
            'UPDATE classes SET class_name = ?, subject = ?, section = ?, school_year = ? WHERE id = ?'
        );
        $stmt->execute([$className, $subject, $section, $schoolYear, $existing['id']]);
        send_json(true, 'Class updated.', ['serverId' => (int) $existing['id']]);
        return;
    }

    $stmt = $pdo->prepare(
        'INSERT INTO classes (class_name, subject, section, school_year, class_code, teacher_id)
         VALUES (?, ?, ?, ?, ?, ?)'
    );
    $stmt->execute([$className, $subject, $section, $schoolYear, $classCode, $teacherId]);
    send_json(true, 'Class synced.', ['serverId' => (int) $pdo->lastInsertId()]);
} catch (Throwable $e) {
    error_log('sync_class.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
