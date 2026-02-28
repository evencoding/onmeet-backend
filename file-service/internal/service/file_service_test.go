package service

import (
	"bytes"
	"io"
	"mime/multipart"
	"net/http"
	"strings"
	"testing"
	"time"

	"com.onmeet.file/internal/client"
	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"github.com/patrickmn/go-cache"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// [Necessary Infrastructure / 인프라 필수] Mock 구조체 정의 및 초기화
type mockFileRepository struct {
	mock.Mock
}

func (m *mockFileRepository) Save(metadata *model.FileMetadata) error {
	args := m.Called(metadata)
	if args.Get(0) != nil {
		return args.Error(0)
	}
	// For testing, mock assigning an ID if successful
	metadata.ID = 1
	return nil
}

func (m *mockFileRepository) FindByID(id uint) (*model.FileMetadata, error) {
	args := m.Called(id)
	if meta, ok := args.Get(0).(*model.FileMetadata); ok {
		return meta, args.Error(1)
	}
	return nil, args.Error(1)
}

func (m *mockFileRepository) Delete(id uint) error {
	args := m.Called(id)
	return args.Error(0)
}

func (m *mockFileRepository) FindByUploaderAndCategory(uploaderId int64, category string) ([]*model.FileMetadata, error) {
	args := m.Called(uploaderId, category)
	if files, ok := args.Get(0).([]*model.FileMetadata); ok {
		return files, args.Error(1)
	}
	return nil, args.Error(1)
}

// [Necessary Infrastructure / 인프라 필수] Mock 구조체 정의 및 초기화
type MockS3Service struct {
	mock.Mock
}

func (m *MockS3Service) UploadFile(key string, content io.Reader, contentType string) error {
	args := m.Called(key, content, contentType)
	return args.Error(0)
}

func (m *MockS3Service) DeleteFile(key string) error {
	args := m.Called(key)
	return args.Error(0)
}

func (m *MockS3Service) GetFile(key string) (io.ReadCloser, string, error) {
	args := m.Called(key)
	if rc, ok := args.Get(0).(io.ReadCloser); ok {
		return rc, args.String(1), args.Error(2)
	}
	return nil, args.String(1), args.Error(2)
}

// [Necessary Infrastructure / 인프라 필수] Mock 구조체 정의 및 초기화
type MockEventProducer struct {
	mock.Mock
}

func (m *MockEventProducer) SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error {
	args := m.Called(topic, fileId, fileName, fileUrl, uploaderId, correlationId)
	return args.Error(0)
}

// [Necessary Infrastructure / 인프라 필수] Mock 구조체 정의 및 초기화
type MockAuthClient struct {
	mock.Mock
}

func (m *MockAuthClient) GetUserPermissions(userId int64, cookie string) (*client.UserPermissionResponse, error) {
	args := m.Called(userId, cookie)
	if res, ok := args.Get(0).(*client.UserPermissionResponse); ok {
		return res, args.Error(1)
	}
	return nil, args.Error(1)
}

// createMultipartFileHeader: 테스트를 위한 가상 HTTP 멀티파트 파일 헤더를 생성하는 헬퍼 함수
func createMultipartFileHeader(filename string, content []byte) (*multipart.FileHeader, error) {
	body := new(bytes.Buffer)
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", filename)
	if err != nil {
		return nil, err
	}
	part.Write(content)
	writer.Close() // Close을 해야 멀티파트 바운더리가 올바르게 닫힘

	req, _ := http.NewRequest("POST", "/", body)
	req.Header.Add("Content-Type", writer.FormDataContentType())
	err = req.ParseMultipartForm(10 << 20)
	if err != nil {
		return nil, err
	}
	return req.MultipartForm.File["file"][0], nil
}

// --- Tests ---

func TestFileService_UploadFiles(t *testing.T) {
	// [Essential / 필수] 파일 업로드 핵심 비즈니스 로직 검증 (동기/비동기)
	t.Run("성공적인 파일 업로드 (동기)", func(t *testing.T) {
		repoMock := new(mockFileRepository)
		s3Mock := new(MockS3Service)
		epMock := new(MockEventProducer)
		authMock := new(MockAuthClient)
		cfg := &config.Config{CloudFrontDomain: "cdn.test.com"}

		fs := NewFileService(repoMock, s3Mock, epMock, authMock, cfg)

		header, err := createMultipartFileHeader("test.txt", []byte("hello world"))
		assert.NoError(t, err)

		uploaderId := int64(123)

		s3Mock.On("UploadFile", mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("string")).Return(nil)
		repoMock.On("Save", mock.AnythingOfType("*model.FileMetadata")).Return(nil)

		results, err := fs.UploadFiles([]*multipart.FileHeader{header}, "documents", &uploaderId, "USER", "123")

		assert.NoError(t, err)
		assert.Len(t, results, 1)
		assert.Equal(t, "test.txt", results[0].OriginalFileName)
		assert.Equal(t, "documents", results[0].Category)
		assert.Equal(t, &uploaderId, results[0].UploaderID)
		assert.True(t, strings.HasPrefix(results[0].S3URL, "https://cdn.test.com/USER/123/documents/"))

		s3Mock.AssertExpectations(t)
		repoMock.AssertExpectations(t)
	})

	t.Run("비동기 업로드 시 이벤트 발행 확인", func(t *testing.T) {
		repoMock := new(mockFileRepository)
		s3Mock := new(MockS3Service)
		epMock := new(MockEventProducer)
		authMock := new(MockAuthClient)
		cfg := &config.Config{CloudFrontDomain: "cdn.test.com"}

		fs := NewFileService(repoMock, s3Mock, epMock, authMock, cfg)

		header, err := createMultipartFileHeader("async_test.txt", []byte("async content"))
		assert.NoError(t, err)

		uploaderId := int64(456)
		correlationId := "async-corr-id"

		s3Mock.On("UploadFile", mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("string")).Return(nil)
		repoMock.On("Save", mock.AnythingOfType("*model.FileMetadata")).Return(nil)
		epMock.On("SendFileUploadEvent", mock.AnythingOfType("string"), mock.AnythingOfType("uint"), mock.AnythingOfType("string"), mock.AnythingOfType("string"), uploaderId, correlationId).Return(nil)

		fs.UploadFileAsync(header, "async_docs", &uploaderId, "USER", "456", "topic", correlationId)

		// Wait briefly for goroutine to process
		time.Sleep(100 * time.Millisecond)

		s3Mock.AssertExpectations(t)
		repoMock.AssertExpectations(t)
		epMock.AssertExpectations(t)
	})
}

func TestFileService_GetFile(t *testing.T) {
	// [Essential / 필수] 파일 메타데이터 조회 조회 로직 검증
	repoMock := new(mockFileRepository)
	fs := NewFileService(repoMock, nil, nil, nil, nil)

	expectedMeta := &model.FileMetadata{FileName: "test.txt"}
	repoMock.On("FindByID", uint(1)).Return(expectedMeta, nil)

	meta, err := fs.GetFile(1)

	assert.NoError(t, err)
	assert.Equal(t, expectedMeta, meta)
	repoMock.AssertExpectations(t)
}

func TestFileService_DeleteFile(t *testing.T) {
	// [Essential / 필수] 권한 기반 파일 삭제 로직 검증 (보안 요구사항)
	t.Run("본인 파일 삭제 성공", func(t *testing.T) {
		repoMock := new(mockFileRepository)
		s3Mock := new(MockS3Service)
		authMock := new(MockAuthClient)
		fs := NewFileService(repoMock, s3Mock, nil, authMock, nil)

		uploaderId := int64(123)
		meta := &model.FileMetadata{
			OwnerType:  "USER",
			OwnerID:    "123",
			Category:   "docs",
			FileName:   "test.pdf",
			UploaderID: &uploaderId,
		}

		companyId := int64(1)
		repoMock.On("FindByID", uint(1)).Return(meta, nil)
		authMock.On("GetUserPermissions", int64(123), "cookie").Return(&client.UserPermissionResponse{
			UserID:    123,
			CompanyID: &companyId,
			Roles:     []string{"MANAGER"},
		}, nil)
		s3Mock.On("DeleteFile", "USER/123/docs/test.pdf").Return(nil)
		repoMock.On("Delete", uint(1)).Return(nil)

		err := fs.DeleteFile(1, 123, "cookie")

		assert.NoError(t, err)
		repoMock.AssertExpectations(t)
		s3Mock.AssertExpectations(t)
		authMock.AssertExpectations(t)
	})

	t.Run("권한 미달로 인한 삭제 실패", func(t *testing.T) {
		repoMock := new(mockFileRepository)
		authMock := new(MockAuthClient)
		fs := NewFileService(repoMock, nil, nil, authMock, nil)

		uploaderId := int64(123)
		meta := &model.FileMetadata{
			UploaderID: &uploaderId,
		}

		repoMock.On("FindByID", uint(1)).Return(meta, nil)
		authMock.On("GetUserPermissions", int64(456), "cookie").Return(&client.UserPermissionResponse{
			UserID: 456,
			Roles:  []string{"USER"},
		}, nil)

		err := fs.DeleteFile(1, 456, "cookie")

		assert.Error(t, err)
		assert.Contains(t, err.Error(), "permission denied")
		repoMock.AssertExpectations(t)
		authMock.AssertExpectations(t)
	})
}

func TestFileService_GenerateDefaultProfileImage(t *testing.T) {
	// [Essential / 필수] 기본 프로필 이미지 생성 로직 및 S3 업로드 검증
	repoMock := new(mockFileRepository)
	s3Mock := new(MockS3Service)
	cfg := &config.Config{CloudFrontDomain: "cdn.test.com"}
	fs := NewFileService(repoMock, s3Mock, nil, nil, cfg)

	uploaderId := int64(123)

	s3Mock.On("UploadFile", mock.AnythingOfType("string"), mock.Anything, "image/svg+xml").Return(nil)
	repoMock.On("Save", mock.AnythingOfType("*model.FileMetadata")).Return(nil)

	meta, err := fs.GenerateDefaultProfileImage("Test", "blue", &uploaderId, "USER", "123")

	assert.NoError(t, err)
	assert.NotNil(t, meta)
	assert.Equal(t, "profile", meta.Category)
	assert.Contains(t, meta.S3URL, "USER/123/profile/")

	s3Mock.AssertExpectations(t)
	repoMock.AssertExpectations(t)
}

func TestFileService_RenderFile(t *testing.T) {
	// [Essential / 필수] 파일 렌더링 및 인메모리 캐싱 전략 검증
	repoMock := new(mockFileRepository)
	s3Mock := new(MockS3Service)
	fs := NewFileService(repoMock, s3Mock, nil, nil, nil)

	uploaderId := int64(123)
	meta := &model.FileMetadata{
		OwnerType:   "USER",
		OwnerID:     "123",
		Category:    "docs",
		FileName:    "test.txt",
		ContentType: "text/plain",
		UploaderID:  &uploaderId,
	}

	// [Scenario 1] Cache Miss - 첫 요청시 DB와 S3에서 실제 데이터를 가져옴
	repoMock.On("FindByID", uint(1)).Return(meta, nil)
	s3Mock.On("GetFile", "USER/123/docs/test.txt").Return(io.NopCloser(bytes.NewReader([]byte("file content"))), "text/plain", nil).Once()

	content, contentType, err := fs.RenderFile(1)

	assert.NoError(t, err)
	assert.Equal(t, "file content", string(content))
	assert.Equal(t, "text/plain", contentType)

	// [Scenario 2] Cache Hit - 두 번째 요청시 S3와 DB 모두 호출하지 않음 (Bug-10 수정 검증)
	content2, contentType2, err2 := fs.RenderFile(1)

	assert.NoError(t, err2)
	assert.Equal(t, "file content", string(content2))
	assert.Equal(t, "text/plain", contentType2)

	repoMock.AssertExpectations(t) // FindByID는 한 번만 호출됨
	s3Mock.AssertExpectations(t)   // GetFile도 한 번만 호출됨
}

func TestFileService_DeleteMyProfile_PartialFailure(t *testing.T) {
	// [Bug-3 수정 검증] DeleteMyProfile에서 부분 실패 시 에러 수집 및 반환
	repoMock := new(mockFileRepository)
	s3Mock := new(MockS3Service)
	fs := NewFileService(repoMock, s3Mock, nil, nil, nil)

	uploaderId := int64(123)
	files := []*model.FileMetadata{
		{ID: 1, OwnerType: "USER", OwnerID: "123", Category: "profile", FileName: "file1.jpg"},
		{ID: 2, OwnerType: "USER", OwnerID: "123", Category: "profile", FileName: "file2.jpg"},
		{ID: 3, OwnerType: "USER", OwnerID: "123", Category: "profile", FileName: "file3.jpg"},
	}

	repoMock.On("FindByUploaderAndCategory", uploaderId, "profile").Return(files, nil)
	s3Mock.On("DeleteFile", "USER/123/profile/file1.jpg").Return(nil)
	s3Mock.On("DeleteFile", "USER/123/profile/file2.jpg").Return(assert.AnError) // S3 삭제 실패
	s3Mock.On("DeleteFile", "USER/123/profile/file3.jpg").Return(nil)
	repoMock.On("Delete", uint(1)).Return(nil)
	repoMock.On("Delete", uint(3)).Return(assert.AnError) // DB 삭제 실패

	err := fs.DeleteMyProfile(uploaderId)

	// 부분 실패가 발생하므로 에러가 반환되어야 함
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "partial delete errors")
	assert.Contains(t, err.Error(), "S3 delete failed")
	assert.Contains(t, err.Error(), "DB delete failed")

	repoMock.AssertExpectations(t)
	s3Mock.AssertExpectations(t)
}

