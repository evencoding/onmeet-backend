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
