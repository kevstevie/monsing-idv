package org.monsing.auth.dto

sealed class MemberInfo(val id: Long, val role: String)

class TeacherMemberInfo(
    id: Long,
    val summary: String?,
    val strongSideType: String?,
    val description: String?,
    val forStudent: String?,
    val verified: Boolean,
    val profileImage: String?,
    val genderType: String,
    val expertiseType: String
) : MemberInfo(id, "TEACHER")

class StudentMemberInfo(
    id: Long,
    val name: String
) : MemberInfo(id, "STUDENT")
