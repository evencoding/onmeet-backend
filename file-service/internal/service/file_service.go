package service

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"log"
	"math/rand"
	"mime/multipart"
	"net/http"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"com.onmeet.file/internal/client"
	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/repository"
	"github.com/google/uuid"
	"github.com/patrickmn/go-cache"
)

// allowedOwnerTypes는 S3 key 구성에 허용된 ownerType 값 목록입니다.
var allowedOwnerTypes = map[string]bool{
	"USER":    true,
	"TEAM":    true,
	"COMPANY": true,
	"MEETING": true,
}

// allowedMIMETypes는 업로드 허용 MIME 타입 목록입니다 (MIME 스푸핑 방지).
var allowedMIMETypes = map[string]bool{
	"image/jpeg":      true,
	"image/png":       true,
	"image/gif":       true,
	"image/webp":      true,
	"application/pdf": true,
	"text/plain":      true,
}

// numericOnlyRe는 ownerId가 숫자로만 구성되었는지 검사합니다.
var numericOnlyRe = regexp.MustCompile(`^\d+$`)

// FileService 인터페이스는 파일 서비스의 핵심 비즈니스 로직을 정의합니다.
type FileService interface {
	UploadFiles(ctx context.Context, files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error)
	GetFile(id uint) (*model.FileMetadata, error)
	DeleteFile(ctx context.Context, id uint, requesterId int64, cookie string) error
	DeleteMyProfile(ctx context.Context, uploaderId int64) error
	UploadFileAsync(ctx context.Context, file *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string)
	GenerateDefaultProfileImage(ctx context.Context, name string, color string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error)
	RenderFile(ctx context.Context, id uint) (io.ReadCloser, int64, string, error)
}

// cachedFile은 캐시에 저장되는 파일 데이터와 메타데이터를 담는 구조체입니다.
type cachedFile struct {
	Data        []byte
	ContentType string
}

type fileService struct {
	repo          repository.FileRepository
	s3            S3Service
	eventProducer EventProducer
	authClient    client.AuthClient
	cfg           *config.Config
	fileCache     *cache.Cache    // TTL 기반 캐시 (Key: fileId, Value: cachedFile)
	semaphore     chan struct{}    // 동시 고루틴 수 제한 (버퍼 채널)
}

// NewFileService는 서비스 구현체를 생성하며 의존성을 주입받습니다.
func NewFileService(repo repository.FileRepository, s3 S3Service, ep EventProducer, auth client.AuthClient, cfg *config.Config) FileService {
	return &fileService{
		repo:          repo,
		s3:            s3,
		eventProducer: ep,
		authClient:    auth,
		cfg:           cfg,
		fileCache:     cache.New(1*time.Hour, 10*time.Minute),
		semaphore:     make(chan struct{}, 10),
	}
}

// validateS3KeyComponents는 ownerType 허용 목록과 ownerId 숫자 검증, 경로 탐색 공격을 방어합니다.
func validateS3KeyComponents(ownerType, ownerId string) error {
	if ownerType != "" && !allowedOwnerTypes[ownerType] {
		return model.ErrInvalidOwnerField
	}
	if ownerId != "" && ownerId != "SYSTEM" {
		if strings.Contains(ownerId, "..") || strings.Contains(ownerId, "/") {
			return model.ErrInvalidOwnerField
		}
		if !numericOnlyRe.MatchString(ownerId) {
			return model.ErrInvalidOwnerField
		}
	}
	return nil
}

// UploadFiles는 여러 파일을 반복하며 업로드 프로세스를 실행합니다.
func (s *fileService) UploadFiles(ctx context.Context, files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error) {
	var results []*model.FileMetadata
	for _, fileHeader := range files {
		metadata, err := s.processFileUpload(ctx, fileHeader, category, uploaderId, ownerType, ownerId)
		if err != nil {
			return nil, err
		}
		results = append(results, metadata)
	}
	return results, nil
}

