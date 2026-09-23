<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

$classId = (int) ($_GET['classId'] ?? 0);
if ($classId === 0) {
    send_json(false, 'classId is required.');
}

try {
    $pdo = get_db_connection();
    $stmt = $pdo->prepare(
        'SELECT id, class_id, title, body, created_at, updated_at
         FROM lessons WHERE class_id = ? ORDER BY created_at DESC'
    );
    $stmt->execute([$classId]);
    $rows = $stmt->fetchAll();

    $attachmentsStmt = $pdo->prepare(
        'SELECT id, type, file_path, name, mime_type, order_index
         FROM lesson_attachments WHERE lesson_id = ? ORDER BY order_index ASC, id ASC'
    );

    // Build the response, turning each server-side relative attachment path
    // into a full download URL (see public_file_url in common.php).
    $lessons = [];
    foreach ($rows as $row) {
        $lesson = [
            'id'          => (int) $row['id'],
            'class_id'    => (int) $row['class_id'],
            'title'       => $row['title'],
            'body'        => $row['body'],
            'created_at'  => $row['created_at'],
            'updated_at'  => $row['updated_at'],
        ];

        $attachmentsStmt->execute([$row['id']]);
        $attachments = [];
        foreach ($attachmentsStmt->fetchAll() as $a) {
            $attachments[] = [
                'id'          => (int) $a['id'],
                'type'        => $a['type'],
                'name'        => $a['name'],
                'mime_type'   => $a['mime_type'],
                'order_index' => (int) $a['order_index'],
                // Only IMAGE/FILE have a stored file - VIDEO_LINK's "name"
                // field already holds the raw URL, nothing to download.
                'url'         => !empty($a['file_path'])
                                    ? public_file_url($a['file_path'])
                                    : null,
            ];
        }
        $lesson['attachments'] = $attachments;

        $lessons[] = $lesson;
    }

    send_json(true, 'Lessons fetched.', ['lessons' => $lessons]);
} catch (Throwable $e) {
    error_log('get_lessons.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