func TestFileService_UploadFileAsync_GoroutineSafety(t *testing.T) {
	// [Bug-11 수정 검증] 고루틴 진입 전 파일 복사로 안전성 확보
	repoMock := new(mockFileRepository)
	s3Mock := new(MockS3Service)
	epMock := new(MockEventProducer)
	cfg := &config.Config{CloudFrontDomain: "cdn.test.com"}
	fs := NewFileService(repoMock, s3Mock, epMock, nil, cfg)

	header, err := createMultipartFileHeader("async_safe.txt", []byte("safe content"))
	assert.NoError(t, err)

	uploaderId := int64(789)

	s3Mock.On("UploadFile", mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("string")).Return(nil)
	repoMock.On("Save", mock.AnythingOfType("*model.FileMetadata")).Return(nil)
	epMock.On("SendFileUploadEvent", mock.AnythingOfType("string"), mock.AnythingOfType("uint"), mock.AnythingOfType("string"), mock.AnythingOfType("string"), uploaderId, "corr-id").Return(nil)

	// 고루틴 실행
	fs.UploadFileAsync(header, "safe_docs", &uploaderId, "USER", "789", "topic", "corr-id")

	// 고루틴 완료 대기
	time.Sleep(200 * time.Millisecond)

	s3Mock.AssertExpectations(t)
	repoMock.AssertExpectations(t)
	epMock.AssertExpectations(t)
}

