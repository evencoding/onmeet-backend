package com.onmeet.common.exception

import org.springframework.http.HttpStatus

/**
 * 모든 서비스의 에러 코드가 구현해야 하는 인터페이스.
 * 각 서비스는 이 인터페이스를 구현한 enum을 정의한다.
 */
interface ErrorCode {
    /** 고유 에러 코드 (예: AUTH_001, VIDEO_002) */
    val code: String
    /** 프론트엔드에 전달될 한글 에러 메시지 */
    val message: String
    /** HTTP 상태 코드 */
    val status: HttpStatus
}