// processFileUpload는 개별 파일에 대해 S3 업로드와 DB 저장을 수행합니다.
func (s *fileService) processFileUpload(ctx context.Context, fileHeader *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	file, err := fileHeader.Open()
	if err != nil {
		return nil, err
	}
	defer file.Close()

	return s.processFileUploadFromReader(ctx, file, fileHeader.Filename, fileHeader.Size, fileHeader.Header.Get("Content-Type"), category, uploaderId, ownerType, ownerId)
}

// processFileUploadFromReader는 io.Reader로부터 파일 업로드를 처리합니다 (고루틴 안전).
func (s *fileService) processFileUploadFromReader(ctx context.Context, reader io.Reader, originalFilename string, fileSize int64, contentType, category string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	// 1. 기본값 처리
	if ownerType == "" {
		ownerType = "USER"
	}
	if ownerId == "" && uploaderId != nil {
		ownerId = fmt.Sprintf("%d", *uploaderId)
	} else if ownerId == "" {
		ownerId = "SYSTEM"
	}

	// 2. S3 key injection 방어: ownerType 허용 목록 + ownerId 숫자 검증 + 경로 탐색 차단
	if err := validateS3KeyComponents(ownerType, ownerId); err != nil {
		return nil, err
	}

	// 3. MIME 스푸핑 방지: 실제 파일 내용 기반 MIME 타입 검사
	sniff := make([]byte, 512)
	n, _ := io.ReadFull(reader, sniff)
	sniff = sniff[:n]
	detected := strings.SplitN(http.DetectContentType(sniff), ";", 2)[0]
	detected = strings.TrimSpace(detected)
	if !allowedMIMETypes[detected] {
		return nil, model.ErrInvalidMIMEType
	}
	// 읽은 바이트를 다시 앞에 붙여 원래 스트림 복원
	reader = io.MultiReader(bytes.NewReader(sniff), reader)

	ext := filepath.Ext(originalFilename)
	fileName := uuid.New().String() + ext
	savedKey := fmt.Sprintf("%s/%s/%s/%s", ownerType, ownerId, category, fileName)

	// 4. S3 업로드 실행
	err := s.s3.UploadFile(ctx, savedKey, reader, contentType)
	if err != nil {
		return nil, err
	}

	// 5. DB 저장을 위한 메타데이터 객체 생성
	metadata := &model.FileMetadata{
		FileName:         fileName,
		Category:         category,
		OriginalFileName: originalFilename,
		S3URL:            fmt.Sprintf("https://%s/%s", s.cfg.CloudFrontDomain, savedKey),
		FileSize:         fileSize,
		ContentType:      contentType,
		OwnerType:        ownerType,
		OwnerID:          ownerId,
		UploaderID:       uploaderId,
	}

	// 6. DB 저장 실행
	err = s.repo.Save(metadata)
	if err != nil {
		// S3 롤백 실패도 명시적으로 로깅
		if rollbackErr := s.s3.DeleteFile(ctx, savedKey); rollbackErr != nil {
			log.Printf("S3 rollback failed for key %s: %v", savedKey, rollbackErr)
		}
		return nil, err
	}

	return metadata, nil
}

func (s *fileService) GetFile(id uint) (*model.FileMetadata, error) {
	return s.repo.FindByID(id)
}

