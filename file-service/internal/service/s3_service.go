package service

import (
	"context"
	"fmt"
	"io"

	"com.onmeet.file/internal/config"
	"github.com/aws/aws-sdk-go-v2/aws"
	s3config "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

// S3Service는 AWS S3와의 통신을 담당하는 인터페이스입니다.
type S3Service interface {
	UploadFile(key string, content io.Reader, contentType string) error
	DeleteFile(key string) error
	GetFile(key string) (io.ReadCloser, string, error)
}

// s3Service 구조체는 인터페이스의 실질적인 구현체입니다.
type s3Service struct {
	client *s3.Client
	bucket string
}

// NewS3Service는 S3 클라이언트를 초기화하여 서비스 인스턴스를 반환합니다.
// Go에서는 리턴값이 여러 개일 수 있어 (결과값, 에러)를 함께 반환하는 것이 표준입니다.
func NewS3Service(cfg *config.Config) (S3Service, error) {
	// context.TODO()는 비어있는 컨텍스트를 의미하며, 나중에 실제 컨텍스트로 바꿀 수 있는 자리 표시자입니다.
	sdkConfig, err := s3config.LoadDefaultConfig(context.TODO(), s3config.WithRegion(cfg.AWSRegion))
	if err != nil {
		// fmt.Errorf는 에러 메시지를 포맷팅하여 새로운 에러 객체를 만듭니다.
		return nil, fmt.Errorf("unable to load SDK config, %v", err)
	}

	client := s3.NewFromConfig(sdkConfig)
	return &s3Service{
		client: client,
		bucket: cfg.S3BucketName,
	}, nil
}

// UploadFile은 S3 버킷에 파일을 업로드합니다.
// io.Reader는 Java의 InputStream과 비슷한 개념으로, 데이터의 흐름을 나타냅니다.
func (s *s3Service) UploadFile(key string, content io.Reader, contentType string) error {
	// PutObjectInput 구조체에 필요한 정보들을 담아 AWS SDK 함수를 호출합니다.
	// aws.String() 함수는 일반 string을 포인터(*string)로 변환해줍니다. (AWS SDK의 규칙)
	_, err := s.client.PutObject(context.TODO(), &s3.PutObjectInput{
		Bucket:      aws.String(s.bucket),
		Key:         aws.String(key),
		Body:        content,
		ContentType: aws.String(contentType),
	})
	return err
}

// DeleteFile은 S3 버킷에서 파일을 삭제합니다.
func (s *s3Service) DeleteFile(key string) error {
	_, err := s.client.DeleteObject(context.TODO(), &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	return err
}

func (s *s3Service) GetFile(key string) (io.ReadCloser, string, error) {
	resp, err := s.client.GetObject(context.TODO(), &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return nil, "", err
	}

	contentType := "application/octet-stream"
	if resp.ContentType != nil {
		contentType = *resp.ContentType
	}

	return resp.Body, contentType, nil
}
