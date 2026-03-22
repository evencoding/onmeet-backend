package config

import (
	"os"
)

// Config 구조체는 애플리케이션의 설정 정보들을 담습니다.
// Java의 @ConfigurationProperties나 application.yml 설정을 담는 VO 클래스와 비슷합니다.
// 필드명이 대문자로 시작하는 것은 외부 패키지에서 접근 가능한 'public'임을 의미합니다.
type Config struct {
	Port                string
	PostgresURL         string
	GatewaySharedSecret string
	AWSRegion           string
	S3BucketName        string
	S3Endpoint          string
	CloudFrontDomain    string
	KafkaBrokers        string
	AuthServiceURL      string
	CORSAllowedOrigins  string
}

// LoadConfig는 환경 변수에서 설정을 읽어와 Config 객체를 생성합니다.
// Spring Boot가 실행 시 yml을 읽어 Bean을 만드는 과정과 유사한 '수동' 설정 로딩입니다.
// 리턴 타입 앞의 *는 포인터(참조값)를 반환한다는 뜻입니다.
func LoadConfig() *Config {
	return &Config{
		Port:                getEnv("SERVER_PORT", "8086"),
		PostgresURL:         getEnv("DB_URL", "host=postgres-file user=postgres password=root dbname=file_db port=5432 sslmode=disable"),
		GatewaySharedSecret: getEnv("GATEWAY_SHARED_SECRET", ""),
		AWSRegion:           getEnv("AWS_REGION", "ap-northeast-2"),
		S3BucketName:        getEnv("S3_BUCKET_NAME", ""),
		S3Endpoint:          getEnv("S3_ENDPOINT", ""),
		CloudFrontDomain:    getEnv("CLOUDFRONT_DOMAIN", ""),
		KafkaBrokers:        getEnv("KAFKA_BROKERS", "kafka:9092"),
		AuthServiceURL:      getEnv("AUTH_SERVICE_URL", "http://auth-service:8081"),
		CORSAllowedOrigins:  getEnv("CORS_ALLOWED_ORIGINS", "http://localhost:8080"),
	}
}

// getEnv는 환경 변수 값을 읽어오되, 없으면 기본값(fallback)을 사용합니다.
// func 키워드 뒤에 함수명이 오고, 파라미터 타입은 변수명 뒤에 적습니다.
func getEnv(key, fallback string) string {
	// os.LookupEnv는 값과 함께 존재 여부(ok)를 반환합니다.
	// Go에서는 이렇게 여러 개의 리턴값을 받는 패턴이 매우 흔합니다.
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
