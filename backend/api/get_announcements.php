<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    send_json(false, 'Method not allowed.', null, 405);
}

// Any logged-in person may ask, but only the class's teacher or one of its
// students gets its announcements (see "Login protection" in common.php).
$pdo = open_authenticated(null, $auth);

$classId = (int) ($_GET['classId'] ?? 0);
$afterId = (int) ($_GET['afterId'] ?? 0);
if ($classId === 0) {
    send_json(false, 'classId is required.');
}

try {
    ensure_announcements_schema($pdo);

    if (class_role($pdo, $auth, $classId) === null) {
        send_forbidden('You do not have access to this class.');
    }

    // Only what's new since the phone's last one. A phone that has none yet
    // (a new student) gets just the latest few, not the class's whole history.
    $stmt = $pdo->prepare(
        'SELECT id, class_id, title, message,
                CAST(EXTRACT(EPOCH FROM created_at) * 1000 AS BIGINT) AS created_at_ms
         FROM announcements WHERE class_id = ? AND id > ?
         ORDER BY id DESC LIMIT ' . ($afterId > 0 ? 100 : 5)
    );
    $stmt->execute([$classId, $afterId]);
    $rows = array_reverse($stmt->fetchAll());

    $result = [];
    foreach ($rows as $row) {
        $result[] = [
            'id'        => (int) $row['id'],
            'classId'   => (int) $row['class_id'],
            'title'     => $row['title'],
            'message'   => $row['message'],
            'createdAt' => (int) $row['created_at_ms'],
        ];
    }

    send_json(true, 'Announcements fetched.', ['announcements' => $result]);
} catch (Throwable $e) {
    error_log('get_announcements.php error: ' . $e->getMessage());
    send_json(false, 'Unable to connect to the server. Please try again.', null, 500);
}
