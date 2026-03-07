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
// MinIO 사용 시 S3Endpoint를 설정하면 커스텀 엔드포인트와 경로 스타일을 사용합니다.
func NewS3Service(cfg *config.Config) (S3Service, error) {
	var sdkConfig aws.Config
	var err error

	// S3Endpoint가 설정되어 있으면 MinIO 등의 S3 호환 스토리지를 사용합니다.
	if cfg.S3Endpoint != "" {
		// 커스텀 엔드포인트 리졸버를 사용하여 MinIO 엔드포인트로 요청을 라우팅합니다.
		customResolver := aws.EndpointResolverWithOptionsFunc(func(service, region string, options ...interface{}) (aws.Endpoint, error) {
			return aws.Endpoint{
				URL:               cfg.S3Endpoint,
				HostnameImmutable: true,
				SigningRegion:     cfg.AWSRegion,
			}, nil
		})

		sdkConfig, err = s3config.LoadDefaultConfig(context.TODO(),
			s3config.WithRegion(cfg.AWSRegion),
			s3config.WithEndpointResolverWithOptions(customResolver),
		)
		if err != nil {
			return nil, fmt.Errorf("unable to load SDK config for MinIO, %v", err)
		}

		// MinIO는 경로 스타일(Path-Style) URL을 사용하므로 UsePathStyle을 true로 설정합니다.
		// 예: http://minio:9000/bucket/key (경로 스타일) vs http://bucket.s3.amazonaws.com/key (가상 호스트 스타일)
		client := s3.NewFromConfig(sdkConfig, func(o *s3.Options) {
			o.UsePathStyle = true
		})

		return &s3Service{
			client: client,
			bucket: cfg.S3BucketName,
		}, nil
	}

	// S3Endpoint가 없으면 기존 AWS S3를 사용합니다 (역호환성 유지).
	sdkConfig, err = s3config.LoadDefaultConfig(context.TODO(), s3config.WithRegion(cfg.AWSRegion))
	if err != nil {
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
