package model

import "time"

// ErrorResponse는 Java측 ErrorResponse.kt와 동일한 형식의 에러 응답 구조체입니다.
// Java 측 형식: { "status": 400, "message": "error message", "timestamp": 1234567890123 }
type ErrorResponse struct {
	Status    int    `json:"status" example:"404"`
	Message   string `json:"message" example:"File not found"`
	Timestamp int64  `json:"timestamp" example:"1234567890123"`
}

// NewErrorResponse는 ErrorResponse를 생성하는 헬퍼 함수입니다.
func NewErrorResponse(status int, message string) ErrorResponse {
	return ErrorResponse{
		Status:    status,
		Message:   message,
		Timestamp: time.Now().UnixMilli(), // Java의 Instant.now().toEpochMilli()와 동일
	}
}
