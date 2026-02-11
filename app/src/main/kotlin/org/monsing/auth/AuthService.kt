package org.monsing.auth

import org.monsing.auth.dto.MemberInfo
import org.monsing.auth.dto.StudentMemberInfo
import org.monsing.auth.dto.TeacherMemberInfo
import org.monsing.auth.jwt.AuthTokenManager
import org.monsing.auth.jwt.AuthTokenPayload
import org.monsing.auth.oauthhandler.OauthAdaptor
import org.monsing.member.MemberRepository
import org.monsing.member.OauthProviderType
import org.monsing.member.Student
import org.monsing.member.TempMember
import org.monsing.member.TempMemberRepository
import org.monsing.member.teacher.Teacher
import org.monsing.token.AuthToken
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val tempMemberRepository: TempMemberRepository,
    private val memberRepository: MemberRepository,
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

        return when (member) {
            is Teacher -> TeacherMemberInfo(
                id = requireNotNull(member.id),
                summary = member.summary,
                strongSideType = member.strongSideType?.name,
                description = member.description,
                forStudent = member.forStudent,
                verified = member.verified,
                profileImage = member.profileImage,
                genderType = member.genderType.name,
                expertiseType = member.expertiseType.name
            )
            is Student -> StudentMemberInfo(
                id = requireNotNull(member.id),
                name = member.nickname.value
            )
            else -> null
        }
    }
}
