package com.malapay.classroom.sync.api;

/** {@code data} payload of a successful find_class.php response. */
public class FindClassData {
    public long serverId;
    public String className;
    public String subject;
    public String section;
    public String schoolYear;
    public String classCode;
    public String teacherName;
    public String teacherEmail;
}
