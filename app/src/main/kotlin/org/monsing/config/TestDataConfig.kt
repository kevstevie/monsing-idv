package org.monsing.config

import org.monsing.auth.jwt.AuthTokenManager
import org.monsing.auth.jwt.AuthTokenPayload
import org.monsing.member.Member
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.Nickname
import org.monsing.member.OauthProviderType
import org.monsing.member.Student
import org.monsing.member.StudentRepository
import org.monsing.member.teacher.ExpertiseType
import org.monsing.member.teacher.GenderType
import org.monsing.member.teacher.Teacher
import org.monsing.member.teacher.TeacherRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local") // local 프로필에서만 실행
class TestDataConfig(
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val authTokenManager: AuthTokenManager
) {

    @Bean
    fun initTestData(): CommandLineRunner {
        return CommandLineRunner {
            // 기존 데이터가 있는지 확인
            val members = memberRepository.findAll()
            val teacherExists = members.any { it.memberType == MemberType.TEACHER }
            val studentExists = members.any { it.memberType == MemberType.STUDENT }

            // 테스트용 토큰
            var teacherToken = ""
            var studentToken = ""

            // 선생님 계정 생성
            if (!teacherExists) {
                memberRepository.save(Member(id = 1L, memberType = MemberType.TEACHER))
                val teacher = Teacher(
                    id = 1L,
                    identifier = "teacher@test.com",
                    oauthProviderType = OauthProviderType.GOOGLE,
                    nickname = Nickname("테스트 선생님"),
                    summary = "테스트용 선생님 계정입니다.",
                    description = "자세한 선생님 설명",
                    forStudent = "학생들을 위한 메시지",
                    verified = true,
                    profileImage = null,
                    genderType = GenderType.MALE,
                    expertiseType = ExpertiseType.VOCAL
                )

                val savedTeacher = teacherRepository.save(teacher)
                println("테스트 선생님 계정이 생성되었습니다. ID: ${savedTeacher.id}")

                // 선생님 토큰 생성
                val payload = AuthTokenPayload(
                    id = savedTeacher.id!!
                )
                teacherToken = authTokenManager.createAccessToken(payload)
                println("선생님 토큰: $teacherToken")
            } else {
                // 기존 선생님 계정의 토큰 생성
                val teacherMember = members.first { it.memberType == MemberType.TEACHER }
                val payload = AuthTokenPayload(
                    id = teacherMember.id!!
                )
                teacherToken = authTokenManager.createAccessToken(payload)
            }

            // 학생 계정 생성
            if (!studentExists) {
                memberRepository.save(Member(id = 2L, memberType = MemberType.STUDENT))
                val student = Student(
                    id = 2L,
                    identifier = "student@test.com",
                    oauthProviderType = OauthProviderType.GOOGLE,
                    nickname = Nickname("테스트 학생"),
                    profileImage = null
                )

                val savedStudent = studentRepository.save(student)
                println("테스트 학생 계정이 생성되었습니다. ID: ${savedStudent.id}")

                // 학생 토큰 생성
                val payload = AuthTokenPayload(
                    id = savedStudent.id!!
                )
                studentToken = authTokenManager.createAccessToken(payload)
                println("학생 토큰: $studentToken")
            } else {
                // 기존 학생 계정의 토큰 생성
                val studentMember = members.first { it.memberType == MemberType.STUDENT }
                val payload = AuthTokenPayload(
                    id = studentMember.id!!
                )
                studentToken = authTokenManager.createAccessToken(payload)
            }

            // 토큰 정보 출력
            println("\n===== 테스트 계정 정보 =====")
            println("선생님 토큰: $teacherToken")
            println("학생 토큰: $studentToken")
            println("===========================\n")

            println("HTTP 파일에서 다음과 같이 토큰을 설정하세요:")
            println("@teacherToken = $teacherToken")
            println("@studentToken = $studentToken")
        }
    }
}
