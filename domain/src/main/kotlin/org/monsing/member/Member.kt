package org.monsing.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id

@Entity
class Member(

    @Id
    var id: Long? = null,

    @Column(name = "member_type")
    @Enumerated(EnumType.STRING)
    val memberType: MemberType
)
