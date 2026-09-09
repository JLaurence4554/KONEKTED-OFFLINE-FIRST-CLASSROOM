package com.malapay.classroom.sync.api;

import java.util.List;

/** data payload of a successful get_quiz_results.php response. */
public class GetQuizResultsData {
    public List<ServerQuizResult> results;

    public static class ServerQuizResult {
        public int id;
        public int quiz_id;
        public int student_id;
        public int score;
        public int correct_count;
        public int total_questions;
        public String submitted_at;
        public String student_name;
    }
}
