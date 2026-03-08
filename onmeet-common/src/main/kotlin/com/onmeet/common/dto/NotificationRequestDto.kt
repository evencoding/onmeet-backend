package com.onmeet.common.dto

/**
 * 타 서비스(Video, AI, Auth 등)에서 알림 서비스로
 * Kafka 이벤트를 발행할 때 공통으로 사용하는 DTO
 */
data class NotificationRequestDto(
    /**
     * 알림을 받을 사용자 ID
     */
    val userId: Long? = null,

    /**
     * 알림 타입 (NotificationType Enum의 name() 값)
     * 예: MEETING_REMINDER, AI_SUMMARY_COMPLETED, TEAM_MEMBER_ADDED 등
     */
    val type: String? = null,

    /**
     * 알림 제목 (회의 이름, 팀 이름 등 템플릿의 {title} 인자로 사용됨)
     */
    val title: String? = null,

    /**
     * 커스텀 알림 본문 (템플릿에 지정된 문구 외에 직접 본문을 전달하고 싶을 때 사용)
     */
    val body: String? = null,

    /**
     * 알림 클릭 시 이동할 URL 경로 (예: /meetings/123)
     */
    val deeplink: String? = null,

    /**
     * 알림 관련 리소스 타입
     * 예: MEETING, MINUTES, TEAM 등
     */
    val resourceType: String? = null,

    /**
     * 알림 관련 리소스 ID (회의 ID 등)
     */
    val resourceId: String? = null,

    /**
     * 알림을 발생시킨 행위자의 사용자 ID (방장 ID 등, 시스템 알림 시 null. 템플릿의 {senderName}으로 사용됨)
     */
    val actorUserId: Long? = null
)
