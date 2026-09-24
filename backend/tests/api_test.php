<?php
/**
 * End-to-end test of the server's login protection.
 *
 *   C:\xampp\php\php.exe backend\tests\api_test.php
 *
 * It never touches your real data: it copies the API files to a temporary
 * folder, creates a throwaway database (classroom_sync_test) from schema.sql,
 * starts PHP's built-in web server on it, and then drives every endpoint over
 * HTTP - once the way the app uses them, and once the way an attacker would
 * (no login, someone else's login, the wrong role, another person's class...).
 * Everything is removed at the end. Exit code 0 = all checks passed.
 *
 * Needs the same PostgreSQL login as config/database.php (or KONEKTED_DB_*
 * environment variables) and the curl, pdo_pgsql and fileinfo PHP extensions.
 */

const TEST_DB = 'classroom_sync_test';
const TEST_PORT = 8097;

$backend = realpath(__DIR__ . '/..');
require $backend . '/config/database.php';   // only for the PostgreSQL login + DB_* names

$passed = 0;
$failed = 0;
function expect(string $label, bool $ok, string $detail = ''): void {
    global $passed, $failed;
    if ($ok) {
        $passed++;
        echo "  PASS  $label\n";
    } else {
        $failed++;
        echo "  FAIL  $label" . ($detail !== '' ? "  -> $detail" : '') . "\n";
    }
}
function section(string $title): void {
    echo "\n$title\n";
}
function rrmdir(string $dir): void {
    if (!is_dir($dir)) return;
    foreach (scandir($dir) as $f) {
        if ($f === '.' || $f === '..') continue;
        $p = $dir . DIRECTORY_SEPARATOR . $f;
        is_dir($p) ? rrmdir($p) : @unlink($p);
    }
    @rmdir($dir);
}
function rcopy(string $from, string $to): void {
    @mkdir($to, 0777, true);
    foreach (scandir($from) as $f) {
        if ($f === '.' || $f === '..') continue;
        $src = $from . DIRECTORY_SEPARATOR . $f;
        is_dir($src) ? rcopy($src, $to . DIRECTORY_SEPARATOR . $f) : copy($src, $to . DIRECTORY_SEPARATOR . $f);
    }
}

// ---------------------------------------------------------------- database
$adminDsn = 'pgsql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=postgres';
$admin = new PDO($adminDsn, DB_USER, DB_PASS, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$admin->exec('DROP DATABASE IF EXISTS ' . TEST_DB . ' WITH (FORCE)');
$admin->exec('CREATE DATABASE ' . TEST_DB);
$db = new PDO('pgsql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . TEST_DB, DB_USER, DB_PASS,
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
$schema = implode("\n", array_filter(explode("\n", file_get_contents($backend . '/schema.sql')),
    fn($line) => !str_starts_with(ltrim($line), '\\')));       // drop psql-only "\c dbname" lines
$db->exec($schema);

// ------------------------------------------------------------------ server
$tmp = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'konekted_api_test_' . getmypid();
rrmdir($tmp);
rcopy($backend . '/api', $tmp . '/api');
rcopy($backend . '/config', $tmp . '/config');

function start_server(string $root, int $port, array $extraEnv = []) {
    $env = array_merge(getenv(), ['KONEKTED_DB_NAME' => TEST_DB], $extraEnv);
    $log = $root . DIRECTORY_SEPARATOR . "server_$port.log";
    $proc = proc_open([PHP_BINARY, '-S', "127.0.0.1:$port", '-t', $root],
        [0 => ['pipe', 'r'], 1 => ['file', $log, 'w'], 2 => ['file', $log, 'a']], $pipes, $root, $env);
    for ($i = 0; $i < 50; $i++) {
        $fp = @fsockopen('127.0.0.1', $port, $errno, $errstr, 0.2);
        if ($fp) { fclose($fp); return $proc; }
        usleep(200000);
    }
    throw new RuntimeException("test web server did not start (see $log)");
}
$server = start_server($tmp, TEST_PORT);
$base = 'http://127.0.0.1:' . TEST_PORT . '/api/';

function call(string $method, string $endpoint, $body = null, ?string $token = null, array $headers = [], bool $multipart = false, ?string $base2 = null): array {
    global $base;
    $ch = curl_init(($base2 ?? $base) . $endpoint);
    $h = $headers;
    if ($token !== null) $h[] = 'Authorization: Bearer ' . $token;
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_HEADER => true, CURLOPT_TIMEOUT => 30]);
    if ($method === 'POST') {
        curl_setopt($ch, CURLOPT_POST, true);
        if ($multipart) {
            curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        } else {
            $h[] = 'Content-Type: application/json';
            curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body ?? new stdClass()));
        }
    }
    curl_setopt($ch, CURLOPT_HTTPHEADER, $h);
    $raw = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $hs = (int) curl_getinfo($ch, CURLINFO_HEADER_SIZE);
    curl_close($ch);
    $bodyStr = substr((string) $raw, $hs);
    return ['status' => $status, 'json' => json_decode($bodyStr, true), 'body' => $bodyStr, 'head' => substr((string) $raw, 0, $hs)];
}
function ok(array $r): bool { return $r['status'] === 200 && ($r['json']['success'] ?? false) === true; }
function code(array $r): string { return (string) ($r['json']['data']['code'] ?? ''); }
function data(array $r) { return $r['json']['data'] ?? null; }

