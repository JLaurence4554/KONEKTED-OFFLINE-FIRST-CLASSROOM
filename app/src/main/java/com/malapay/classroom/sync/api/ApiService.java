package com.malapay.classroom.sync.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Maps 1:1 to the PHP endpoints under {@code backend/api/} - see that
 * folder for the server-side implementation. Covers class, lesson, quiz,
 * question, and quiz result synchronization.
 */
public interface ApiService {

    // ---------------------------------------------------------------
    // Class endpoints
    // ---------------------------------------------------------------

    /** Teacher: upload a locally-created/edited class. Upserts by class_code on the server. */
    @POST("sync_class.php")
    Call<ApiEnvelope<SyncClassData>> syncClass(@Body SyncClassRequest request);

    /** Student: look up a class by its code, regardless of which device/network created it. */
    @GET("find_class.php")
    Call<ApiEnvelope<FindClassData>> findClass(@Query("code") String classCode);

    /** Student: record membership in a class on the backend. */
    @POST("join_class.php")
    Call<ApiEnvelope<Void>> joinClass(@Body JoinClassRequest request);

    /** Student: remove their membership from a class on the backend. */
    @POST("leave_class.php")
    Call<ApiEnvelope<Void>> leaveClass(@Body LeaveClassRequest request);

    // ---------------------------------------------------------------
    // Lesson endpoints
    // ---------------------------------------------------------------

    /** Teacher: upload a lesson to the server. Upserts by class_id + title.
     *
     * When the request carries an attachment ({@link SyncLessonRequest#attachmentFile}
     * is non-null), the call is sent as multipart/form-data with the file as a part.
     * Otherwise it is sent as a JSON body, matching the previous behaviour.
     */
    @POST("sync_lesson.php")
    Call<ApiEnvelope<SyncLessonData>> syncLesson(@Body SyncLessonRequest request);

    /** Fetch all lessons for a class from the server. */
    @GET("get_lessons.php")
    Call<ApiEnvelope<GetLessonsData>> getLessons(@Query("classId") long classId);

    // ---------------------------------------------------------------
    // Quiz endpoints
    // ---------------------------------------------------------------

    /** Teacher: upload a quiz to the server. Upserts by lesson_id + title. */
    @POST("sync_quiz.php")
    Call<ApiEnvelope<SyncQuizData>> syncQuiz(@Body SyncQuizRequest request);

    /** Fetch all quizzes (with questions) for a lesson from the server. */
    @GET("get_quizzes.php")
    Call<ApiEnvelope<GetQuizzesData>> getQuizzes(@Query("lessonId") long lessonId);

    // ---------------------------------------------------------------
    // Question endpoints
    // ---------------------------------------------------------------

    /** Teacher: upload a question to the server. Upserts by quiz_id + question text. */
    @POST("sync_question.php")
    Call<ApiEnvelope<SyncQuestionData>> syncQuestion(@Body SyncQuestionRequest request);

    // ---------------------------------------------------------------
    // Quiz Result endpoints
    // ---------------------------------------------------------------

    /** Student: upload a quiz result to the server. Upserts by quiz_id + student. */
    @POST("sync_quiz_result.php")
    Call<ApiEnvelope<SyncQuizResultData>> syncQuizResult(@Body SyncQuizResultRequest request);

    /** Fetch all quiz results for a quiz from the server. */
    @GET("get_quiz_results.php")
    Call<ApiEnvelope<GetQuizResultsData>> getQuizResults(@Query("quizId") long quizId);

    // ---------------------------------------------------------------
    // Join Request endpoints
    // ---------------------------------------------------------------

    /** Student: send a join request to the teacher for approval. */
    @POST("sync_join_request.php")
    Call<ApiEnvelope<SyncJoinRequestResult>> syncJoinRequest(@Body SyncJoinRequest request);

    /** Teacher: fetch all pending join requests for their classes. */
    @GET("get_join_requests.php")
    Call<ApiEnvelope<GetJoinRequestsData>> getJoinRequests(@Query("teacherEmail") String teacherEmail);

    // ---------------------------------------------------------------
    // Student membership endpoints
    // ---------------------------------------------------------------

    /** Student: fetch all classes they are a member of. */
    @GET("get_student_classes.php")
    Call<ApiEnvelope<GetStudentClassesData>> getStudentClasses(@Query("studentEmail") String studentEmail);

    /** Teacher: approve a join request, adding the student as a class member. */
    @POST("approve_join_request.php")
    Call<ApiEnvelope<Void>> approveJoinRequest(@Body ApproveJoinRequest request);
}
