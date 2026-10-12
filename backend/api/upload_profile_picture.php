<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/common.php';

// Sets or removes an account's profile picture - the server-side copy of
// ProfilePhotoRepository on Android. The app saves the photo on the phone
// first (offline-first, like everything else) and calls this to back it up,
// so login.php can hand it back after a reinstall or on a new phone.
//
//   Set:    multipart POST with fields "email" and "file" (JPEG/PNG/WebP, max 5 MB)
//   Remove: JSON POST {"email": "...", "remove": true}
//
// Same account rule as update_profile.php: identified by email, and only a
// real registered account (one with a password) can have a picture.

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    send_json(false, 'Method not allowed.', null, 405);
}

$maxBytes = 5 * 1024 * 1024;
$allowedTypes = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'];

// The photo belongs to the logged-in account (see "Login protection" in
// common.php); an email in the request is only checked against it.
$pdo = open_authenticated(null, $auth);

$hasFile = isset($_FILES['file']) && $_FILES['file']['error'] === UPLOAD_ERR_OK;
if ($hasFile) {
    $email  = strtolower(trim($_POST['email'] ?? ''));
    $remove = false;
} else {
    $input  = read_json_body();
    $email  = strtolower(trim($input['email'] ?? ''));
    $remove = !empty($input['remove']);
}

assert_same_account($auth, $email);

if (!$hasFile && !$remove) {
    send_json(false, 'A photo (or "remove") is required.');
}

$newName = null;
try {
    ensure_attachments_dir();

    $stmt = $pdo->prepare('SELECT id, profile_picture FROM users WHERE id = ?');
    $stmt->execute([$auth['id']]);
    $user = $stmt->fetch();
    if (!$user) {
        send_json(false, 'Account not found.');
    }

    if ($hasFile) {
        if ($_FILES['file']['size'] > $maxBytes) {
            send_json(false, 'The photo is too large (max 5 MB).');
        }
        // Trust the file's actual bytes, not the type the client claimed.
        $mime = finfo_file(finfo_open(FILEINFO_MIME_TYPE), $_FILES['file']['tmp_name']);
        if (!isset($allowedTypes[$mime])) {
            send_json(false, 'Only JPEG, PNG or WebP photos are allowed.');
        }
        $newName = store_uploaded_attachment($_FILES['file']['tmp_name'],
            'avatar_' . $user['id'] . '.' . $allowedTypes[$mime]);
    }

    $stmt = $pdo->prepare('UPDATE users SET profile_picture = ? WHERE id = ?');
    $stmt->execute([$newName, $user['id']]);

    // Only now that the row points at the new photo (or none), drop the old file.
    $old = $user['profile_picture'];
    if (!empty($old) && $old !== $newName && strpos($old, '/') === false && strpos($old, '\\') === false
            && strpos($old, '..') === false && (storage_enabled() || is_file(ATTACHMENTS_DIR . '/' . $old))) {
        remove_stored_file($old);
    }

    send_json(true, $newName !== null ? 'Profile picture updated.' : 'Profile picture removed.', [
        'profilePictureUrl' => $newName !== null ? public_file_url($newName) : null,
    ]);
} catch (Throwable $e) {
    // Don't leave a stored file behind that no account points to.
    if ($newName !== null) {
        remove_stored_file($newName);
    }
    error_log('upload_profile_picture.php error: ' . $e->getMessage());
    send_json(false, 'Unable to save the profile picture.', null, 500);
}
