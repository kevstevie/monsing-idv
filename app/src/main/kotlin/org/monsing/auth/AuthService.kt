package org.monsing.auth

import org.monsing.auth.dto.MemberInfo
import org.monsing.auth.dto.StudentMemberInfo
import org.monsing.auth.dto.TeacherMemberInfo
import org.monsing.auth.jwt.AuthTokenManager
import org.monsing.auth.jwt.AuthTokenPayload
import org.monsing.auth.oauthhandler.OauthAdaptor
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.OauthProviderType
import org.monsing.member.StudentRepository
import org.monsing.member.TempMember
import org.monsing.member.TempMemberRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.token.AuthToken
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val tempMemberRepository: TempMemberRepository,
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val oauthAdaptor: OauthAdaptor,
    private val authTokenManager: AuthTokenManager
) {

    @Transactional
    fun login(oauthProviderType: OauthProviderType, oauthToken: String): AuthToken {
        val oauthIdentifier = oauthAdaptor.handle(oauthProviderType, oauthToken)
        val member = tempMemberRepository.findByIdentifierAndOauthProviderType(oauthIdentifier.id, oauthProviderType)
            ?: tempMemberRepository.save(
                TempMember(
                    identifier = oauthIdentifier.id,
                    oauthProviderType = oauthProviderType
                )
            )

        val id = requireNotNull(member.id) {
            "Member id must not be null"
        }

        return AuthToken(
            accessToken = authTokenManager.createAccessToken(AuthTokenPayload(id)),
            refreshToken = authTokenManager.createRefreshToken(id)
        )
    }

    @Transactional(readOnly = true)
    fun refresh(refreshToken: String): AuthToken {
        val payload = authTokenManager.getRefreshPayload(refreshToken)

        return AuthToken(
            accessToken = authTokenManager.createAccessToken(AuthTokenPayload(payload)),
            refreshToken = refreshToken
        )
    }

    @Transactional(readOnly = true)
    fun getMember(id: Long): MemberInfo? {
        val member = memberRepository.findByIdOrNull(id) ?: return null

        return when (member.memberType) {
            MemberType.TEACHER -> teacherRepository.findByIdOrNull(id)?.let { teacher ->
                TeacherMemberInfo(
                    id = requireNotNull(teacher.id),
                    summary = teacher.summary,
                    strongSideType = teacher.strongSideType?.name,
                    description = teacher.description,
                    forStudent = teacher.forStudent,
                    verified = teacher.verified,
                    profileImage = teacher.profileImage,
                    genderType = teacher.genderType.name,
                    expertiseType = teacher.expertiseType.name
                )
            }

            MemberType.STUDENT -> studentRepository.findByIdOrNull(id)?.let { student ->
                StudentMemberInfo(
                    id = requireNotNull(student.id),
                    name = student.nickname.value
                )
            }
        }
    }
}