try {

// ============================================================ 1. no login
section('1. Nobody logged in: every protected endpoint must answer 401');
$protectedGet = [
    'find_class.php?code=ABC', 'get_lessons.php?classId=1', 'get_quizzes.php?lessonId=1', 'get_quiz_results.php?quizId=1',
    'get_join_requests.php', 'get_student_classes.php', 'get_teacher_classes.php', 'download_attachment.php?file=x.pdf',
];
$protectedPost = [
    'sync_class.php', 'join_class.php', 'leave_class.php', 'delete_class.php', 'sync_lesson.php', 'sync_lesson_attachment.php',
    'delete_lesson.php', 'delete_lesson_attachment.php', 'sync_quiz.php', 'sync_question.php', 'delete_quiz.php',
    'sync_quiz_result.php', 'sync_join_request.php', 'approve_join_request.php', 'sync_attendance.php', 'update_profile.php',
    'upload_profile_picture.php', 'logout.php',
];
$badTokens = [null, 'kt_' . str_repeat('a', 64), 'short', 'x y z'];
$allRefused = true; $worst = '';
foreach ($protectedGet as $ep) foreach ($badTokens as $t) {
    $r = call('GET', $ep, null, $t);
    if ($r['status'] !== 401 || code($r) !== 'AUTH_REQUIRED') { $allRefused = false; $worst = "GET $ep token=" . var_export($t, true) . " -> {$r['status']}"; }
}
foreach ($protectedPost as $ep) foreach ($badTokens as $t) {
    $r = call('POST', $ep, ['teacherEmail' => 'x@y.z', 'studentEmail' => 'x@y.z', 'email' => 'x@y.z'], $t);
    if ($r['status'] !== 401 || code($r) !== 'AUTH_REQUIRED') { $allRefused = false; $worst = "POST $ep token=" . var_export($t, true) . " -> {$r['status']}"; }
}
expect(count($protectedGet) + count($protectedPost) . ' endpoints x 4 kinds of missing/invalid token all refused with 401', $allRefused, $worst);
$r = call('GET', 'login.php');
expect('login.php is reachable without a token (405 for GET, not 401)', $r['status'] === 405);
$r = call('POST', 'get_lessons.php');
expect('wrong HTTP method is still refused', $r['status'] === 401 || $r['status'] === 405);

// ====================================================== 2. accounts + login
section('2. Sign-up and login');
function register(string $name, string $email, string $role, string $password = 'secret123'): array {
    return call('POST', 'register.php', ['fullName' => $name, 'email' => $email, 'password' => $password, 'role' => $role]);
}
$reg = [];
foreach ([['T1', 't1@school.test', 'Teacher'], ['T2', 't2@school.test', 'Teacher'], ['S1', 's1@school.test', 'Student'],
          ['S2', 's2@school.test', 'Student'], ['S3', 's3@school.test', 'Student']] as [$n, $e, $role]) {
    $reg[$n] = register($n . ' Name', $e, $role);
}
$tok = [];
foreach ($reg as $n => $r) {
    $tok[$n] = data($r)['token'] ?? null;
    expect("register $n returns a login token", ok($r) && is_string($tok[$n]) && preg_match('/^kt_[0-9a-f]{64}$/', $tok[$n]) === 1);
}
$id = [];
foreach ($reg as $n => $r) $id[$n] = (int) (data($r)['serverId'] ?? 0);
$r = register('Dup', 't1@school.test', 'Teacher');
expect('registering an email that already has an account is refused', !ok($r) && !isset(data($r)['token']));
$r = register('Short', 'short@school.test', 'Student', '12345');
expect('a password shorter than 6 characters is refused', !ok($r));

$r = call('POST', 'login.php', ['email' => 'T1@School.test', 'password' => 'secret123']);
$loginTok = data($r)['token'] ?? '';
expect('login works (email not case-sensitive) and returns a token, name and role', ok($r) && $loginTok !== '' && data($r)['role'] === 'Teacher');
expect('a login token is different from the sign-up token', $loginTok !== $tok['T1']);
$wrong = call('POST', 'login.php', ['email' => 't1@school.test', 'password' => 'wrong-password']);
$unknown = call('POST', 'login.php', ['email' => 'nobody@school.test', 'password' => 'whatever1']);
expect('wrong password: refused, no token', ok($wrong) === false && !isset(data($wrong)['token']));
expect('wrong password and unknown email get the SAME answer (no way to find out who has an account)',
    $wrong['json']['message'] === $unknown['json']['message'] && $wrong['status'] === $unknown['status']);

$hash = $db->query("SELECT COUNT(*) FROM auth_tokens WHERE token_hash = " . $db->quote($loginTok))->fetchColumn();
$hash2 = $db->query("SELECT COUNT(*) FROM auth_tokens WHERE token_hash = " . $db->quote(hash('sha256', $loginTok)))->fetchColumn();
expect('the database keeps only a hash of the token, never the token itself', (int) $hash === 0 && (int) $hash2 === 1);

$r = call('GET', 'get_teacher_classes.php', null, $loginTok);
expect('a fresh login token works', ok($r));
$r = call('GET', 'get_teacher_classes.php', null, null, ['X-Auth-Token: ' . $loginTok]);
expect('the X-Auth-Token header works too (for hosts that strip Authorization)', ok($r));

// ======================================================== 3. attempt limits
section('3. Guessing passwords is limited');
for ($i = 0; $i < 8; $i++) call('POST', 'login.php', ['email' => 's3@school.test', 'password' => "wrong$i"]);
$r = call('POST', 'login.php', ['email' => 's3@school.test', 'password' => 'secret123']);
expect('after 8 wrong passwords the email is locked - even the RIGHT password is refused (429)',
    $r['status'] === 429 && code($r) === 'RATE_LIMITED');
$r = call('POST', 'login.php', ['email' => 's2@school.test', 'password' => 'secret123']);
expect('other people are not affected by that lock', ok($r));
$db->exec("UPDATE login_attempts SET attempted_at = NOW() - INTERVAL '20 minutes' WHERE email = 's3@school.test'");
$r = call('POST', 'login.php', ['email' => 's3@school.test', 'password' => 'secret123']);
expect('the lock ends after the waiting time', ok($r));
$tok['S3'] = data($r)['token'];
for ($i = 0; $i < 5; $i++) call('POST', 'login.php', ['email' => 's2@school.test', 'password' => "oops$i"]);
$r = call('POST', 'login.php', ['email' => 's2@school.test', 'password' => 'secret123']);
expect('a few mistakes followed by the right password work, and are forgiven', ok($r));
for ($i = 0; $i < 5; $i++) call('POST', 'login.php', ['email' => 's2@school.test', 'password' => "again$i"]);
$r = call('POST', 'login.php', ['email' => 's2@school.test', 'password' => 'secret123']);
expect('...so 5 more mistakes later there is still no lock', ok($r));

// ================================================= 4. classes and joining
section('4. Classes, joining and approval');
$T1 = $tok['T1']; $T2 = $tok['T2']; $S1 = $tok['S1']; $S2 = $tok['S2']; $S3 = $tok['S3'];
$r = call('POST', 'sync_class.php', ['className' => 'Math', 'subject' => 'Algebra', 'section' => 'A', 'schoolYear' => '2026', 'classCode' => 'MATH01', 'teacherName' => 'T1', 'teacherEmail' => 't1@school.test'], $T1);
$classId = (int) (data($r)['serverId'] ?? 0);
expect('a teacher can create a class (old-style request with teacherEmail still works)', ok($r) && $classId > 0);
$r = call('POST', 'sync_class.php', ['className' => 'Steal', 'classCode' => 'MATH01'], $T2);
expect('another teacher cannot take over that class code', !ok($r));
$r = call('POST', 'sync_class.php', ['className' => 'Hack', 'classCode' => 'HACK01'], $S1);
expect('a student cannot create a class (403)', $r['status'] === 403 && code($r) === 'WRONG_ROLE');
$r = call('POST', 'sync_class.php', ['className' => 'Fake', 'classCode' => 'FAKE01', 'teacherEmail' => 't2@school.test'], $T1);
expect('a request naming someone ELSE\'s email is refused (403)', $r['status'] === 403 && code($r) === 'ACCOUNT_MISMATCH');

$r = call('GET', 'find_class.php?code=math01', null, $S1);
expect('a student can look a class up by its code', ok($r) && data($r)['className'] === 'Math');
$r = call('GET', 'find_class.php?code=NOPE99', null, $S1);
expect('an unknown code is a normal "not found"', !ok($r) && $r['status'] === 200);

$r = call('POST', 'sync_join_request.php', ['classCode' => 'MATH01', 'studentName' => 'S1', 'studentEmail' => 's1@school.test'], $S1);
expect('a student can ask to join', ok($r));
$r = call('POST', 'sync_join_request.php', ['classCode' => 'MATH01', 'studentEmail' => 's1@school.test'], $S1);
expect('asking twice is refused politely', !ok($r));
$r = call('POST', 'sync_join_request.php', ['classCode' => 'MATH01', 'studentEmail' => 's2@school.test'], $S1);
expect('a student cannot ask on someone else\'s behalf (403)', $r['status'] === 403 && code($r) === 'ACCOUNT_MISMATCH');
$r = call('POST', 'sync_join_request.php', ['classCode' => 'MATH01'], $T1);
expect('a teacher cannot send a join request (403)', $r['status'] === 403);

$r = call('GET', 'get_join_requests.php?teacherEmail=t1@school.test', null, $T1);
expect('the class teacher sees the pending request', ok($r) && count(data($r)['requests']) === 1 && data($r)['requests'][0]['studentEmail'] === 's1@school.test');
$r = call('GET', 'get_join_requests.php', null, $T2);
expect('another teacher sees none of it', ok($r) && count(data($r)['requests']) === 0);
$r = call('GET', 'get_join_requests.php?teacherEmail=t1@school.test', null, $T2);
expect('...and cannot pretend to be the class teacher by passing the email (403)', $r['status'] === 403);
$r = call('GET', 'get_join_requests.php', null, $S1);
expect('a student cannot read join requests (403)', $r['status'] === 403);

$r = call('POST', 'approve_join_request.php', ['classCode' => 'MATH01', 'studentEmail' => 's1@school.test'], $T2);
expect('a teacher who does not own the class cannot approve (403)', $r['status'] === 403);
$r = call('POST', 'approve_join_request.php', ['classCode' => 'MATH01', 'studentEmail' => 's1@school.test'], $S1);
expect('a student cannot approve their own request (403)', $r['status'] === 403 && code($r) === 'WRONG_ROLE');
$r = call('POST', 'approve_join_request.php', ['classCode' => 'MATH01', 'studentEmail' => 's1@school.test'], $T1);
expect('the class teacher can approve', ok($r));
$member = $db->query("SELECT COUNT(*) FROM class_members WHERE class_id = $classId AND student_id = {$id['S1']}")->fetchColumn();
expect('...and the student is now a member', (int) $member === 1);

$r = call('POST', 'join_class.php', ['classCode' => 'MATH01', 'studentName' => 'S2', 'studentEmail' => 's2@school.test'], $S2);
expect('join_class.php still adds the logged-in student', ok($r));
$r = call('GET', 'get_student_classes.php?studentEmail=s1@school.test', null, $S1);
expect('a student sees their own classes', ok($r) && count(data($r)['classes']) === 1);
$r = call('GET', 'get_student_classes.php?studentEmail=s1@school.test', null, $S3);
expect('...and cannot list another student\'s classes by passing their email (403)', $r['status'] === 403);
$r = call('GET', 'get_student_classes.php', null, $S3);
expect('a student with no classes gets an empty list', ok($r) && count(data($r)['classes']) === 0);
$r = call('GET', 'get_teacher_classes.php', null, $T1);
expect('a teacher sees their classes with the roster', ok($r) && count(data($r)['classes']) === 1 && count(data($r)['classes'][0]['students']) === 2);
$r = call('GET', 'get_teacher_classes.php', null, $T2);
expect('another teacher sees nothing of it', ok($r) && count(data($r)['classes']) === 0);

// ======================================================== 5. lessons + files
section('5. Lessons and files');
$r = call('POST', 'sync_lesson.php', ['classId' => $classId, 'title' => 'Lesson 1', 'body' => 'Hello', 'teacherEmail' => 't1@school.test'], $T1);
$lessonId = (int) (data($r)['serverId'] ?? 0);
expect('the class teacher can add a lesson', ok($r) && $lessonId > 0);
$r = call('POST', 'sync_lesson.php', ['classId' => $classId, 'title' => 'Sneaky', 'body' => 'x'], $T2);
expect('another teacher cannot add a lesson to that class', !ok($r));
$r = call('POST', 'sync_lesson.php', ['classId' => $classId, 'title' => 'Sneaky', 'body' => 'x'], $S1);
expect('a student cannot add a lesson (403)', $r['status'] === 403);

$pdfPath = $tmp . '/report.pdf';   file_put_contents($pdfPath, "%PDF-1.4\n% test file\n%%EOF\n");
$phpPath = $tmp . '/evil.php';     file_put_contents($phpPath, '<?php echo "pwned";');
$r = call('POST', 'sync_lesson_attachment.php', ['lessonId' => $lessonId, 'type' => 'FILE', 'name' => 'report.pdf', 'teacherEmail' => 't1@school.test', 'file' => new CURLFile($pdfPath, 'application/pdf', 'report.pdf')], $T1, [], true);
$attPdf = (int) (data($r)['serverId'] ?? 0);
expect('the teacher can upload a file', ok($r) && $attPdf > 0);
$r = call('POST', 'sync_lesson_attachment.php', ['lessonId' => $lessonId, 'type' => 'FILE', 'name' => 'evil.php', 'file' => new CURLFile($phpPath, 'text/x-php', 'evil.php')], $T1, [], true);
$attPhp = (int) (data($r)['serverId'] ?? 0);
expect('a script upload is accepted but stored as harmless .bin', ok($r) && $attPhp > 0);
$r = call('POST', 'sync_lesson_attachment.php', ['lessonId' => $lessonId, 'type' => 'FILE', 'name' => 'x.pdf', 'file' => new CURLFile($pdfPath, 'application/pdf', 'x.pdf')], $T2, [], true);
expect('another teacher cannot upload into that lesson', !ok($r));
$r = call('POST', 'sync_lesson_attachment.php', ['lessonId' => $lessonId, 'type' => 'VIDEO_LINK', 'name' => 'https://example.com/v'], $T1);
expect('a video link attachment works', ok($r));

$r = call('GET', "get_lessons.php?classId=$classId", null, $S1);
$lessons = data($r)['lessons'] ?? [];
expect('a member student gets the class\'s lessons', ok($r) && count($lessons) === 1 && count($lessons[0]['attachments']) === 3);
$urls = [];
foreach ($lessons[0]['attachments'] ?? [] as $a) if (!empty($a['url'])) $urls[$a['name']] = $a['url'];
$phpStored = ''; $pdfUrl = $urls['report.pdf'] ?? '';
if (isset($urls['evil.php']) && preg_match('/file=([^&]+)/', $urls['evil.php'], $m)) $phpStored = urldecode($m[1]);
expect('the stored name of the script ends in .bin', str_ends_with($phpStored, '.bin'), $phpStored);
$r = call('GET', "get_lessons.php?classId=$classId", null, $T1);
expect('the class teacher gets them too', ok($r) && count(data($r)['lessons']) === 1);
$r = call('GET', "get_lessons.php?classId=$classId", null, $S3);
expect('a student who is NOT in the class is refused (403)', $r['status'] === 403 && code($r) === 'FORBIDDEN');
$r = call('GET', "get_lessons.php?classId=$classId", null, $T2);
expect('so is another teacher (403)', $r['status'] === 403);
$r = call('GET', 'get_lessons.php?classId=99999', null, $S1);
expect('a class that no longer exists gives an empty list, as before', ok($r) && count(data($r)['lessons']) === 0);

$pdfFile = ''; if (preg_match('/file=([^&]+)/', $pdfUrl, $m)) $pdfFile = urldecode($m[1]);
$dl = fn(string $f, ?string $t) => call('GET', 'download_attachment.php?file=' . rawurlencode($f), null, $t);
$r = $dl($pdfFile, $S1);
expect('a member student can download the file', $r['status'] === 200 && str_contains($r['body'], '%PDF-1.4'));
expect('the download is sent as an attachment, not shown in a page', stripos($r['head'], 'Content-Disposition: attachment') !== false && stripos($r['head'], 'nosniff') !== false);
$r = $dl($pdfFile, $T1);
expect('the class teacher can download it', $r['status'] === 200);
$r = $dl($pdfFile, $S3);
expect('a student outside the class cannot (403)', $r['status'] === 403);
$r = $dl($pdfFile, $T2);
expect('another teacher cannot (403)', $r['status'] === 403);
$r = $dl($pdfFile, null);
expect('nobody without a login can (401)', $r['status'] === 401);
$r = $dl('../config/database.php', $T1);
expect('a path trick is refused (400)', $r['status'] === 400);
file_put_contents($tmp . '/attachments/orphan_123.pdf', 'orphan');
$r = $dl('orphan_123.pdf', $T1);
expect('a file that no lesson or profile points to is nobody\'s to download (403)', $r['status'] === 403);

$png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==');
$pngPath = $tmp . '/me.png'; file_put_contents($pngPath, $png);
$r = call('POST', 'upload_profile_picture.php', ['email' => 's1@school.test', 'file' => new CURLFile($pngPath, 'image/png', 'me.png')], $S1, [], true);
$avatarUrl = data($r)['profilePictureUrl'] ?? '';
expect('a person can set their own profile picture', ok($r) && $avatarUrl !== '');
$r = call('POST', 'upload_profile_picture.php', ['email' => 's1@school.test', 'file' => new CURLFile($pngPath, 'image/png', 'me.png')], $S2, [], true);
expect('...but not someone else\'s (403)', $r['status'] === 403);
$avatarFile = ''; if (preg_match('/file=([^&]+)/', $avatarUrl, $m)) $avatarFile = urldecode($m[1]);
$r = $dl($avatarFile, $T2);
expect('any logged-in person can see a profile picture', $r['status'] === 200);
$r = $dl($avatarFile, null);
expect('a profile picture needs a login too (401)', $r['status'] === 401);

// ================================================== 6. quizzes and results
section('6. Quizzes, answers and scores');
$r = call('POST', 'sync_quiz.php', ['lessonId' => $lessonId, 'title' => 'Quiz 1', 'instructions' => 'Do it', 'passingScore' => 60, 'status' => 'Published', 'teacherEmail' => 't1@school.test'], $T1);
$quizId = (int) (data($r)['serverId'] ?? 0);
expect('the class teacher can create a quiz', ok($r) && $quizId > 0);
$r = call('POST', 'sync_quiz.php', ['lessonId' => $lessonId, 'title' => 'Quiz X'], $T2);
expect('another teacher cannot', !ok($r));
$r = call('POST', 'sync_question.php', ['quizId' => $quizId, 'question' => '2+2?', 'questionType' => 'MULTIPLE_CHOICE', 'choiceA' => '3', 'choiceB' => '4', 'choiceC' => '5', 'choiceD' => '6', 'correctAnswer' => 'B', 'orderIndex' => 1, 'teacherEmail' => 't1@school.test'], $T1);
expect('the teacher can add a question', ok($r));
$r = call('POST', 'sync_question.php', ['quizId' => $quizId, 'question' => 'Capital of France?', 'questionType' => 'IDENTIFICATION', 'answerText' => 'Paris', 'orderIndex' => 2], $T1);
expect('...an identification question', ok($r));
$r = call('POST', 'sync_question.php', ['quizId' => $quizId, 'question' => 'Cheat', 'correctAnswer' => 'A'], $T2);
expect('another teacher cannot add questions', !ok($r));

$r = call('GET', "get_quizzes.php?lessonId=$lessonId", null, $S1);
expect('a member student gets the quizzes with their questions', ok($r) && count(data($r)['quizzes']) === 1 && count(data($r)['quizzes'][0]['questions']) === 2);
$r = call('GET', "get_quizzes.php?lessonId=$lessonId", null, $S3);
expect('a student outside the class does not (403)', $r['status'] === 403);
$r = call('GET', 'get_quizzes.php?lessonId=99999', null, $S1);
expect('a lesson that no longer exists gives an empty list, as before', ok($r) && count(data($r)['quizzes']) === 0);

$r = call('POST', 'sync_quiz_result.php', ['quizId' => $quizId, 'studentEmail' => 's1@school.test', 'score' => 50, 'correctCount' => 1, 'totalQuestions' => 2], $S1);
expect('a member student can submit their result', ok($r));
$r = call('POST', 'sync_quiz_result.php', ['quizId' => $quizId, 'studentEmail' => 's1@school.test', 'score' => 100, 'correctCount' => 2, 'totalQuestions' => 2], $S1);
expect('...and update it (one result per student)', ok($r));
$r = call('POST', 'sync_quiz_result.php', ['quizId' => $quizId, 'studentEmail' => 's3@school.test', 'score' => 100, 'correctCount' => 2, 'totalQuestions' => 2], $S3);
expect('a student outside the class cannot submit a result (403)', $r['status'] === 403);
$r = call('POST', 'sync_quiz_result.php', ['quizId' => $quizId, 'studentEmail' => 's2@school.test', 'score' => 0, 'correctCount' => 0, 'totalQuestions' => 2], $S1);
expect('a student cannot submit a result in someone else\'s name (403)', $r['status'] === 403 && code($r) === 'ACCOUNT_MISMATCH');
$r = call('POST', 'sync_quiz_result.php', ['quizId' => $quizId, 'score' => 100, 'correctCount' => 2, 'totalQuestions' => 2], $T1);
expect('a teacher cannot submit a result (403)', $r['status'] === 403);

$r = call('GET', "get_quiz_results.php?quizId=$quizId", null, $T1);
expect('the class teacher reads the results', ok($r) && count(data($r)['results']) === 1 && (int) data($r)['results'][0]['score'] === 100);
$r = call('GET', "get_quiz_results.php?quizId=$quizId", null, $T2);
expect('another teacher cannot (403)', $r['status'] === 403);
$r = call('GET', "get_quiz_results.php?quizId=$quizId", null, $S1);
expect('students cannot read everyone\'s scores (403)', $r['status'] === 403 && code($r) === 'WRONG_ROLE');

// =========================================================== 7. attendance
section('7. Attendance');
$att = ['classId' => $classId, 'studentName' => 'S1', 'studentEmail' => 's1@school.test', 'status' => 'Present', 'date' => '2026-09-24', 'teacherEmail' => 't1@school.test'];
$r = call('POST', 'sync_attendance.php', $att, $T1);
expect('the class teacher can mark attendance', ok($r));
$r = call('POST', 'sync_attendance.php', $att, $T2);
expect('another teacher cannot', !ok($r));
$r = call('POST', 'sync_attendance.php', $att, $S1);
expect('a student cannot mark themselves present (403)', $r['status'] === 403);

// ===================================================== 8. profile + logout
section('8. Profile and logging out');
$r = call('POST', 'update_profile.php', ['email' => 's1@school.test', 'fullName' => 'S1 Renamed'], $S1);
expect('a person can change their own name', ok($r));
$name = $db->query("SELECT full_name FROM users WHERE id = {$id['S1']}")->fetchColumn();
expect('...and it changed', $name === 'S1 Renamed');
$r = call('POST', 'update_profile.php', ['email' => 's1@school.test', 'fullName' => 'Hacked'], $S2);
expect('nobody can rename someone else (403)', $r['status'] === 403);
$name = $db->query("SELECT full_name FROM users WHERE id = {$id['S1']}")->fetchColumn();
expect('...and the name did not change', $name === 'S1 Renamed');

$r = call('POST', 'login.php', ['email' => 's1@school.test', 'password' => 'secret123']);
$S1b = data($r)['token'];
$r = call('POST', 'logout.php', [], $S1);
expect('logging out works', ok($r));
$r = call('GET', 'get_student_classes.php', null, $S1);
expect('the token used to log out stops working at once (401)', $r['status'] === 401);
$r = call('GET', 'get_student_classes.php', null, $S1b);
expect('...but the same person\'s other phone stays logged in', ok($r));
$r = call('POST', 'logout.php', ['allDevices' => true], $S1b);
$r2 = call('GET', 'get_student_classes.php', null, $S1b);
expect('"log out everywhere" ends every phone\'s login', ok($r) && $r2['status'] === 401);

// ====================================================== 9. token lifetime
section('9. Token lifetime');
$r = call('POST', 'login.php', ['email' => 't2@school.test', 'password' => 'secret123']);
$T2b = data($r)['token'];
$h = hash('sha256', $T2b);
$db->exec("UPDATE auth_tokens SET expires_at = NOW() - INTERVAL '1 minute' WHERE token_hash = '$h'");
$r = call('GET', 'get_teacher_classes.php', null, $T2b);
expect('an expired token is refused (401)', $r['status'] === 401);
$r = call('POST', 'login.php', ['email' => 't2@school.test', 'password' => 'secret123']);
$T2c = data($r)['token'];
$h = hash('sha256', $T2c);
$db->exec("UPDATE auth_tokens SET last_used_at = NOW() - INTERVAL '2 hours', expires_at = NOW() + INTERVAL '1 day' WHERE token_hash = '$h'");
call('GET', 'get_teacher_classes.php', null, $T2c);
$days = (float) $db->query("SELECT EXTRACT(EPOCH FROM (expires_at - NOW())) / 86400 FROM auth_tokens WHERE token_hash = '$h'")->fetchColumn();
expect('using a token pushes its expiry out to about 90 days again', $days > 89 && $days < 91, (string) $days);
for ($i = 0; $i < 13; $i++) call('POST', 'login.php', ['email' => 't2@school.test', 'password' => 'secret123']);
$n = (int) $db->query("SELECT COUNT(*) FROM auth_tokens WHERE user_id = {$id['T2']}")->fetchColumn();
expect('one person keeps at most 10 phones logged in (the oldest logins are dropped)', $n === 10, (string) $n);
$r = call('POST', 'login.php', ['email' => 't2@school.test', 'password' => 'secret123']);
$T2 = data($r)['token']; // the sign-up token was one of the dropped ones; use a fresh login for what follows

// ============================================================ 10. deleting
section('10. Deleting: only the owner');
$r = call('POST', 'delete_lesson_attachment.php', ['attachmentId' => $attPdf], $T2);
$still = (int) $db->query("SELECT COUNT(*) FROM lesson_attachments WHERE id = $attPdf")->fetchColumn();
expect('another teacher cannot delete a file (it is still there)', $still === 1);
$r = call('POST', 'delete_lesson_attachment.php', ['attachmentId' => $attPdf, 'teacherEmail' => 't1@school.test'], $T1);
$gone = (int) $db->query("SELECT COUNT(*) FROM lesson_attachments WHERE id = $attPdf")->fetchColumn();
expect('the owner can', ok($r) && $gone === 0);
$r = call('POST', 'delete_quiz.php', ['quizId' => $quizId], $T2);
expect('another teacher cannot delete the quiz', !ok($r));
$r = call('POST', 'delete_quiz.php', ['quizId' => $quizId], $S2);
expect('a student cannot (403)', $r['status'] === 403);
$r = call('POST', 'delete_lesson.php', ['lessonId' => $lessonId], $T2);
expect('another teacher cannot delete the lesson', !ok($r));
$r = call('POST', 'delete_quiz.php', ['quizId' => $quizId, 'teacherEmail' => 't1@school.test'], $T1);
expect('the owner can delete the quiz', ok($r));
$r = call('POST', 'delete_lesson.php', ['lessonId' => $lessonId, 'teacherEmail' => 't1@school.test'], $T1);
expect('...and the lesson', ok($r));
$r = call('POST', 'leave_class.php', ['classCode' => 'MATH01', 'studentEmail' => 's2@school.test'], $S2);
expect('a student can leave a class', ok($r));
$r = call('POST', 'leave_class.php', ['classCode' => 'MATH01', 'studentEmail' => 's2@school.test'], $S2);
expect('...once', !ok($r));
$r = call('POST', 'delete_class.php', ['classId' => $classId], $T2);
expect('another teacher cannot delete the class', !ok($r));
$r = call('POST', 'delete_class.php', ['classId' => $classId, 'teacherEmail' => 't1@school.test'], $T1);
expect('the owner can', ok($r));

// ================================================================ 11. https
section('11. HTTPS enforcement (KONEKTED_REQUIRE_HTTPS=1)');
$server2 = start_server($tmp, TEST_PORT + 1, ['KONEKTED_REQUIRE_HTTPS' => '1']);
$base2 = 'http://127.0.0.1:' . (TEST_PORT + 1) . '/api/';
$r = call('POST', 'login.php', ['email' => 't1@school.test', 'password' => 'secret123'], null, [], false, $base2);
expect('plain HTTP is refused (426)', $r['status'] === 426 && code($r) === 'HTTPS_REQUIRED');
$r = call('POST', 'login.php', ['email' => 't1@school.test', 'password' => 'secret123'], null, ['X-Forwarded-Proto: https'], false, $base2);
expect('the same request through an HTTPS proxy (ngrok) works', ok($r));
proc_terminate($server2);

} catch (Throwable $e) {
    $failed++;
    echo "\n  ERROR  the test stopped: " . $e->getMessage() . "\n  " . $e->getFile() . ':' . $e->getLine() . "\n";
}

// ------------------------------------------------------------------ cleanup
proc_terminate($server);
usleep(300000);
unset($db);
try { $admin->exec('DROP DATABASE IF EXISTS ' . TEST_DB . ' WITH (FORCE)'); } catch (Throwable $e) { /* leave it */ }
rrmdir($tmp);

echo "\n" . str_repeat('=', 60) . "\n";
echo $failed === 0 ? "ALL $passed CHECKS PASSED\n" : "$failed FAILED, $passed passed\n";
exit($failed === 0 ? 0 : 1);
