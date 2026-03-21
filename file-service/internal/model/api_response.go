package model

// ApiResponse는 프론트엔드가 기대하는 공통 응답 래퍼입니다.
// 성공: { "success": true, "data": T }
// 실패: { "success": false, "error": { "code": "...", "status": 400, "message": "..." } }
type ApiResponse struct {
	Success bool        `json:"success"`
	Data    interface{} `json:"data,omitempty"`
	Error   *ErrorDetail `json:"error,omitempty"`
}

// ErrorDetail은 에러 응답의 세부 정보입니다.
type ErrorDetail struct {
	Code    string `json:"code,omitempty"`
	Status  int    `json:"status"`
	Message string `json:"message"`
}

// SuccessResponse는 성공 응답 ApiResponse를 생성합니다.
func SuccessResponse(data interface{}) ApiResponse {
	return ApiResponse{Success: true, Data: data}
}

// ErrorApiResponse는 에러 응답 ApiResponse를 생성합니다.
func ErrorApiResponse(status int, message string, code string) ApiResponse {
	return ApiResponse{Success: false, Error: &ErrorDetail{Code: code, Status: status, Message: message}}
}

// ErrorApiResponseFromAppError는 AppError로부터 에러 ApiResponse를 생성합니다.
func ErrorApiResponseFromAppError(appErr *AppError) ApiResponse {
	return ErrorApiResponse(appErr.Status, appErr.Message, appErr.Code)
}
