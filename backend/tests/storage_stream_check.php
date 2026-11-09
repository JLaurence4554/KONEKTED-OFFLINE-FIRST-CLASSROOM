<?php
/**
 * Helper for tests/api_test.php (run by it with a LOW memory_limit, e.g. `php -d memory_limit=24M`).
 *
 * Moves a 40 MB file into storage and back using the server's own storage_put() / storage_open(), against the
 * stand-in for Supabase Storage whose address is in KONEKTED_SUPABASE_URL. With the file read into memory
 * (the old code) this dies at 24 MB; streamed, it needs almost none. Prints OK, or the reason it failed.
 */
$backend = dirname(__DIR__);
require $backend . '/config/database.php';
ob_start();
require $backend . '/api/common.php';
ob_end_clean();

if (!storage_enabled()) {
    echo "storage is not configured\n";
    exit(2);
}
$sizeMb = 40;
$source = tempnam(sys_get_temp_dir(), 'ktsrc');
$copy = tempnam(sys_get_temp_dir(), 'ktdst');
try {
    $fh = fopen($source, 'wb');
    for ($i = 0; $i < $sizeMb; $i++) {
        fwrite($fh, str_repeat(chr(65 + $i % 26) . random_bytes(255), 4096));   // 1 MB blocks
    }
    fclose($fh);

    storage_put('stream_check_' . getmypid() . '.mp4', $source);
    $object = storage_open('stream_check_' . getmypid() . '.mp4');
    if ($object === null) {
        echo "the file did not come back from storage\n";
        exit(1);
    }
    $out = fopen($copy, 'wb');
    stream_copy_to_stream($object['stream'], $out);
    fclose($object['stream']);
    fclose($out);

    if ($object['length'] !== filesize($source)) {
        echo 'Content-Length was ' . var_export($object['length'], true) . ' instead of ' . filesize($source) . "\n";
        exit(1);
    }
    if (hash_file('sha256', $source) !== hash_file('sha256', $copy)) {
        echo "the downloaded file differs from the uploaded one\n";
        exit(1);
    }
    echo 'OK peak=' . round(memory_get_peak_usage(true) / 1048576, 1) . "MB\n";
} catch (Throwable $e) {
    echo 'FAILED: ' . $e->getMessage() . "\n";
    exit(1);
} finally {
    @unlink($source);
    @unlink($copy);
}
