package handler

import (
	"bytes"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"

	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/service"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// MockFileService defines a mock for the FileService interface
type MockFileService struct {
	mock.Mock
}

func (m *MockFileService) UploadFiles(files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error) {
	args := m.Called(files, category, uploaderId, ownerType, ownerId)
	return args.Get(0).([]*model.FileMetadata), args.Error(1)
}

func (m *MockFileService) GetFile(id uint) (*model.FileMetadata, error) {
	args := m.Called(id)
	return args.Get(0).(*model.FileMetadata), args.Error(1)
}

func (m *MockFileService) DeleteFile(id uint, requesterId int64, cookie string) error {
	args := m.Called(id, requesterId, cookie)
	return args.Error(0)
}

func (m *MockFileService) UploadFileAsync(file *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string) {
	m.Called(file, category, uploaderId, ownerType, ownerId, callbackTopic, correlationId)
}

func (m *MockFileService) GenerateDefaultProfileImage(name string, color string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	args := m.Called(name, color, uploaderId, ownerType, ownerId)
	return args.Get(0).(*model.FileMetadata), args.Error(1)
}

func (m *MockFileService) RenderFile(id uint) ([]byte, string, error) {
	args := m.Called(id)
	return args.Get(0).([]byte), args.String(1), args.Error(2)
}

func (m *MockFileService) DeleteMyProfile(uploaderId int64) error {
	args := m.Called(uploaderId)
	return args.Error(0)
}

// Ensure MockFileService implements service.FileService
var _ service.FileService = (*MockFileService)(nil)

func TestFileHandler_GenerateProfileImage(t *testing.T) {
	// [Necessary Infrastructure / 인프라 필수] Gin 모드 설정 및 Mock 초기화
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.POST("/profile/default", handler.GenerateProfileImage)

	// Mock Expectation
	expectedMetadata := &model.FileMetadata{
		FileName: "test.svg",
		S3URL:    "https://example.com/test.svg",
	}
	mockService.On("GenerateDefaultProfileImage", "TestUser", "blue", (*int64)(nil), "USER", "user123").Return(expectedMetadata, nil)

	// Request Body
	reqBody := map[string]string{
		"name":      "TestUser",
		"color":     "blue",
		"ownerType": "USER",
		"ownerId":   "user123",
	}
	jsonBody, _ := json.Marshal(reqBody)

	// Perform Request
	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/profile/default", bytes.NewBuffer(jsonBody))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)

	// Assertions
	assert.Equal(t, http.StatusOK, w.Code)

	var response model.FileMetadata
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.Equal(t, "test.svg", response.FileName)

	mockService.AssertExpectations(t)
}

// createMultipartRequest: 파일 업로드 API 테스트를 위해 multipart/form-data 형태의 HTTP 요청을 생성하는 헬퍼 함수
func createMultipartRequest(t *testing.T, uri string, fields map[string]string, filename string, content []byte) *http.Request {
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// 일반 필드 추가 (category, ownerType 등)
	for k, v := range fields {
		_ = writer.WriteField(k, v)
	}
	// 파일 필드 추가
	if filename != "" {
		part, _ := writer.CreateFormFile("files", filename)
		part.Write(content)
	}
	writer.Close()

	req, _ := http.NewRequest("POST", uri, body)
	req.Header.Set("Content-Type", writer.FormDataContentType()) // 바운더리 정보가 포함된 Content-Type 설정 필수
	return req
}

func TestFileHandler_Upload(t *testing.T) {
	// [Essential / 필수] 파일 업로드 엔드포인트 및 멀티파트 데이터 처리 로직 검증
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.POST("/upload", func(c *gin.Context) {
		// 미들웨어를 통해 설정되는 userId 컬렉션을 수동으로 주입하여 인증 상태 시뮬레이션
		c.Set("userId", "123")
		handler.Upload(c)
	})

	expectedMetadata := []*model.FileMetadata{{FileName: "test.txt"}}
	uploaderId := int64(123)
	// 서비스 레이어 호출 예상 설정
	mockService.On("UploadFiles", mock.AnythingOfType("[]*multipart.FileHeader"), "docs", &uploaderId, "USER", "user123").Return(expectedMetadata, nil)

	// 멀티파트 요청 생성
	req := createMultipartRequest(t, "/upload", map[string]string{
		"category":  "docs",
		"ownerType": "USER",
		"ownerId":   "user123",
	}, "test.txt", []byte("file content"))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	// 응답 검증
	assert.Equal(t, http.StatusOK, w.Code)
	mockService.AssertExpectations(t)
}

func TestFileHandler_UploadAsync(t *testing.T) {
	// [Essential / 필수] 비동기 파일 업로드 요청 처리 및 202 Accepted 응답 검증
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.POST("/upload-async", func(c *gin.Context) {
		c.Set("userId", "123")
		handler.UploadAsync(c)
	})

	uploaderId := int64(123)
	mockService.On("UploadFileAsync", mock.AnythingOfType("*multipart.FileHeader"), "docs", &uploaderId, "USER", "user123", "topic", "corr-123").Return()

	req := createMultipartRequest(t, "/upload-async", map[string]string{
		"category":      "docs",
		"ownerType":     "USER",
		"ownerId":       "user123",
		"callbackTopic": "topic",
		"correlationId": "corr-123",
	}, "test.txt", []byte("file content"))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code)
	mockService.AssertExpectations(t)
}

func TestFileHandler_GetFileInfo(t *testing.T) {
	// [Essential / 필수] 파일 정보 조회 API의 정상 동작 및 메타데이터 반환 검증
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/:fileId", handler.GetFileInfo)

	expectedMetadata := &model.FileMetadata{FileName: "test.txt"}
	mockService.On("GetFile", uint(1)).Return(expectedMetadata, nil)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/1", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	mockService.AssertExpectations(t)
}

