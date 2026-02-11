package org.monsing.teacher

import openapi.api.TeacherApi
import openapi.model.CareerResponse
import openapi.model.TeacherCreateRequest
import openapi.model.TeacherOverviewResponse
import org.monsing.auth.jwt.AuthTokenPayload
import org.monsing.member.teacher.GenderType
import org.monsing.teacher.dto.TeacherSummary
import org.monsing.util.enumValueOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TeacherController(
    private val teacherService: TeacherService
) : TeacherApi {

    override fun createTeacher(
        authTokenPayload: AuthTokenPayload,
        teacherCreateRequest: TeacherCreateRequest
    ): ResponseEntity<Unit> {
        teacherService.createTeacher(
            authTokenPayload.id,
            teacherCreateRequest.name,
            teacherCreateRequest.gender()
        )

        return ResponseEntity.ok().build()
    }

    private fun TeacherCreateRequest.gender(): GenderType? {
        return enumValueOrNull<GenderType>(
            gender.name.uppercase()
        )
    }

    override fun getTeacherOverview(id: Long): ResponseEntity<TeacherOverviewResponse> {
        val teacher = teacherService.findTeacherById(id)
        return ResponseEntity.ok(teacher.toResponse())
    }

    override fun readTeachers(): ResponseEntity<List<TeacherOverviewResponse>> {
        val teachers = teacherService.findAllTeachers()
        return ResponseEntity.ok(teachers.map { it.toResponse() })
    }

    private fun TeacherSummary.toResponse(): TeacherOverviewResponse = TeacherOverviewResponse(
        id = id,
        name = name,
        verified = verified,
        careers = careers.map { CareerResponse(it.detail, it.period) },
        portfolios = portfolioUrls,
        profileImage = profileImage,
        summary = summary,
        description = description
    )
}
