package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "questions" table. Supports three question types:
 * <ul>
 *   <li>{@code MULTIPLE_CHOICE} – four choices (A-D), correctAnswer stores "A"-"D"</li>
 *   <li>{@code IDENTIFICATION} – student types a short answer, answerText stores the correct keyword(s)</li>
 *   <li>{@code ESSAY} – student types a long response, answerText stores a model/reference answer</li>
 * </ul>
 * {@code orderIndex} controls display/reorder position within the quiz (lower shows first).
 */
@Entity(
        tableName = "questions",
        indices = {@Index("quiz_id")},
        foreignKeys = @ForeignKey(
                entity = Quiz.class,
                parentColumns = "id",
                childColumns = "quiz_id",
                onDelete = ForeignKey.CASCADE
        )
)
public class Question {

    /** Question type constants. */
    public static final String TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TYPE_IDENTIFICATION  = "IDENTIFICATION";
    public static final String TYPE_ESSAY           = "ESSAY";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "quiz_id")
    private long quizId;

    @NonNull
    @ColumnInfo(name = "question")
    private String questionText;

    /** Question type: MULTIPLE_CHOICE, IDENTIFICATION, or ESSAY. */
    @NonNull
    @ColumnInfo(name = "question_type", defaultValue = "MULTIPLE_CHOICE")
    private String questionType = TYPE_MULTIPLE_CHOICE;

    @NonNull
    @ColumnInfo(name = "choice_a", defaultValue = "")
    private String choiceA = "";

    @NonNull
    @ColumnInfo(name = "choice_b", defaultValue = "")
    private String choiceB = "";

    @NonNull
    @ColumnInfo(name = "choice_c", defaultValue = "")
    private String choiceC = "";

    @NonNull
    @ColumnInfo(name = "choice_d", defaultValue = "")
    private String choiceD = "";

    /**
     * For MULTIPLE_CHOICE: "A", "B", "C", or "D".
     * For IDENTIFICATION: the correct keyword/answer (case-insensitive match).
     * For ESSAY: empty string (graded manually by teacher).
     */
    @NonNull
    @ColumnInfo(name = "correct_answer", defaultValue = "")
    private String correctAnswer = "";

    /**
     * For IDENTIFICATION: the correct answer text.
     * For ESSAY: a model/reference answer for teacher's reference.
     * For MULTIPLE_CHOICE: unused (empty).
     */
    @Nullable
    @ColumnInfo(name = "answer_text")
    private String answerText;

    @ColumnInfo(name = "order_index")
    private int orderIndex;

    /** Server-side ID for syncing with the backend. 0 = not yet synced. */
    @ColumnInfo(name = "server_id", defaultValue = "0")
    private long serverId;

    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    /** Full constructor for MULTIPLE_CHOICE (backward compatible). */
    @Ignore
    public Question(long quizId, @NonNull String questionText, @NonNull String choiceA,
                     @NonNull String choiceB, @NonNull String choiceC, @NonNull String choiceD,
                     @NonNull String correctAnswer, int orderIndex) {
        this.quizId = quizId;
        this.questionText = questionText;
        this.questionType = TYPE_MULTIPLE_CHOICE;
        this.choiceA = choiceA;
        this.choiceB = choiceB;
        this.choiceC = choiceC;
        this.choiceD = choiceD;
        this.correctAnswer = correctAnswer;
        this.orderIndex = orderIndex;
    }

    /** Constructor for IDENTIFICATION questions. */
    @Ignore
    public Question(long quizId, @NonNull String questionText, @NonNull String answerText, int orderIndex) {
        this.quizId = quizId;
        this.questionText = questionText;
        this.questionType = TYPE_IDENTIFICATION;
        this.correctAnswer = "";
        this.answerText = answerText;
        this.orderIndex = orderIndex;
    }

    /** Constructor for ESSAY questions. */
    public static Question createEssay(long quizId, @NonNull String questionText,
                                        @Nullable String modelAnswer, int orderIndex) {
        Question q = new Question();
        q.quizId = quizId;
        q.questionText = questionText;
        q.questionType = TYPE_ESSAY;
        q.correctAnswer = "";
        q.answerText = modelAnswer;
        q.orderIndex = orderIndex;
        return q;
    }

    public Question() { /* used by Room and createEssay */ }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getQuizId() { return quizId; }
    public void setQuizId(long quizId) { this.quizId = quizId; }

    @NonNull
    public String getQuestionText() { return questionText; }
    public void setQuestionText(@NonNull String questionText) { this.questionText = questionText; }

    @NonNull
    public String getQuestionType() { return questionType; }
    public void setQuestionType(@NonNull String questionType) { this.questionType = questionType; }

    public boolean isMultipleChoice() { return TYPE_MULTIPLE_CHOICE.equals(questionType); }
    public boolean isIdentification() { return TYPE_IDENTIFICATION.equals(questionType); }
    public boolean isEssay()          { return TYPE_ESSAY.equals(questionType); }

    @NonNull
    public String getChoiceA() { return choiceA; }
    public void setChoiceA(@NonNull String choiceA) { this.choiceA = choiceA; }

    @NonNull
    public String getChoiceB() { return choiceB; }
    public void setChoiceB(@NonNull String choiceB) { this.choiceB = choiceB; }

    @NonNull
    public String getChoiceC() { return choiceC; }
    public void setChoiceC(@NonNull String choiceC) { this.choiceC = choiceC; }

    @NonNull
    public String getChoiceD() { return choiceD; }
    public void setChoiceD(@NonNull String choiceD) { this.choiceD = choiceD; }

    @NonNull
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(@NonNull String correctAnswer) { this.correctAnswer = correctAnswer; }

    @Nullable
    public String getAnswerText() { return answerText; }
    public void setAnswerText(@Nullable String answerText) { this.answerText = answerText; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public long getServerId() { return serverId; }
    public void setServerId(long serverId) { this.serverId = serverId; }

    @NonNull
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(@NonNull String syncStatus) { this.syncStatus = syncStatus; }

    @NonNull
    public Long getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(@NonNull Long lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    /** Returns the text of whichever choice (A-D) is currently marked correct. Only for MCQ. */
    @NonNull
    public String getCorrectChoiceText() {
        switch (correctAnswer) {
            case "A": return choiceA;
            case "B": return choiceB;
            case "C": return choiceC;
            case "D": return choiceD;
            default: return "";
        }
    }

    /** Human-readable type label for display. */
    @NonNull
    public String getTypeLabel() {
        switch (questionType) {
            case TYPE_IDENTIFICATION: return "Identification";
            case TYPE_ESSAY:          return "Essay";
            default:                  return "Multiple Choice";
        }
    }
}