func TestFileService_RenderFile_CacheTTLExpiration(t *testing.T) {
	// [Bug-9, 10 Edge Case] 캐시 TTL 만료 후 재호출 시 캐시 miss 및 DB/S3 재조회 검증
	repoMock := new(mockFileRepository)
	s3Mock := new(MockS3Service)

	// 매우 짧은 TTL(50ms)로 fileService를 직접 생성하여 테스트
	cfg := &config.Config{CloudFrontDomain: "cdn.test.com"}
	uploaderId := int64(123)

	// fileService를 직접 만들되, TTL을 매우 짧게 설정
	fs := &fileService{
		repo:       repoMock,
		s3:         s3Mock,
		cfg:        cfg,
		fileCache:  cache.New(50*time.Millisecond, 10*time.Millisecond), // TTL: 50ms, Cleanup: 10ms
	}

	meta := &model.FileMetadata{
		OwnerType:   "USER",
		OwnerID:     "123",
		Category:    "docs",
		FileName:    "test.txt",
		ContentType: "text/plain",
		UploaderID:  &uploaderId,
	}

	// [Phase 1] 첫 번째 호출 - DB와 S3에서 데이터를 가져오고 캐시에 저장
	repoMock.On("FindByID", uint(1)).Return(meta, nil).Once()
	s3Mock.On("GetFile", "USER/123/docs/test.txt").Return(
		io.NopCloser(bytes.NewReader([]byte("cached content"))),
		"text/plain",
		nil,
	).Once()

	content1, contentType1, err1 := fs.RenderFile(1)

	assert.NoError(t, err1)
	assert.Equal(t, "cached content", string(content1))
	assert.Equal(t, "text/plain", contentType1)

	// [Phase 2] TTL이 만료되기 전 즉시 재호출 - 캐시 히트 (DB/S3 호출 없음)
	content2, contentType2, err2 := fs.RenderFile(1)

	assert.NoError(t, err2)
	assert.Equal(t, "cached content", string(content2))
	assert.Equal(t, "text/plain", contentType2)

	// Mock 검증: FindByID와 GetFile은 여전히 1회만 호출됨 (캐시 히트)
	repoMock.AssertExpectations(t)
	s3Mock.AssertExpectations(t)

	// [Phase 3] TTL 만료 대기 (100ms > 50ms TTL)
	time.Sleep(100 * time.Millisecond)

	// TTL 만료 후 재호출을 위한 Mock 설정 (두 번째 호출)
	repoMock.On("FindByID", uint(1)).Return(meta, nil).Once()
	s3Mock.On("GetFile", "USER/123/docs/test.txt").Return(
		io.NopCloser(bytes.NewReader([]byte("fresh content after expiry"))),
		"text/plain",
		nil,
	).Once()

	// TTL 만료 후 재호출 - 캐시 미스로 DB와 S3를 다시 조회해야 함
	content3, contentType3, err3 := fs.RenderFile(1)

	assert.NoError(t, err3)
	assert.Equal(t, "fresh content after expiry", string(content3))
	assert.Equal(t, "text/plain", contentType3)

	// Mock 검증: FindByID와 GetFile이 각각 2회씩 호출됨 (첫 호출 + TTL 만료 후 재호출)
	repoMock.AssertExpectations(t)
	s3Mock.AssertExpectations(t)
}