func (s *fileService) DeleteFile(ctx context.Context, id uint, requesterId int64, cookie string) error {
	metadata, err := s.repo.FindByID(id)
	if err != nil {
		return err
	}

	// 1. 권한 체크 (MANAGER 이상만 가능)
	requesterPerms, err := s.authClient.GetUserPermissions(requesterId, cookie)
	if err != nil {
		return err
	}

	hasRole := false
	for _, role := range requesterPerms.Roles {
		if role == "MANAGER" || role == "ADMIN" {
			hasRole = true
			break
		}
	}
	if !hasRole {
		return model.ErrPermissionDenied
	}

	if metadata.OwnerType == "COMPANY" {
		fileCompanyId, _ := strconv.ParseInt(metadata.OwnerID, 10, 64)
		if requesterPerms.CompanyID == nil || *requesterPerms.CompanyID != fileCompanyId {
			return model.ErrCrossCompanyDenied
		}
	} else if metadata.UploaderID != nil {
		uploaderPerms, err := s.authClient.GetUserPermissions(*metadata.UploaderID, cookie)
		if err != nil || uploaderPerms.CompanyID == nil || requesterPerms.CompanyID == nil ||
			*uploaderPerms.CompanyID != *requesterPerms.CompanyID {
			return model.ErrCrossCompanyDenied
		}
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", metadata.OwnerType, metadata.OwnerID, metadata.Category, metadata.FileName)

	err = s.s3.DeleteFile(ctx, savedKey)
	if err != nil {
		return err
	}

	s.fileCache.Delete(fmt.Sprintf("%d", id))

	return s.repo.Delete(id)
}

func (s *fileService) DeleteMyProfile(ctx context.Context, uploaderId int64) error {
	files, err := s.repo.FindByUploaderAndCategory(uploaderId, "profile")
	if err != nil {
		return err
	}

	var errs []string
	for _, metadata := range files {
		savedKey := fmt.Sprintf("%s/%s/%s/%s", metadata.OwnerType, metadata.OwnerID, metadata.Category, metadata.FileName)
		if err := s.s3.DeleteFile(ctx, savedKey); err != nil {
			errs = append(errs, fmt.Sprintf("S3 delete failed for %s: %v", savedKey, err))
			continue
		}
		s.fileCache.Delete(fmt.Sprintf("%d", metadata.ID))
		if err := s.repo.Delete(metadata.ID); err != nil {
			errs = append(errs, fmt.Sprintf("DB delete failed for ID %d: %v", metadata.ID, err))
		}
	}
	if len(errs) > 0 {
		return fmt.Errorf("partial delete errors: %s", strings.Join(errs, "; "))
	}
	return nil
}

// UploadFileAsync는 고루틴(Goroutine)을 사용하여 비동기적으로 파일을 처리합니다.
// 세마포어(semaphore)로 동시 고루틴 수를 최대 10개로 제한합니다.
func (s *fileService) UploadFileAsync(ctx context.Context, fileHeader *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string) {
	// 고루틴 바깥에서 파일을 읽어 복사본 생성 (고루틴 안전성 확보)
	file, err := fileHeader.Open()
	if err != nil {
		log.Printf("Failed to open file for async upload: %v", err)
		return
	}

	var buf bytes.Buffer
	if _, err := io.Copy(&buf, file); err != nil {
		file.Close()
		log.Printf("Failed to copy file content: %v", err)
		return
	}
	file.Close()

	filename := fileHeader.Filename
	size := fileHeader.Size
	contentType := fileHeader.Header.Get("Content-Type")

	// 세마포어 획득 후 고루틴 실행 (동시 고루틴 수 제한)
	s.semaphore <- struct{}{}
	go func() {
		defer func() { <-s.semaphore }()

		// 고루틴은 요청 컨텍스트가 아닌 Background 컨텍스트를 사용 (비동기 작업)
		bgCtx := context.Background()
		metadata, err := s.processFileUploadFromReader(bgCtx, &buf, filename, size, contentType, category, uploaderId, ownerType, ownerId)
		if err != nil {
			log.Printf("Async upload failed: %v", err)
			return
		}

		if callbackTopic == "" {
			callbackTopic = "file-upload-events"
		}

		var uId int64
		if uploaderId != nil {
			uId = *uploaderId
		}

		_ = s.eventProducer.SendFileUploadEvent(bgCtx, callbackTopic, metadata.ID, metadata.FileName, metadata.S3URL, uId, correlationId)
	}()
}

func (s *fileService) GenerateDefaultProfileImage(ctx context.Context, name string, color string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	var textColor string
	if color == "" {
		type ColorPair struct {
			Background string
			Text       string
		}

		palette := []ColorPair{
			{"#E1BEE7", "#6A1B9A"},
			{"#D1C4E9", "#4527A0"},
			{"#C5CAE9", "#283593"},
			{"#BBDEFB", "#1565C0"},
			{"#B3E5FC", "#0277BD"},
			{"#B2DFDB", "#00695C"},
			{"#C8E6C9", "#2E7D32"},
			{"#DCEDC8", "#558B2F"},
			{"#FFF9C4", "#F9A825"},
			{"#FFECB3", "#FF6F00"},
			{"#FFE0B2", "#EF6C00"},
			{"#FFCCBC", "#D84315"},
			{"#D7CCC8", "#4E342E"},
			{"#F5F5F5", "#424242"},
			{"#CFD8DC", "#37474F"},
		}

		idx := rand.Intn(len(palette))
		color = palette[idx].Background
		textColor = palette[idx].Text
	} else {
		textColor = "white"
	}

	initial := ""
	if len(name) > 0 {
		runes := []rune(name)
		initial = string(runes[0])
	}

	svgContent := fmt.Sprintf(`<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg"><rect width="100%%" height="100%%" fill="%s"/><text x="50%%" y="50%%" font-size="100" text-anchor="middle" dy=".3em" fill="%s" font-family="Arial, sans-serif">%s</text></svg>`, color, textColor, initial)

	fileName := uuid.New().String() + ".svg"
	category := "profile"

	// 기본값 처리
	if ownerType == "" {
		ownerType = "USER"
	}
	if ownerId == "" && uploaderId != nil {
		ownerId = fmt.Sprintf("%d", *uploaderId)
	}

	// S3 key injection 방어
	if err := validateS3KeyComponents(ownerType, ownerId); err != nil {
		return nil, err
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", ownerType, ownerId, category, fileName)

	reader := strings.NewReader(svgContent)
	err := s.s3.UploadFile(ctx, savedKey, reader, "image/svg+xml")
	if err != nil {
		return nil, err
	}

	metadata := &model.FileMetadata{
		FileName:         fileName,
		Category:         category,
		OriginalFileName: "default_profile.svg",
		S3URL:            fmt.Sprintf("https://%s/%s", s.cfg.CloudFrontDomain, savedKey),
		FileSize:         int64(len(svgContent)),
		ContentType:      "image/svg+xml",
		OwnerType:        ownerType,
		OwnerID:          ownerId,
		UploaderID:       uploaderId,
	}

	err = s.repo.Save(metadata)
	if err != nil {
		if rollbackErr := s.s3.DeleteFile(ctx, savedKey); rollbackErr != nil {
			log.Printf("S3 rollback failed for key %s: %v", savedKey, rollbackErr)
		}
		return nil, err
	}

	return metadata, nil
}

// RenderFile은 파일을 스트리밍으로 반환합니다.
// 1MB 미만 파일은 인메모리 캐시를 사용하고, 이상은 S3에서 직접 스트리밍합니다.
func (s *fileService) RenderFile(ctx context.Context, id uint) (io.ReadCloser, int64, string, error) {
	// 1. 캐시 조회
	if val, found := s.fileCache.Get(fmt.Sprintf("%d", id)); found {
		cached := val.(cachedFile)
		return io.NopCloser(bytes.NewReader(cached.Data)), int64(len(cached.Data)), cached.ContentType, nil
	}

	// 2. 캐시 미스 -> DB 조회
	metadata, err := s.repo.FindByID(id)
	if err != nil {
		return nil, 0, "", err
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", metadata.OwnerType, metadata.OwnerID, metadata.Category, metadata.FileName)

	// 3. S3 다운로드
	s3Reader, contentType, err := s.s3.GetFile(ctx, savedKey)
	if err != nil {
		return nil, 0, "", err
	}

	// 4. 1MB 이상은 직접 스트리밍 (메모리 절약)
	const streamThreshold = 1024 * 1024
	if metadata.FileSize >= streamThreshold {
		return s3Reader, metadata.FileSize, contentType, nil
	}

	// 5. 1MB 미만: 전체 읽기 후 캐싱
	defer s3Reader.Close()
	content, err := io.ReadAll(s3Reader)
	if err != nil {
		return nil, 0, "", err
	}

	s.fileCache.Set(fmt.Sprintf("%d", id), cachedFile{
		Data:        content,
		ContentType: contentType,
	}, cache.DefaultExpiration)

	return io.NopCloser(bytes.NewReader(content)), int64(len(content)), contentType, nil
}
