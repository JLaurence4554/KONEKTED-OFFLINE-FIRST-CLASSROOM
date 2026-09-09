package com.malapay.classroom.sync.api;

import java.util.List;

/** data payload of a successful get_lessons.php response. */
public class GetLessonsData {
    public List<ServerLesson> lessons;

    public static class ServerLesson {
        public int id;
        public int class_id;
        public String title;
        public String body;
        public String subject;
        public String video_link;
        public String created_at;
        public String updated_at;

        /** Original file name of the lesson's attachment, or null. */
        @androidx.annotation.Nullable
        public String attachment_name;

        /** MIME type of the lesson's attachment, or null. */
        @androidx.annotation.Nullable
        public String attachment_mime;

        /** Full URL to download_attachment.php for this lesson's file, or null. */
        @androidx.annotation.Nullable
        public String attachment_url;
    }
}
