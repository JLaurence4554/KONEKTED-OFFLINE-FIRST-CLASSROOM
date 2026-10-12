<?php
/**
 * Checks that this computer can reach the database named by the KONEKTED_DB_* settings,
 * and that the KONEKTED tables are there. It only READS; it changes nothing.
 *
 *   C:\xampp\php\php.exe backend\supabase\check_connection.php
 *
 * See SUPABASE_SETUP.md for the settings to use with Supabase.
 */
require __DIR__ . '/../config/database.php';

echo "Trying: host=" . DB_HOST . " port=" . DB_PORT . " db=" . DB_NAME . " user=" . DB_USER
   . " ssl=" . (DB_SSLMODE !== '' ? DB_SSLMODE : '(not set)') . "\n";

try {
    $pdo = get_db_connection();
} catch (Throwable $e) {
    echo "\nCOULD NOT CONNECT: " . $e->getMessage() . "\n";
    echo "Check the host, user and password, and that KONEKTED_DB_SSLMODE=require is set for Supabase.\n";
    exit(1);
}

$encrypted = false;
try {
    $encrypted = (bool) $pdo->query('SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()')->fetchColumn();
} catch (Throwable $e) {
    // some poolers hide this; not important
}
echo "Connected. Connection is " . ($encrypted ? "encrypted." : "NOT shown as encrypted (fine on your own computer; Supabase should say encrypted).") . "\n";

$expected = ['users', 'classes', 'class_members', 'lessons', 'lesson_attachments', 'quizzes', 'questions',
             'quiz_results', 'attendance', 'join_requests', 'auth_tokens', 'login_attempts'];
$have = $pdo->query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")->fetchAll(PDO::FETCH_COLUMN);
$missing = array_values(array_diff($expected, $have));
if ($missing) {
    echo "\nMISSING TABLES: " . implode(', ', $missing) . "\n";
    echo "Run backend/supabase/schema_supabase.sql in the Supabase SQL Editor (once).\n";
    exit(2);
}
echo "All " . count($expected) . " KONEKTED tables are there.\n";

$users = (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
echo "Accounts in this database: $users\n";
echo "\nOK - this database is ready for the KONEKTED server.\n";
