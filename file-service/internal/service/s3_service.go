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

type S3Service interface {
	UploadFile(key string, content io.Reader, contentType string) error
	DeleteFile(key string) error
}

type s3Service struct {
	client *s3.Client
	bucket string
}

func NewS3Service(cfg *config.Config) (S3Service, error) {
	sdkConfig, err := s3config.LoadDefaultConfig(context.TODO(), s3config.WithRegion(cfg.AWSRegion))
	if err != nil {
		return nil, fmt.Errorf("unable to load SDK config, %v", err)
	}

	client := s3.NewFromConfig(sdkConfig)
	return &s3Service{
		client: client,
		bucket: cfg.S3BucketName,
	}, nil
}

func (s *s3Service) UploadFile(key string, content io.Reader, contentType string) error {
	_, err := s.client.PutObject(context.TODO(), &s3.PutObjectInput{
		Bucket:      aws.String(s.bucket),
		Key:         aws.String(key),
		Body:        content,
		ContentType: aws.String(contentType),
	})
	return err
}

func (s *s3Service) DeleteFile(key string) error {
	_, err := s.client.DeleteObject(context.TODO(), &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	return err
}
