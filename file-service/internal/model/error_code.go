package model

import "net/http"

// AppError는 에러 코드, HTTP 상태, 메시지를 포함하는 구조화된 에러 타입입니다.
// Kotlin/Java의 BusinessException과 동일한 역할을 합니다.
type AppError struct {
	Code    string
	Status  int
	Message string
}

func (e *AppError) Error() string { return e.Message }

// NewAppError는 AppError 인스턴스를 생성하는 헬퍼 함수입니다.
func NewAppError(code string, status int, message string) *AppError {
	return &AppError{Code: code, Status: status, Message: message}
}

// file-service 에러 코드 상수 (FILE_001 ~ FILE_049)
const (
	// 핸들러 계층 (FILE_001 ~ FILE_013)
	CodeMultipartParseFail      = "FILE_001"
	CodeUploadFail              = "FILE_002"
	CodeAsyncMultipartParseFail = "FILE_003"
	CodeInvalidFileID           = "FILE_004"
	CodeFileNotFound            = "FILE_005"
	CodeDeleteInvalidFileID     = "FILE_006"
	CodeDeleteFail              = "FILE_007"
	CodeUnauthorized            = "FILE_008"
	CodeProfileDeleteFail       = "FILE_009"
	CodeRequestParseFail        = "FILE_010"
	CodeDefaultProfileGenFail   = "FILE_011"
	CodeRenderInvalidFileID     = "FILE_012"
	CodeRenderFail              = "FILE_013"

	// 미들웨어 계층 (FILE_014)
	CodeInvalidGatewaySecret = "FILE_014"

	// 서비스 계층 (FILE_015 ~ FILE_035)
	CodeFileOpenFail           = "FILE_015"
	CodeS3UploadFail           = "FILE_016"
	CodeMetaSaveFail           = "FILE_017"
	CodeDeleteFileNotFound     = "FILE_018"
	CodePermissionFetchFail    = "FILE_019"
	CodePermissionDenied       = "FILE_020"
	CodeCrossCompanyDenied     = "FILE_021"
	CodeS3DeleteFail           = "FILE_022"
	CodeMetaDeleteFail         = "FILE_023"
	CodeProfileListFail        = "FILE_024"
	CodeProfileS3PartialFail   = "FILE_025"
	CodeProfileMetaPartialFail = "FILE_026"
	CodeAsyncFileOpenFail      = "FILE_027"
	CodeAsyncFileCopyFail      = "FILE_028"
	CodeAsyncUploadFail        = "FILE_029"
	CodeAsyncKafkaFail         = "FILE_030"
	CodeDefaultProfileS3Fail   = "FILE_031"
	CodeDefaultProfileMetaFail = "FILE_032"
	CodeRenderMetaNotFound     = "FILE_033"
	CodeS3GetFail              = "FILE_034"
	CodeFileReadFail           = "FILE_035"

	// S3 서비스 계층 (FILE_036 ~ FILE_040)
	CodeMinIOConfigFail    = "FILE_036"
	CodeAWSConfigFail      = "FILE_037"
	CodeS3PutObjectFail    = "FILE_038"
	CodeS3DeleteObjectFail = "FILE_039"
	CodeS3GetObjectFail    = "FILE_040"

	// 리포지토리 계층 (FILE_041 ~ FILE_044)
	CodeDBSaveFail   = "FILE_041"
	CodeDBNotFound   = "FILE_042"
	CodeDBDeleteFail = "FILE_043"
	CodeDBListFail   = "FILE_044"

	// auth 클라이언트 계층 (FILE_045 ~ FILE_048)
	CodeAuthRequestFail = "FILE_045"
	CodeAuthConnFail    = "FILE_046"
	CodeAuthBadResponse = "FILE_047"
	CodeAuthParseFail   = "FILE_048"

	// Kafka 이벤트 (FILE_049)
	CodeKafkaPublishFail = "FILE_049"
)

// 자주 사용되는 AppError 인스턴스 (핸들러/미들웨어에서 재사용)
var (
	ErrMultipartParseFail      = NewAppError(CodeMultipartParseFail, http.StatusBadRequest, "멀티파트 폼 데이터 파싱에 실패했습니다")
	ErrAsyncMultipartParseFail = NewAppError(CodeAsyncMultipartParseFail, http.StatusBadRequest, "비동기 업로드용 멀티파트 폼 데이터 파싱에 실패했습니다")
	ErrInvalidFileID           = NewAppError(CodeInvalidFileID, http.StatusBadRequest, "유효하지 않은 파일 ID입니다")
	ErrDeleteInvalidFileID     = NewAppError(CodeDeleteInvalidFileID, http.StatusBadRequest, "유효하지 않은 파일 ID입니다")
	ErrRenderInvalidFileID     = NewAppError(CodeRenderInvalidFileID, http.StatusBadRequest, "유효하지 않은 파일 ID입니다")
	ErrUnauthorized            = NewAppError(CodeUnauthorized, http.StatusUnauthorized, "인증이 필요합니다")
	ErrRequestParseFail        = NewAppError(CodeRequestParseFail, http.StatusBadRequest, "요청 데이터 파싱에 실패했습니다")
	ErrInvalidGatewaySecret    = NewAppError(CodeInvalidGatewaySecret, http.StatusForbidden, "유효하지 않은 게이트웨이 시크릿입니다")
	ErrPermissionDenied        = NewAppError(CodePermissionDenied, http.StatusForbidden, "파일 삭제 권한이 없습니다. 관리자 이상만 삭제할 수 있습니다")
	ErrCrossCompanyDenied      = NewAppError(CodeCrossCompanyDenied, http.StatusForbidden, "다른 회사의 파일은 삭제할 수 없습니다")
	ErrDBNotFound              = NewAppError(CodeDBNotFound, http.StatusNotFound, "해당 ID의 파일 메타데이터를 찾을 수 없습니다")
)
