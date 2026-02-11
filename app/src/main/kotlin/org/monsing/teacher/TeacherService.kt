package org.monsing.teacher

import org.monsing.member.MemberRepository
import org.monsing.member.Nickname
import org.monsing.member.TempMemberRepository
import org.monsing.member.teacher.GenderType
import org.monsing.member.teacher.Teacher
import org.monsing.teacher.dto.CareerInfo
import org.monsing.teacher.dto.TeacherSummary
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class TeacherService(
    private val memberRepository: MemberRepository,
    private val tempMemberRepository: TempMemberRepository
) {

    @Transactional
    fun createTeacher(memberId: Long, name: String, genderType: GenderType?) {
        val member = tempMemberRepository.findByIdOrElseThrow(memberId)

        memberRepository.save(
            Teacher(
                id = member.id,
                identifier = member.identifier,
                oauthProviderType = member.oauthProviderType,
                nickname = Nickname(name)
            )
        )
    }

    fun findTeacherById(id: Long): TeacherSummary {
        return memberRepository.findTeacherById(id).toSummary()
    }

    fun findAllTeachers(): List<TeacherSummary> {
        return memberRepository.findAllTeachers().map { it.toSummary() }
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
