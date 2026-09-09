package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.malapay.classroom.data.local.entity.User;

@Dao
public interface UserDao {

    /**
     * Inserts a user. Aborts on unique-constraint (duplicate email) conflict
     * so callers can detect the failure instead of silently overwriting data.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(User user);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    boolean emailExists(String email);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(long id);

    @Query("SELECT COUNT(*) FROM users")
    int countUsers();
}
