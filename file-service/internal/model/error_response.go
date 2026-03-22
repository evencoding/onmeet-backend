package model

import "time"

// ErrorResponse는 Kotlin/Java측 ErrorResponse와 동일한 형식의 에러 응답 구조체입니다.
// 통일된 형식: { "code": "FILE_001", "status": 400, "message": "...", "timestamp": 1234567890123 }
type ErrorResponse struct {
	Code      string `json:"code" example:"FILE_001"`
	Status    int    `json:"status" example:"400"`
	Message   string `json:"message" example:"멀티파트 폼 데이터 파싱에 실패했습니다"`
	Timestamp int64  `json:"timestamp" example:"1234567890123"`
}

// NewErrorResponse는 ErrorResponse를 생성하는 헬퍼 함수입니다.
func NewErrorResponse(code string, status int, message string) ErrorResponse {
	return ErrorResponse{
		Code:      code,
		Status:    status,
		Message:   message,
		Timestamp: time.Now().UnixMilli(),
	}
}

// ErrorResponseFromAppError는 AppError에서 ErrorResponse를 생성하는 편의 함수입니다.
func ErrorResponseFromAppError(appErr *AppError) ErrorResponse {
	return NewErrorResponse(appErr.Code, appErr.Status, appErr.Message)
}
