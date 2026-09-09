package com.malapay.classroom.sync.api;

import java.util.List;

/** data payload of a successful get_quizzes.php response. */
public class GetQuizzesData {
    public List<ServerQuiz> quizzes;

    public static class ServerQuiz {
        public int id;
        public int lesson_id;
        public String title;
        public String instructions;
        public int time_limit_minutes;
        public int passing_score;
        public String status;
        public String created_at;
        public List<ServerQuestion> questions;
    }

    public static class ServerQuestion {
        public int id;
        public int quiz_id;
        public String question;
        public String question_type;
        public String choice_a;
        public String choice_b;
        public String choice_c;
        public String choice_d;
        public String correct_answer;
        public String answer_text;
        public int order_index;
    }
}
