package org.monsing.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "member_chat",
    indexes = [
        Index(name = "idx_member_chat_member_id", columnList = "member_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_chat_chat_member",
            columnNames = ["chat_id", "member_id"]
        )
    ]
)
class MemberChat(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long
)
