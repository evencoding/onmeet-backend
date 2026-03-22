package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * ai-service 에러 코드 정의.
 */
enum class AiErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === 회의록 ===
    MINUTES_NOT_FOUND("AI_001", "해당 회의실의 회의록을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    S3_READ_FAILED("AI_002", "S3에서 파일을 읽는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSCRIPT_PARSE_FAILED("AI_003", "트랜스크립트 JSON 파싱 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSCRIPT_EMPTY("AI_004", "트랜스크립트가 비어 있어 처리할 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY),
    SUMMARIZE_FAILED("AI_005", "AI 요약 처리 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    S3_WRITE_FAILED("AI_006", "S3에 파일을 저장하는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === STT/오디오 ===
    AUDIO_CHUNK_READ_FAILED("AI_007", "오디오 청크를 S3에서 읽는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    AUDIO_DECODE_FAILED("AI_008", "오디오 디코딩에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    VAD_UNSUPPORTED_SAMPLE_RATE("AI_009", "VAD는 16kHz 샘플레이트만 지원합니다", HttpStatus.BAD_REQUEST),
    AUDIO_ENCODE_FAILED("AI_010", "오디오 WAV 인코딩에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    STT_FAILED("AI_011", "음성인식(STT) 처리 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === DB ===
    MINUTES_SAVE_FAILED("AI_012", "회의록 데이터베이스 저장 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    EVENT_PUBLISH_FAILED("AI_013", "이벤트 발행 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Redis ===
    REDIS_READ_FAILED("AI_014", "Redis에서 데이터를 읽는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_EVENT_PARSE_FAILED("AI_015", "Redis 이벤트 JSON 파싱 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSCRIPT_SERIALIZE_FAILED("AI_016", "트랜스크립트 JSON 직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSCRIPT_EVENT_PUBLISH_FAILED("AI_017", "트랜스크립트 완료 이벤트 발행 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 외부 API ===
    CLAUDE_API_KEY_MISSING("AI_018", "Anthropic API 키가 설정되지 않았습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    CLAUDE_REQUEST_SERIALIZE_FAILED("AI_019", "Claude API 요청 직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    CLAUDE_API_FAILED("AI_020", "Claude API 호출 중 오류가 발생했습니다", HttpStatus.BAD_GATEWAY),
    CLAUDE_API_TIMEOUT("AI_021", "Claude API 연결 시간이 초과되었습니다", HttpStatus.GATEWAY_TIMEOUT),
    CLAUDE_RESPONSE_PARSE_FAILED("AI_022", "Claude API 응답 파싱 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    CLAUDE_EMPTY_RESPONSE("AI_023", "Claude API 응답에 요약 내용이 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    OPENAI_API_KEY_MISSING("AI_024", "OpenAI API 키가 설정되지 않았습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    OPENAI_API_FAILED("AI_025", "OpenAI STT API 호출 중 오류가 발생했습니다", HttpStatus.BAD_GATEWAY),
    OPENAI_API_TIMEOUT("AI_026", "OpenAI API 연결 시간이 초과되었습니다", HttpStatus.GATEWAY_TIMEOUT),

    // === S3 상세 ===
    S3_FILE_NOT_FOUND("AI_027", "S3에서 해당 파일을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    S3_CLIENT_ERROR("AI_028", "S3 파일 읽기 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    S3_UPLOAD_FAILED("AI_029", "S3 파일 쓰기 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Redis 저장 ===
    REDIS_CHAT_SAVE_FAILED("AI_030", "채팅 이벤트를 Redis에 저장하는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_VOICE_SAVE_FAILED("AI_031", "음성 세그먼트 이벤트를 Redis에 저장하는 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_DATA_FORMAT_ERROR("AI_032", "Redis 이벤트 데이터 형식이 올바르지 않습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === VAD ===
    VAD_MODEL_LOAD_FAILED("AI_033", "Silero VAD 모델 로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    VAD_INFERENCE_FAILED("AI_035", "VAD ONNX 추론 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Kafka 역직렬화 ===
    AUDIO_CHUNK_DESERIALIZE_FAILED("AI_038", "오디오 청크 이벤트 역직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSCRIPT_FINALIZED_DESERIALIZE_FAILED("AI_039", "트랜스크립트 완료 이벤트 역직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    MEETING_ENDED_DESERIALIZE_FAILED("AI_040", "회의 종료 이벤트 역직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    CHAT_EVENT_DESERIALIZE_FAILED("AI_041", "채팅 이벤트 역직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    VOICE_SEGMENT_DESERIALIZE_FAILED("AI_042", "음성 세그먼트 이벤트 역직렬화 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
}
