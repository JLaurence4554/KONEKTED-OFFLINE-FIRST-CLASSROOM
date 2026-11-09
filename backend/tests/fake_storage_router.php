<?php
/**
 * A stand-in for Supabase Storage, used only by tests/api_test.php (run it with `php -S ... fake_storage_router.php`).
 * Objects are kept as plain files in the folder named by the KT_FAKE_STORE_DIR environment variable.
 *
 * It behaves like the real service where it matters to us:
 *  - a new-style "sb_secret_..." key is accepted in the "apikey" header only; sending it as an
 *    "Authorization: Bearer" token is refused (401 "Invalid Compact JWS"), like Supabase does;
 *  - uploads are read as a stream, downloads are sent with Content-Length;
 *  - a missing object answers 404.
 */
$dir = getenv('KT_FAKE_STORE_DIR') ?: sys_get_temp_dir();
$headers = array_change_key_case(getallheaders(), CASE_LOWER);
$apikey = $headers['apikey'] ?? '';
$authorization = $headers['authorization'] ?? '';
$bearer = stripos($authorization, 'Bearer ') === 0 ? substr($authorization, 7) : '';
$method = $_SERVER['REQUEST_METHOD'];
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

header('Content-Type: application/json');
if ($bearer !== '' && strpos($bearer, 'eyJ') !== 0) {
    http_response_code(401);
    echo json_encode(['statusCode' => '401', 'error' => 'Unauthorized', 'message' => 'Invalid Compact JWS']);
    return true;
}
if ($apikey === '' && $bearer === '') {
    http_response_code(401);
    echo json_encode(['message' => 'No API key found in request']);
    return true;
}

if ($method === 'POST' && $path === '/storage/v1/bucket') {
    echo json_encode(['name' => 'attachments']);
    return true;
}
if (preg_match('#^/storage/v1/object/[^/]+/(.+)$#', $path, $m)) {
    $file = $dir . '/' . basename(rawurldecode($m[1]));
    if ($method === 'POST') {
        $in = fopen('php://input', 'rb');
        $out = fopen($file, 'wb');
        stream_copy_to_stream($in, $out);
        fclose($in);
        fclose($out);
        echo json_encode(['Key' => 'attachments/' . basename($file)]);
        return true;
    }
    if ($method === 'GET') {
        if (!is_file($file)) {
            http_response_code(404);
            echo json_encode(['statusCode' => '404', 'error' => 'not_found', 'message' => 'Object not found']);
            return true;
        }
        header('Content-Type: application/octet-stream');
        header('Content-Length: ' . filesize($file));
        readfile($file);
        return true;
    }
    if ($method === 'DELETE') {
        @unlink($file);
        echo '{}';
        return true;
    }
}
http_response_code(404);
echo '{}';
return true;
