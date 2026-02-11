package org.monsing.record

import io.swagger.v3.oas.annotations.Operation
import org.monsing.auth.Auth
import org.monsing.auth.AuthPayload
import org.monsing.auth.jwt.AuthTokenPayload
import org.monsing.record.feedback.FeedbackService
import org.monsing.record.feedback.dto.FeedbackItemInfo
import org.monsing.record.response.FeedbackResponse
import org.monsing.record.response.StudentInfoResponse
import org.monsing.teacher.dto.TeacherBrief
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/feedbacks")
class FeedbackController(
    private val feedbackService: FeedbackService
) {
    @Auth
    @PostMapping("/items")
    @Operation(summary = "피드백 상품 생성")
    fun createFeedbackTicket(
        @AuthPayload authTokenPayload: AuthTokenPayload,
        @RequestBody request: FeedbackTicketCreateRequest
    ) {
        feedbackService.createFeedbackItem(authTokenPayload.id, request.price, request.description, request.amount)
    }

    @Operation(summary = "피드백 상품 조회")
    @GetMapping("/items/{itemId}")
    fun getFeedbackItem(
        @PathVariable itemId: Long
    ): FeedbackItemResponse {
        val feedbackItemInfo = feedbackService.getFeedbackItem(itemId)
        return feedbackItemInfo.toResponse()
    }

    @Operation(summary = "피드백 다건 조회")
    @GetMapping("/items")
    fun getFeedbackItems(
        @RequestParam(required = false) teacherId: Long?
    ): List<FeedbackItemResponse> {
        return feedbackService.getFeedbackItemsByTeacherId(teacherId).map { it.toResponse() }
    }

    @Auth
    @Operation(summary = "피드백 티켓 구매")
    @PostMapping("/tickets")
    fun purchaseFeedbackTicket(
        @AuthPayload authTokenPayload: AuthTokenPayload,
        @RequestBody request: FeedbackTicketPurchaseRequest
    ) {
        feedbackService.purchaseFeedbackTicket(authTokenPayload.id, request.amount, request.itemId)
    }

    @Auth
    @Operation(summary = "내 피드백 상품 조회")
    @GetMapping("/items/my")
    fun getMyFeedbackItems(
        @AuthPayload authTokenPayload: AuthTokenPayload
    ): List<MyFeedbackItemResponse> {
        val feedbackItems = feedbackService.getFeedbackItemsByMemberId(authTokenPayload.id)

        val itemIds = feedbackItems.map { it.id }
        val remainingTicketsMap = feedbackService.getRemainingTicketsMapByMemberId(authTokenPayload.id, itemIds)

        return feedbackItems.map { item ->
            MyFeedbackItemResponse(
                id = item.id,
                teacher = item.teacher.toResponse(),
                description = item.description,
                price = item.price,
                amount = item.amount,
                remainingTickets = remainingTicketsMap[item.id]
            )
        }
    }

    @Auth
    @Operation(summary = "피드백 요청")
    @PostMapping("/{feedbackTicketId}")
    fun requestFeedback(
        @AuthPayload authTokenPayload: AuthTokenPayload,
        @PathVariable feedbackTicketId: Long,
        @RequestBody request: RequestFeedbackRequest
    ) {
        feedbackService.requestFeedback(authTokenPayload.id, request.recordId, feedbackTicketId)
    }

    @Auth
    @Operation(summary = "내 피드백 조회")
    @GetMapping("/my")
    fun listFeedbacks(
        @AuthPayload authTokenPayload: AuthTokenPayload
    ): List<FeedbackResponse> {
        val feedbacks = feedbackService.findFeedbacksByMemberId(authTokenPayload.id)

        return feedbacks.map {
            FeedbackResponse(
                id = it.id,
                recordId = it.recordId,
                teacherId = it.teacherId,
                teacherName = it.teacherName,
                teacherProfileImage = it.teacherProfileImage,
                student = StudentInfoResponse(
                    id = it.studentId,
                    name = it.studentName,
                    profileImageUrl = it.studentProfileImage
                ),
                detail = it.detail,
                createdAt = it.createdAt
            )
        }
    }

    private fun FeedbackItemInfo.toResponse(): FeedbackItemResponse = FeedbackItemResponse(
        id = id,
        teacher = teacher.toResponse(),
        description = description,
        price = price,
        amount = amount
    )

    private fun TeacherBrief.toResponse(): TeacherResponse = TeacherResponse(
        id = id,
        name = name,
        profileImageUrl = profileImageUrl,
        verified = verified,
        description = description,
        genderType = genderType,
        expertiseType = expertiseType
    )
}

data class FeedbackItemResponse(
    val id: Long,
    val teacher: TeacherResponse,
    val description: String,
    val price: Int,
    val amount: Int
)

data class MyFeedbackItemResponse(
    val id: Long,
    val teacher: TeacherResponse,
    val description: String,
    val price: Int,
    val amount: Int,
    val remainingTickets: Int?
)

data class TeacherResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val verified: Boolean,
    val description: String?,
    val genderType: String,
    val expertiseType: String
)

data class RequestFeedbackRequest(
    val recordId: Long
)

data class FeedbackTicketPurchaseRequest(
    val amount: Int,
    val itemId: Long
)

data class FeedbackTicketCreateRequest(
    val amount: Int,
    val price: Int,
    val description: String
)
