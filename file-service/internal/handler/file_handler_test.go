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
	// Setup
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
