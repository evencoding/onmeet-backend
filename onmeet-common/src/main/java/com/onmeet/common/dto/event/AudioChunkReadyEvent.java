package com.onmeet.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 서비스 간 공유되는 오디오 청크 준비 완료 이벤트 DTO (Java 기반)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioChunkReadyEvent {
    /** 회의실 ID */
    private Long roomId;

    /** 참가자 식별자 (identity) */
    private String participantIdentity;

    /** 오디오 청크 세그먼트 인덱스 */
    private int segmentIndex;

    /** 
     * 파일 서버(file-service) 고유 ID 
     * 마이그레이션 완료 시 필수 필드가 됩니다.
     */
    private Long fileId;

    /** 
     * S3 물리 경로 
     * 하이브리드 운영 기간 동안 하위 호환성을 위해 유지합니다.
     */
    private String s3Path;

    /** 오디오 청크 시작 시간 */
    private Instant startTime;

    /** 오디오 청크 종료 시간 */
    private Instant endTime;

    /** 이벤트 발행 시점 */
    private Instant timestamp;
}
