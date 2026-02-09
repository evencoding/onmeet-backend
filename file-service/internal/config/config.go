package config

import (
	"os"
)

type Config struct {
	Port                string
	PostgresURL         string
	GatewaySharedSecret string
	AWSRegion           string
	S3BucketName        string
	CloudFrontDomain    string
	KafkaBrokers        string
	AuthServiceURL      string
}

func LoadConfig() *Config {
	return &Config{
		Port:                getEnv("SERVER_PORT", "8086"),
		PostgresURL:         getEnv("DB_URL", "host=postgres-file user=postgres password=root dbname=file_db port=5432 sslmode=disable"),
		GatewaySharedSecret: getEnv("GATEWAY_SHARED_SECRET", ""),
		AWSRegion:           getEnv("AWS_REGION", "ap-northeast-2"),
		S3BucketName:        getEnv("S3_BUCKET_NAME", ""),
		CloudFrontDomain:    getEnv("CLOUDFRONT_DOMAIN", ""),
		KafkaBrokers:        getEnv("KAFKA_BROKERS", "kafka:9092"),
		AuthServiceURL:      getEnv("AUTH_SERVICE_URL", "http://auth-service:8081"),
	}
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
