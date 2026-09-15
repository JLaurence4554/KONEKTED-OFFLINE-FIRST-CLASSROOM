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

    // Build the response, turning the server-side relative attachment path into
    // a full download URL the client can pass to download_attachment.php.
    //
    // HTTP_ORIGIN is a browser-only header - Retrofit/OkHttp (the Android app)
    // never sends one, so that branch never actually fires for a real client
    // and always fell through to the REQUEST_URI fallback below. That fallback
    // is itself broken for this app: REQUEST_URI is a path only (no scheme or
    // host), so the resulting "base URL" was something like "/classroom-api" -
    // schemeless, so Uri.parse()+ACTION_VIEW on the Android side could never
    // resolve it. Build a real absolute URL from the host instead, honoring
    // X-Forwarded-Proto since ngrok terminates HTTPS at its edge and forwards
    // to XAMPP over plain HTTP - without that header check the scheme would
    // wrongly come out as "http" even when the app reached this over https.
    $scheme = 'http';
    if (!empty($_SERVER['HTTP_X_FORWARDED_PROTO'])) {
        $scheme = strtolower(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'])[0]);
    } elseif (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') {
        $scheme = 'https';
    }
    $host = $_SERVER['HTTP_HOST'] ?? ($_SERVER['SERVER_NAME'] ?? 'localhost');
    // SCRIPT_NAME (unlike REQUEST_URI) has no query string to worry about, e.g.
    // "/classroom-api/api/get_lessons.php" -> "/classroom-api".
    $scriptDir = rtrim(preg_replace('#/api/[^/]+$#', '', $_SERVER['SCRIPT_NAME'] ?? ''), '/');
    $baseUrl = $scheme . '://' . $host . $scriptDir;

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
                                    ? $baseUrl . '/api/download_attachment.php?file=' . rawurlencode($a['file_path'])
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
