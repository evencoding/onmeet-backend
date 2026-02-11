package service

import (
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	"com.onmeet.file/internal/client"
	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/repository"
	"github.com/google/uuid"
)

// FileService 인터페이스는 파일 서비스의 핵심 비즈니스 로직을 정의합니다.
// Spring의 @Service 인터페이스와 같습니다.
type FileService interface {
	UploadFiles(files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error)
	GetFile(id uint) (*model.FileMetadata, error)
	DeleteFile(id uint, requesterId int64, cookie string) error
	// UploadFileAsync는 비동기 업로드를 지원합니다.
	UploadFileAsync(file *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string)
	GenerateDefaultProfileImage(name string, color string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error)
	RenderFile(id uint) ([]byte, string, error)
}

type fileService struct {
	repo          repository.FileRepository
	s3            S3Service
	eventProducer EventProducer
	authClient    client.AuthClient
	cfg           *config.Config
	fileCache     sync.Map // 간단한 인메모리 캐시 (Key: fileId, Value: []byte)
}

// NewFileService는 서비스 구현체를 생성하며 의존성(Repo, S3, Kafka, AuthClient)을 주입받습니다.
// Spring의 생성자 주입(Constructor Injection)을 손수 구현한 모습입니다.
func NewFileService(repo repository.FileRepository, s3 S3Service, ep EventProducer, auth client.AuthClient, cfg *config.Config) FileService {
	return &fileService{
		repo:          repo,
		s3:            s3,
		eventProducer: ep,
		authClient:    auth,
		cfg:           cfg,
		fileCache:     sync.Map{},
	}
}

// UploadFiles는 여러 파일을 반복하며 업로드 프로세스를 실행합니다.
func (s *fileService) UploadFiles(files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error) {
	var results []*model.FileMetadata
	for _, fileHeader := range files {
		metadata, err := s.processFileUpload(fileHeader, category, uploaderId, ownerType, ownerId)
		if err != nil {
			return nil, err
		}
		results = append(results, metadata)
	}
	return results, nil
}

