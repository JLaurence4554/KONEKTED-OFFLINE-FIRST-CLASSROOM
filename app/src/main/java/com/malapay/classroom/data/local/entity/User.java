package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "users" table.
 * Both Teacher and Student roles are stored in this single table,
 * differentiated by the {@link #role} field.
 */
@Entity(
        tableName = "users",
        indices = {@Index(value = "email", unique = true)}
)
public class User {

    /** Two valid roles for this app. Kept as constants to avoid typos elsewhere. */
    public static final String ROLE_TEACHER = "Teacher";
    public static final String ROLE_STUDENT = "Student";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "fullname")
    private String fullName;

    @NonNull
    @ColumnInfo(name = "email")
    private String email;

    /** Stores the PBKDF2 hash + salt, never the raw password. */
    @NonNull
    @ColumnInfo(name = "password_hash")
    private String passwordHash;

    @NonNull
    @ColumnInfo(name = "role")
    private String role;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public User(@NonNull String fullName, @NonNull String email,
                @NonNull String passwordHash, @NonNull String role, long createdAt) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getFullName() {
        return fullName;
    }

    public void setFullName(@NonNull String fullName) {
        this.fullName = fullName;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    public void setEmail(@NonNull String email) {
        this.email = email;
    }

    @NonNull
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@NonNull String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    public void setRole(@NonNull String role) {
        this.role = role;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isTeacher() {
        return ROLE_TEACHER.equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        return ROLE_STUDENT.equalsIgnoreCase(role);
    }
}