func TestFileHandler_DeleteFile(t *testing.T) {
	// [Essential / 필수] 파일 삭제 API 호출 및 서비스 레이어 권한 파라미터 전달 검증
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.DELETE("/:fileId", func(c *gin.Context) {
		c.Set("userId", "123")
		handler.DeleteFile(c)
	})

	mockService.On("DeleteFile", uint(1), int64(123), mock.Anything).Return(nil)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/1", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNoContent, w.Code)
	mockService.AssertExpectations(t)
}

func TestFileHandler_RenderFile(t *testing.T) {
	// [Essential / 필수] 파일 스트리밍/렌더링 API의 Content-Type 및 데이터 수신 검증
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/render/:fileId", handler.RenderFile)

	content := []byte("preview data")
	mockService.On("RenderFile", uint(1)).Return(content, "text/plain", nil)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/render/1", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "text/plain", w.Header().Get("Content-Type"))
	assert.Equal(t, "preview data", w.Body.String())
	mockService.AssertExpectations(t)
}

// --- Bug-7 수정 검증 테스트: 에러 파싱 누락 수정 ---

func TestFileHandler_Upload_InvalidMultipartForm(t *testing.T) {
	// [Bug-7 수정 검증] 잘못된 multipart form 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.POST("/upload", handler.Upload)

	// 잘못된 Content-Type (multipart가 아님)
	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/upload", bytes.NewBuffer([]byte("invalid")))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid multipart form")
}

func TestFileHandler_UploadAsync_InvalidMultipartForm(t *testing.T) {
	// [Bug-7 수정 검증] 비동기 업로드에서 잘못된 multipart form 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.POST("/upload-async", handler.UploadAsync)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/upload-async", bytes.NewBuffer([]byte("invalid")))
	req.Header.Set("Content-Type", "text/plain")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid multipart form")
}

func TestFileHandler_GetFileInfo_InvalidFileId(t *testing.T) {
	// [Bug-7 수정 검증] 잘못된 파일 ID 형식 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/:fileId", handler.GetFileInfo)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/invalid-id", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

func TestFileHandler_DeleteFile_InvalidFileId(t *testing.T) {
	// [Bug-7 수정 검증] 잘못된 파일 ID로 삭제 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.DELETE("/:fileId", handler.DeleteFile)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/abc123", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

func TestFileHandler_RenderFile_InvalidFileId(t *testing.T) {
	// [Bug-7 수정 검증] 잘못된 파일 ID로 렌더링 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/render/:fileId", handler.RenderFile)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/render/not-a-number", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

// ===== [Bug-7 Extreme Edge Cases] 극단적인 파싱 에러 케이스 =====

func TestFileHandler_GetFileInfo_MaxUint64Overflow(t *testing.T) {
	// [Bug-7 Extreme Edge Case] MaxUint64를 초과하는 숫자로 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/:fileId", handler.GetFileInfo)

	// MaxUint64 = 18446744073709551615, 이보다 큰 숫자
	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/18446744073709551616", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

func TestFileHandler_DeleteFile_NegativeFileId(t *testing.T) {
	// [Bug-7 Extreme Edge Case] 음수 파일 ID로 삭제 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.DELETE("/:fileId", handler.DeleteFile)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/-1", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

func TestFileHandler_RenderFile_VeryLargeNumber(t *testing.T) {
	// [Bug-7 Extreme Edge Case] 매우 큰 숫자 문자열로 렌더링 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/render/:fileId", handler.RenderFile)

	// 100자리 숫자 (ParseUint가 처리할 수 없는 크기)
	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/render/99999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid file ID")
}

func TestFileHandler_GetFileInfo_SpecialCharacters(t *testing.T) {
	// [Bug-7 Extreme Edge Case] 특수문자가 포함된 파일 ID로 요청 시 400 에러 반환
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.GET("/:fileId", handler.GetFileInfo)

	testCases := []string{
		"123abc!@#",
		"12.34",
		"0x1234", // hexadecimal
		"1e10",   // scientific notation
		"",       // empty string (Gin may handle this differently)
	}

	for _, tc := range testCases {
		w := httptest.NewRecorder()
		req, _ := http.NewRequest("GET", "/"+tc, nil)
		router.ServeHTTP(w, req)

		// Empty string may route to a different handler, so we only check non-empty cases
		if tc != "" {
			assert.Equal(t, http.StatusBadRequest, w.Code, "Failed for input: "+tc)
			assert.Contains(t, w.Body.String(), "Invalid file ID", "Failed for input: "+tc)
		}
	}
}

func TestFileHandler_DeleteFile_ZeroFileId(t *testing.T) {
	// [Bug-7 Edge Case] 0번 파일 ID는 유효한 uint이지만 실제로는 존재하지 않는 ID
	// (DB의 auto-increment는 보통 1부터 시작)
	gin.SetMode(gin.TestMode)
	mockService := new(MockFileService)
	handler := NewFileHandler(mockService)

	router := gin.Default()
	router.DELETE("/:fileId", func(c *gin.Context) {
		c.Set("userId", "123")
		handler.DeleteFile(c)
	})

	// 0은 파싱은 성공하지만 서비스 레이어에서 NotFound로 처리될 가능성이 높음
	mockService.On("DeleteFile", uint(0), int64(123), mock.Anything).Return(assert.AnError)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/0", nil)
	router.ServeHTTP(w, req)

	// 0은 유효한 uint이므로 파싱은 성공, 하지만 서비스에서 에러 발생
	assert.NotEqual(t, http.StatusBadRequest, w.Code) // 파싱 에러는 아님
	mockService.AssertExpectations(t)
}