// processFileUpload는 개별 파일에 대해 S3 업로드와 DB 저장을 수행합니다.
func (s *fileService) processFileUpload(fileHeader *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	file, err := fileHeader.Open()
	if err != nil {
		return nil, err
	}
	defer file.Close()

	ext := filepath.Ext(fileHeader.Filename)
	fileName := uuid.New().String() + ext

	// 기본값 처리 로직
	if ownerType == "" {
		ownerType = "USER"
	}
	if ownerId == "" && uploaderId != nil {
		ownerId = fmt.Sprintf("%d", *uploaderId)
	} else if ownerId == "" {
		ownerId = "SYSTEM"
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", ownerType, ownerId, category, fileName)

	// 1. S3 업로드 실행
	err = s.s3.UploadFile(savedKey, file, fileHeader.Header.Get("Content-Type"))
	if err != nil {
		return nil, err
	}

	// 2. DB 저장을 위한 메타데이터 객체 생성
	metadata := &model.FileMetadata{
		FileName:         fileName,
		Category:         category,
		OriginalFileName: fileHeader.Filename,
		S3URL:            fmt.Sprintf("https://%s/%s", s.cfg.CloudFrontDomain, savedKey),
		FileSize:         fileHeader.Size,
		ContentType:      fileHeader.Header.Get("Content-Type"),
		OwnerType:        ownerType,
		OwnerID:          ownerId,
		UploaderID:       uploaderId,
	}

	// 3. DB 저장 실행
	err = s.repo.Save(metadata)
	if err != nil {
		_ = s.s3.DeleteFile(savedKey)
		return nil, err
	}

	return metadata, nil
}

func (s *fileService) GetFile(id uint) (*model.FileMetadata, error) {
	return s.repo.FindByID(id)
}

func (s *fileService) DeleteFile(id uint, requesterId int64, cookie string) error {
	metadata, err := s.repo.FindByID(id)
	if err != nil {
		return err
	}

	// 1. 권한 체크 로직
	// 기본적으로 본인이 업로드한 파일은 삭제 가능합니다.
	canDelete := metadata.UploaderID != nil && *metadata.UploaderID == requesterId

	// 2. 관리자(MANAGER) 권한 체크
	// 본인이 아니더라도 같은 회사의 관리자라면 삭제가 가능해야 합니다.
	if !canDelete {
		requesterPerms, err := s.authClient.GetUserPermissions(requesterId, cookie)
		if err == nil {
			isManager := false
			for _, role := range requesterPerms.Roles {
				if role == "MANAGER" || role == "ADMIN" {
					isManager = true
					break
				}
			}

			if isManager {
				// 파일의 소유주가 회사인 경우: 해당 회사의 ID와 관리자의 회사 ID가 같은지 확인
				if metadata.OwnerType == "COMPANY" {
					fileCompanyId, _ := strconv.ParseInt(metadata.OwnerID, 10, 64)
					if requesterPerms.CompanyID != nil && *requesterPerms.CompanyID == fileCompanyId {
						canDelete = true
					}
				} else if metadata.UploaderID != nil {
					// 파일의 소유주가 유저인 경우: 업로더가 관리자와 같은 회사 소속인지 확인
					uploaderPerms, err := s.authClient.GetUserPermissions(*metadata.UploaderID, cookie)
					if err == nil && uploaderPerms.CompanyID != nil && requesterPerms.CompanyID != nil &&
						*uploaderPerms.CompanyID == *requesterPerms.CompanyID {
						canDelete = true
					}
				}
			}
		}
	}

	if !canDelete {
		return fmt.Errorf("permission denied: you do not have permission to delete this file")
	}

	// S3 Key 추출 (S3URL: https://domain/ownerType/ownerId/category/uuid.ext)
	savedKey := fmt.Sprintf("%s/%s/%s/%s", metadata.OwnerType, metadata.OwnerID, metadata.Category, metadata.FileName)

	err = s.s3.DeleteFile(savedKey)
	if err != nil {
		return err
	}

	// 캐시 삭제 (데이터 불일치 방지)
	s.fileCache.Delete(id)

	// DB 메타데이터 삭제
	return s.repo.Delete(id)
}

// UploadFileAsync는 고루틴(Goroutine)을 사용하여 비동기적으로 파일을 처리합니다.
func (s *fileService) UploadFileAsync(fileHeader *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string) {
	go func() {
		metadata, err := s.processFileUpload(fileHeader, category, uploaderId, ownerType, ownerId)
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

		_ = s.eventProducer.SendFileUploadEvent(callbackTopic, metadata.ID, metadata.FileName, metadata.S3URL, uId, correlationId)
	}()
}

func (s *fileService) GenerateDefaultProfileImage(name string, color string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	if color == "" {
		// 랜덤 파스텔 컬러 (단순 예시)
		color = "#6c5ce7"
	}

	initial := ""
	if len(name) > 0 {
		runes := []rune(name)
		initial = string(runes[0])
	}

	svgContent := fmt.Sprintf(`<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg"><rect width="100%%" height="100%%" fill="%s"/><text x="50%%" y="50%%" font-size="100" text-anchor="middle" dy=".3em" fill="white" font-family="Arial, sans-serif">%s</text></svg>`, color, initial)

	fileName := uuid.New().String() + ".svg"
	category := "profile"

	// 기본값 처리
	if ownerType == "" {
		ownerType = "USER"
	}
	if ownerId == "" && uploaderId != nil {
		ownerId = fmt.Sprintf("%d", *uploaderId)
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", ownerType, ownerId, category, fileName)

	// S3 업로드 (String Reader -> io.Reader)
	reader := strings.NewReader(svgContent)
	err := s.s3.UploadFile(savedKey, reader, "image/svg+xml")
	if err != nil {
		return nil, err
	}

	// DB 메타데이터 저장
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
		_ = s.s3.DeleteFile(savedKey)
		return nil, err
	}

	return metadata, nil
}

func (s *fileService) RenderFile(id uint) ([]byte, string, error) {
	// 1. 캐시 조회
	if val, ok := s.fileCache.Load(id); ok {
		// 캐시된 데이터 반환시, Content-Type도 어딘가 저장해야 하지만,
		// 일단 DB조회는 빠르므로 메타데이터는 DB에서 가져오고 내용은 캐시에서 가져오는 전략
		// 혹은, 캐시에 구조체를 저장. 간단히 []byte만 저장하고 메타데이터는 DB 조회.
		// 성능 최적화를 위해 메타데이터도 캐싱하면 좋지만, Content-Type 확인을 위해 DB 조회는 감수.
		metadata, err := s.repo.FindByID(id)
		if err != nil {
			return nil, "", err
		}
		return val.([]byte), metadata.ContentType, nil
	}

	// 2. 캐시 미스 -> DB 조회
	metadata, err := s.repo.FindByID(id)
	if err != nil {
		return nil, "", err
	}

	// S3 Key 재구성
	savedKey := fmt.Sprintf("%s/%s/%s/%s", metadata.OwnerType, metadata.OwnerID, metadata.Category, metadata.FileName)

	// 3. S3 다운로드
	reader, contentType, err := s.s3.GetFile(savedKey)
	if err != nil {
		return nil, "", err
	}
	defer reader.Close()

	content, err := io.ReadAll(reader)
	if err != nil {
		return nil, "", err
	}

	// 4. 캐시 저장 (메모리 관리 주의: 프로필 이미지는 보통 작지만, 큰 파일 캐싱은 위험할 수 있음)
	// SVG나 작은 이미지만 캐싱하도록 제한을 걸 수도 있음 (예: 1MB 이하)
	if len(content) < 1024*1024 { // 1MB 미만만 캐싱
		s.fileCache.Store(id, content)
	}

	return content, contentType, nil
}
