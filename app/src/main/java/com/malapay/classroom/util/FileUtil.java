package com.malapay.classroom.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles lesson attachments entirely offline: the picked file's bytes are
 * copied into app-private storage immediately (so the app doesn't depend on
 * the original document staying reachable), and later opened through a
 * {@link FileProvider} content URI, since a plain file:// URI can't be
 * shared with other apps on modern Android.
 */
public final class FileUtil {

    private static final String ATTACHMENTS_DIR = "lesson_attachments";

    private FileUtil() {
        // no instances
    }

    /** Reads the user-facing display name of a picked document URI. */
    public static String getFileName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Falls through to the default name below.
        }
        return name != null ? name : "attachment";
    }

    public static String getMimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        return type != null ? type : "*/*";
    }

    /**
     * Copies the content behind {@code uri} into app-private storage and
     * returns the absolute path of the new file. Throws IOException on any
     * read/write failure so the caller can surface a clear error.
     */
    public static String copyToInternalStorage(Context context, Uri uri, String displayName) throws IOException {
        File dir = getAttachmentsDir(context);
        String uniqueName = System.currentTimeMillis() + "_" + sanitize(displayName);
        File outFile = new File(dir, uniqueName);

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) {
                throw new IOException("Could not open the selected file");
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        }

        return outFile.getAbsolutePath();
    }

    /**
     * Downloads the content at {@code url} into app-private
     * {@code lesson_attachments/<timestamp>_<sanitizedName>} and returns the
     * absolute path of the downloaded file. The stored name is derived from the
     * last path segment of the URL when possible, otherwise falls back to
     * {@code attachment} as the display name.
     *
     * Callers should delete any previously-downloaded copy for the same logical
     * lesson before calling this, to avoid accumulating duplicates.
     *
     * @throws IOException on network or write failure
     */
    public static String copyFromUrl(Context context, String url, @Nullable String hintName) throws IOException {
        File dir = getAttachmentsDir(context);
        String displayName = (hintName != null && !hintName.isEmpty()) ? hintName
                : lastPathSegment(url);
        String uniqueName = System.currentTimeMillis() + "_" + sanitize(displayName);
        File outFile = new File(dir, uniqueName);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setRequestMethod("GET");
        conn.connect();
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + code);
            }
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
            }
        } finally {
            conn.disconnect();
        }

        return outFile.getAbsolutePath();
    }

    /** Deletes a previously-copied attachment file, if it exists. Safe to call with null. */
    public static void deleteFile(String absolutePath) {
        if (absolutePath == null) {
            return;
        }
        File file = new File(absolutePath);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** Opens an attachment with whatever app the user has for its MIME type. */
    public static void openFile(Context context, String absolutePath, String mimeType) {
        File file = new File(absolutePath);
        if (!file.exists()) {
            Toast.makeText(context, "This file is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, mimeType != null ? mimeType : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    private static File getAttachmentsDir(Context context) {
        File dir = new File(context.getFilesDir(), ATTACHMENTS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Could not create attachments directory");
        }
        return dir;
    }

    @Nullable
    private static String lastPathSegment(String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URL(url).getPath();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String name) {
        if (name == null) {
            return "attachment";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
