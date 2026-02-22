package org.monsing.member

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.LocalDateTime
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate

@Entity
class Student(

    @Id
    var id: Long? = null,

    val identifier: String,

    @Enumerated(EnumType.STRING)
    val oauthProviderType: OauthProviderType,

    @Embedded
    var nickname: Nickname = Nickname(),

    @Column(name = "profile_image")
    val profileImage: String? = null,

    @CreatedDate
    val createdDate: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    val updatedDate: LocalDateTime = LocalDateTime.now()
)
