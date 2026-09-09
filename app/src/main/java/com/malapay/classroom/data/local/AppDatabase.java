package com.malapay.classroom.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.malapay.classroom.data.local.dao.AnalyticsDao;
import com.malapay.classroom.data.local.dao.AttendanceDao;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.ClassMemberDao;
import com.malapay.classroom.data.local.dao.JoinRequestDao;
import com.malapay.classroom.data.local.dao.LessonDao;
import com.malapay.classroom.data.local.dao.QuestionDao;
import com.malapay.classroom.data.local.dao.QuizDao;
import com.malapay.classroom.data.local.dao.QuizResultDao;
import com.malapay.classroom.data.local.dao.UserDao;
import com.malapay.classroom.data.local.entity.Attendance;
import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.JoinRequest;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.util.PasswordUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single Room database for the whole app.
 * v1-v5: original schema evolution.
 * v6: adds online-sync columns on classes/class_members.
 * v7: adds server_id on lessons.
 * v8: adds sync columns on quizzes/questions/quiz_results.
 * v9: adds attendance table with sync columns.
 * v10: adds question_type + answer_text columns to questions.
 * v11: adds join_requests table for teacher approval of student joins.
 */
@Database(entities = {User.class, SchoolClass.class, ClassMember.class, Lesson.class,
        Quiz.class, Question.class, QuizResult.class, Attendance.class,
        JoinRequest.class}, version = 11, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "classroom_offline.db";
    private static volatile AppDatabase instance;

    /** Shared background executor for DB writes/seed work off the main thread. */
    public static final ExecutorService DATABASE_WRITE_EXECUTOR = Executors.newFixedThreadPool(4);

    public abstract UserDao userDao();
    public abstract ClassDao classDao();
    public abstract ClassMemberDao classMemberDao();
    public abstract LessonDao lessonDao();
    public abstract QuizDao quizDao();
    public abstract QuestionDao questionDao();
    public abstract QuizResultDao quizResultDao();
    public abstract AttendanceDao attendanceDao();
    public abstract JoinRequestDao joinRequestDao();
    public abstract AnalyticsDao analyticsDao();

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE classes ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE classes ADD COLUMN server_id INTEGER");
            db.execSQL("ALTER TABLE classes ADD COLUMN last_synced_at INTEGER");
            db.execSQL("ALTER TABLE class_members ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE class_members ADD COLUMN last_synced_at INTEGER");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN server_id INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE quizzes ADD COLUMN server_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE quizzes ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE quizzes ADD COLUMN last_synced_at INTEGER");
            db.execSQL("ALTER TABLE quizzes ADD COLUMN server_lesson_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE questions ADD COLUMN server_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE questions ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE questions ADD COLUMN last_synced_at INTEGER");
            db.execSQL("ALTER TABLE quiz_results ADD COLUMN server_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE quiz_results ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            db.execSQL("ALTER TABLE quiz_results ADD COLUMN last_synced_at INTEGER");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS attendance (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "class_id INTEGER NOT NULL, " +
                    "student_id INTEGER NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'Present', " +
                    "date INTEGER NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "server_id INTEGER NOT NULL DEFAULT 0, " +
                    "sync_status TEXT NOT NULL DEFAULT 'PENDING', " +
                    "last_synced_at INTEGER, " +
                    "FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_class_id_student_id_date ON attendance (class_id, student_id, date)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_class_id ON attendance (class_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_student_id ON attendance (student_id)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE quiz_results ADD COLUMN essay_answers TEXT");
            db.execSQL("ALTER TABLE quiz_results ADD COLUMN graded_score INTEGER");
            db.execSQL("CREATE TABLE IF NOT EXISTS join_requests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "class_id INTEGER NOT NULL, " +
                    "student_id INTEGER NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'PENDING', " +
                    "requested_at INTEGER NOT NULL, " +
                    "resolved_at INTEGER, " +
                    "sync_status TEXT NOT NULL DEFAULT 'PENDING', " +
                    "server_id INTEGER, " +
                    "last_synced_at INTEGER, " +
                    "FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_join_requests_class_id_student_id " +
                    "ON join_requests (class_id, student_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_join_requests_class_id ON join_requests (class_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_join_requests_student_id ON join_requests (student_id)");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE questions ADD COLUMN question_type TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE'");
            db.execSQL("ALTER TABLE questions ADD COLUMN answer_text TEXT");
        }
    };

    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .addCallback(seedDefaultTeacherCallback)
                            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    private static final RoomDatabase.Callback seedDefaultTeacherCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            DATABASE_WRITE_EXECUTOR.execute(() -> {
                UserDao dao = instance.userDao();
                if (dao.countUsers() == 0) {
                    String hash = PasswordUtil.hashPassword("Password123");
                    User defaultTeacher = new User(
                            "Default Teacher", "teacher@malapay.edu",
                            hash, User.ROLE_TEACHER, System.currentTimeMillis());
                    dao.insert(defaultTeacher);
                    User defaultStudent = new User(
                            "Default Student", "student@malapay.edu",
                            hash, User.ROLE_STUDENT, System.currentTimeMillis());
                    dao.insert(defaultStudent);
                }
            });
        }
    };
}
