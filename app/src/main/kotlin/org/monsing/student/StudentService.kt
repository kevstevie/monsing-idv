package org.monsing.student

import org.monsing.member.Member
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.Nickname
import org.monsing.member.Student
import org.monsing.member.StudentRepository
import org.monsing.member.TempMemberRepository
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StudentService(
    private val memberRepository: MemberRepository,
    private val studentRepository: StudentRepository,
    private val tempMemberRepository: TempMemberRepository
) {

    @Transactional
    fun create(memberId: Long, name: String) {
        val tempMember = tempMemberRepository.findByIdOrElseThrow(memberId)

        memberRepository.save(Member(id = tempMember.id, memberType = MemberType.STUDENT))
        studentRepository.save(
            Student(
                id = tempMember.id,
                identifier = tempMember.identifier,
                oauthProviderType = tempMember.oauthProviderType,
                nickname = Nickname(name)
            )
        )
    }
}
