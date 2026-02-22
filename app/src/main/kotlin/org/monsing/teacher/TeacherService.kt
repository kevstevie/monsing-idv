package org.monsing.teacher

import org.monsing.member.Member
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.Nickname
import org.monsing.member.TempMemberRepository
import org.monsing.member.teacher.GenderType
import org.monsing.member.teacher.Teacher
import org.monsing.member.teacher.TeacherReadRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.teacher.dto.CareerInfo
import org.monsing.teacher.dto.TeacherSummary
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class TeacherService(
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val tempMemberRepository: TempMemberRepository,
    private val teacherReadRepository: TeacherReadRepository
) {

    @Transactional
    fun createTeacher(memberId: Long, name: String, genderType: GenderType?) {
        val tempMember = tempMemberRepository.findByIdOrElseThrow(memberId)

        memberRepository.save(Member(id = tempMember.id, memberType = MemberType.TEACHER))
        teacherRepository.save(
            Teacher(
                id = tempMember.id,
                identifier = tempMember.identifier,
                oauthProviderType = tempMember.oauthProviderType,
                nickname = Nickname(name)
            )
        )
    }

    fun findTeacherById(id: Long): TeacherSummary {
        return teacherRepository.findByIdOrElseThrow(id).toSummary()
    }

    fun findAllTeachers(size: Int = 20, lastId: Long = 0L): List<TeacherSummary> {
        val teachers = teacherReadRepository.findAllTeachersFlat(lastId, size)
        if (teachers.isEmpty()) return emptyList()

        val teacherIds = teachers.map { it.getId() }

        val careersMap = teacherReadRepository.findCareersByTeacherIds(teacherIds)
            .groupBy { it.getTeacherId() }
        val portfoliosMap = teacherReadRepository.findPortfoliosByTeacherIds(teacherIds)
            .groupBy { it.getTeacherId() }

        return teachers.map { t ->
            val id = t.getId()
            TeacherSummary(
                id = id,
                name = t.getNickname(),
                verified = t.getVerified(),
                careers = careersMap[id]?.map { CareerInfo(detail = it.getDetail(), period = it.getPeriod()) }
                    ?: emptyList(),
                portfolioUrls = portfoliosMap[id]?.map { it.getUrl() } ?: emptyList(),
                profileImage = t.getProfileImage(),
                summary = t.getSummary(),
                description = t.getDescription()
            )
        }
    }

    private fun Teacher.toSummary(): TeacherSummary = TeacherSummary(
        id = requireNotNull(id),
        name = nickname.value,
        verified = verified,
        careers = careers.map { CareerInfo(detail = it.detail, period = it.period) },
        portfolioUrls = portfolios.map { it.url },
        profileImage = profileImage,
        summary = summary,
        description = description
    )
}
