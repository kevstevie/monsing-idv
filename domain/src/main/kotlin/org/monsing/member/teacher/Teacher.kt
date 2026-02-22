package org.monsing.member.teacher

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import java.time.LocalDateTime
import org.hibernate.annotations.BatchSize
import org.monsing.member.Nickname
import org.monsing.member.OauthProviderType
import org.monsing.member.StrongSideType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate

@Entity
class Teacher(

    @Id
    var id: Long? = null,

    val identifier: String,

    @Enumerated(EnumType.STRING)
    val oauthProviderType: OauthProviderType,

    @Embedded
    var nickname: Nickname = Nickname(),

    val summary: String? = null,

    @Enumerated(EnumType.STRING)
    val strongSideType: StrongSideType? = null,

    val description: String? = null,

    val forStudent: String? = null,

    val verified: Boolean = false,

    @Column(name = "profile_image")
    val profileImage: String? = null,

    @Enumerated(EnumType.STRING)
    var genderType: GenderType = GenderType.OTHER,

    @Enumerated(EnumType.STRING)
    var expertiseType: ExpertiseType = ExpertiseType.NONE,

    @BatchSize(size = 5)
    @OneToMany
    @JoinColumn(name = "teacher_id")
    val portfolios: MutableList<Portfolio> = mutableListOf(),

    @BatchSize(size = 5)
    @OneToMany
    @JoinColumn(name = "teacher_id")
    val careers: MutableList<Career> = mutableListOf(),

    @CreatedDate
    val createdDate: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    val updatedDate: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Teacher) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}
