package service

import (
	"fmt"
	"log"
	"mime/multipart"
	"path/filepath"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/repository"
	"github.com/google/uuid"
)

type FileService interface {
	UploadFiles(files []*multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) ([]*model.FileMetadata, error)
	GetFile(id uint) (*model.FileMetadata, error)
	DeleteFile(id uint, requesterId int64) error
	UploadFileAsync(file *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId, callbackTopic, correlationId string)
}

type fileService struct {
	repo          repository.FileRepository
	s3            S3Service
	eventProducer EventProducer
	cfg           *config.Config
}

func NewFileService(repo repository.FileRepository, s3 S3Service, ep EventProducer, cfg *config.Config) FileService {
	return &fileService{
		repo:          repo,
		s3:            s3,
		eventProducer: ep,
		cfg:           cfg,
	}
}

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

func (s *fileService) processFileUpload(fileHeader *multipart.FileHeader, category string, uploaderId *int64, ownerType, ownerId string) (*model.FileMetadata, error) {
	file, err := fileHeader.Open()
	if err != nil {
		return nil, err
	}
	defer file.Close()

	ext := filepath.Ext(fileHeader.Filename)
	fileName := uuid.New().String() + ext
	if ownerType == "" {
		ownerType = "USER"
	}
	if ownerId == "" && uploaderId != nil {
		ownerId = fmt.Sprintf("%d", *uploaderId)
	} else if ownerId == "" {
		ownerId = "SYSTEM"
	}

	savedKey := fmt.Sprintf("%s/%s/%s/%s", ownerType, ownerId, category, fileName)

	err = s.s3.UploadFile(savedKey, file, fileHeader.Header.Get("Content-Type"))
	if err != nil {
		return nil, err
	}

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

	err = s.repo.Save(metadata)
	if err != nil {
		// Rollback S3
		_ = s.s3.DeleteFile(savedKey)
		return nil, err
	}

	return metadata, nil
}

func (s *fileService) GetFile(id uint) (*model.FileMetadata, error) {
	return s.repo.FindByID(id)
}

func (s *fileService) DeleteFile(id uint, requesterId int64) error {
	metadata, err := s.repo.FindByID(id)
	if err != nil {
		return err
	}

	// Permission check (Simplified for migration example)
	// In production, you'd call auth-service or check roles from context.
	// For now, only owner or uploader can delete.
	if metadata.UploaderID != nil && *metadata.UploaderID != requesterId {
		// return fmt.Errorf("permission denied") // Add custom error
	}

	err = s.s3.DeleteFile(metadata.FileName)
	if err != nil {
		return err
	}

	return s.repo.Delete(id)
}

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
