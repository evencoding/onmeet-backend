package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * notification-service 에러 코드 정의.
 */
enum class NotificationErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === SSE ===
    SSE_USER_ID_MISSING("NOTI_001", "SSE 구독에 필요한 사용자 ID가 없습니다", HttpStatus.BAD_REQUEST),
    SSE_INIT_EVENT_FAILED("NOTI_002", "SSE 연결 초기 이벤트 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    SSE_STREAM_SAVE_FAILED("NOTI_003", "SSE 스트림 정보 저장에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 알림 전송 ===
    NO_RECIPIENT("NOTI_004", "알림 수신자가 지정되지 않았습니다", HttpStatus.BAD_REQUEST),
    NOTIFICATION_TYPE_MISSING("NOTI_005", "알림 타입이 누락되었습니다", HttpStatus.BAD_REQUEST),
    INVALID_NOTIFICATION_TYPE("NOTI_006", "유효하지 않은 알림 타입입니다", HttpStatus.BAD_REQUEST),
    INVALID_RESOURCE_TYPE("NOTI_007", "유효하지 않은 리소스 타입입니다", HttpStatus.BAD_REQUEST),
    NOTIFICATION_SAVE_FAILED("NOTI_008", "알림 저장에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    SSE_SEND_FAILED("NOTI_009", "SSE 알림 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 알림 조회/관리 ===
    NOTIFICATION_NOT_FOUND("NOTI_010", "해당 알림을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    NOTIFICATION_SETTING_SAVE_FAILED("NOTI_012", "알림 설정 저장에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === FCM ===
    FCM_TOKEN_MISSING("NOTI_013", "FCM 토큰 또는 디바이스 ID가 누락되었습니다", HttpStatus.BAD_REQUEST),
    FCM_TOKEN_SAVE_FAILED("NOTI_014", "FCM 토큰 등록에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    FCM_NOT_INITIALIZED("NOTI_015", "Firebase가 초기화되지 않아 FCM 푸시를 전송할 수 없습니다", HttpStatus.SERVICE_UNAVAILABLE),
    FCM_SEND_FAILED("NOTI_016", "FCM 푸시 알림 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    FCM_TOKEN_INVALID("NOTI_017", "유효하지 않은 FCM 토큰입니다", HttpStatus.BAD_REQUEST),
    FCM_RETRY_EXHAUSTED("NOTI_018", "FCM 푸시 알림 최종 전송에 실패했습니다 (재시도 소진)", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 외부 서비스 ===
    AUTH_SERVICE_CALL_FAILED("NOTI_019", "사용자 정보 조회에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Kafka ===
    KAFKA_DESERIALIZE_FAILED("NOTI_020", "Kafka 알림 이벤트 역직렬화에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    KAFKA_PROCESS_FAILED("NOTI_021", "Kafka 알림 이벤트 처리에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 스케줄러 ===
    SCHEDULED_QUERY_FAILED("NOTI_022", "예약 알림 조회에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULED_PROCESS_FAILED("NOTI_023", "예약 알림 처리에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    CLEANUP_FAILED("NOTI_024", "오래된 알림 정리 작업에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 설정 ===
    FIREBASE_CONFIG_MISSING("NOTI_025", "Firebase 설정 파일을 찾을 수 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),
}
