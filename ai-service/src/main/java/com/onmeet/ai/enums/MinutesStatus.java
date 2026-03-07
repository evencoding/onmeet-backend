package com.onmeet.ai.enums;

public enum MinutesStatus {
    GENERATED,        // AI 생성 완료
    EDITED_BY_USER,   // 사용자가 편집함
    REGENERATING,     // 재생성 진행(선택)
    FAILED;           // 생성/재생성 실패
}
