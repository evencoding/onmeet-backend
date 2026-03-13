package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * file-service 에러 코드 정의.
 *
 * 참고: file-service는 Go로 작성되어 이 enum을 직접 사용하지 않지만,
 * 에러 코드 레지스트리 일관성을 위해 onmeet-common에 정의한다.
 * Go 쪽에서는 이 코드와 동일한 값을 사용해야 한다.
 */
enum class FileErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === 업로드 ===
    MULTIPART_PARSE_FAILED("FILE_001", "멀티파트 폼 데이터 파싱에 실패했습니다", HttpStatus.BAD_REQUEST),
    UPLOAD_FAILED("FILE_002", "파일 업로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    ASYNC_MULTIPART_PARSE_FAILED("FILE_003", "비동기 업로드용 멀티파트 폼 데이터 파싱에 실패했습니다", HttpStatus.BAD_REQUEST),

    // === 파일 조회 ===
    INVALID_FILE_ID("FILE_004", "유효하지 않은 파일 ID입니다", HttpStatus.BAD_REQUEST),
    FILE_NOT_FOUND("FILE_005", "파일을 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // === 파일 삭제 ===
    DELETE_FAILED("FILE_007", "파일 삭제에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    AUTH_REQUIRED("FILE_008", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    PROFILE_DELETE_FAILED("FILE_009", "프로필 이미지 삭제에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 프로필 ===
    REQUEST_PARSE_FAILED("FILE_010", "요청 데이터 파싱에 실패했습니다", HttpStatus.BAD_REQUEST),
    PROFILE_GENERATE_FAILED("FILE_011", "기본 프로필 이미지 생성에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    RENDER_FAILED("FILE_013", "파일을 찾을 수 없거나 렌더링에 실패했습니다", HttpStatus.NOT_FOUND),

    // === 보안 ===
    INVALID_GATEWAY_SECRET("FILE_014", "유효하지 않은 게이트웨이 시크릿입니다", HttpStatus.FORBIDDEN),

    // === S3 ===
    FILE_OPEN_FAILED("FILE_015", "파일을 열 수 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    S3_UPLOAD_FAILED("FILE_016", "S3 파일 업로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    METADATA_SAVE_FAILED("FILE_017", "파일 메타데이터 저장에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_LOOKUP_FAILED("FILE_018", "삭제할 파일을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PERMISSION_CHECK_FAILED("FILE_019", "사용자 권한 조회에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_PERMISSION_DENIED("FILE_020", "파일 삭제 권한이 없습니다", HttpStatus.FORBIDDEN),
    CROSS_COMPANY_DELETE_DENIED("FILE_021", "다른 회사의 파일은 삭제할 수 없습니다", HttpStatus.FORBIDDEN),
    S3_DELETE_FAILED("FILE_022", "S3 파일 삭제에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    METADATA_DELETE_FAILED("FILE_023", "파일 메타데이터 삭제에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Kafka ===
    KAFKA_EVENT_FAILED("FILE_049", "Kafka 파일 업로드 이벤트 발행에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
}
