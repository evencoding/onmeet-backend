package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestLoadConfig_DefaultValues(t *testing.T) {
	// Ensure no env vars that would override defaults are set
	keysToUnset := []string{
		"SERVER_PORT", "DB_URL", "GATEWAY_SHARED_SECRET", "AWS_REGION",
		"S3_BUCKET_NAME", "S3_ENDPOINT", "CLOUDFRONT_DOMAIN", "KAFKA_BROKERS",
		"AUTH_SERVICE_URL", "CORS_ALLOWED_ORIGINS",
	}
	for _, k := range keysToUnset {
		os.Unsetenv(k)
	}

	cfg := LoadConfig()

	assert.Equal(t, "8086", cfg.Port)
	assert.Contains(t, cfg.PostgresURL, "host=postgres-file")
	assert.Equal(t, "", cfg.GatewaySharedSecret)
	assert.Equal(t, "ap-northeast-2", cfg.AWSRegion)
	assert.Equal(t, "", cfg.S3BucketName)
	assert.Equal(t, "", cfg.S3Endpoint)
	assert.Equal(t, "", cfg.CloudFrontDomain)
	assert.Equal(t, "kafka:9092", cfg.KafkaBrokers)
	assert.Equal(t, "http://auth-service:8081", cfg.AuthServiceURL)
	assert.Equal(t, "http://localhost:8080", cfg.CORSAllowedOrigins)
}

func TestLoadConfig_CustomValues(t *testing.T) {
	os.Setenv("SERVER_PORT", "9090")
	os.Setenv("GATEWAY_SHARED_SECRET", "my-secret")
	os.Setenv("AWS_REGION", "us-east-1")
	os.Setenv("S3_BUCKET_NAME", "my-bucket")
	os.Setenv("S3_ENDPOINT", "http://minio:9000")
	os.Setenv("CLOUDFRONT_DOMAIN", "cdn.example.com")
	os.Setenv("KAFKA_BROKERS", "kafka1:9092,kafka2:9092")
	os.Setenv("AUTH_SERVICE_URL", "http://auth:8081")
	os.Setenv("CORS_ALLOWED_ORIGINS", "https://app.example.com")

	defer func() {
		os.Unsetenv("SERVER_PORT")
		os.Unsetenv("GATEWAY_SHARED_SECRET")
		os.Unsetenv("AWS_REGION")
		os.Unsetenv("S3_BUCKET_NAME")
		os.Unsetenv("S3_ENDPOINT")
		os.Unsetenv("CLOUDFRONT_DOMAIN")
		os.Unsetenv("KAFKA_BROKERS")
		os.Unsetenv("AUTH_SERVICE_URL")
		os.Unsetenv("CORS_ALLOWED_ORIGINS")
	}()

	cfg := LoadConfig()

	assert.Equal(t, "9090", cfg.Port)
	assert.Equal(t, "my-secret", cfg.GatewaySharedSecret)
	assert.Equal(t, "us-east-1", cfg.AWSRegion)
	assert.Equal(t, "my-bucket", cfg.S3BucketName)
	assert.Equal(t, "http://minio:9000", cfg.S3Endpoint)
	assert.Equal(t, "cdn.example.com", cfg.CloudFrontDomain)
	assert.Equal(t, "kafka1:9092,kafka2:9092", cfg.KafkaBrokers)
	assert.Equal(t, "http://auth:8081", cfg.AuthServiceURL)
	assert.Equal(t, "https://app.example.com", cfg.CORSAllowedOrigins)
}

func TestLoadConfig_PartialOverride(t *testing.T) {
	// Only override specific fields; others should remain as defaults
	os.Setenv("CLOUDFRONT_DOMAIN", "cdn.partial.com")
	os.Unsetenv("SERVER_PORT")

	defer os.Unsetenv("CLOUDFRONT_DOMAIN")

	cfg := LoadConfig()

	assert.Equal(t, "8086", cfg.Port, "non-overridden field should use default")
	assert.Equal(t, "cdn.partial.com", cfg.CloudFrontDomain)
}

func TestLoadConfig_EmptyStringEnvVar(t *testing.T) {
	// Explicit empty string env var should override the default
	os.Setenv("S3_BUCKET_NAME", "")
	defer os.Unsetenv("S3_BUCKET_NAME")

	cfg := LoadConfig()

	assert.Equal(t, "", cfg.S3BucketName)
}

func TestLoadConfig_ReturnsPointer(t *testing.T) {
	cfg := LoadConfig()
	assert.NotNil(t, cfg, "LoadConfig must return a non-nil pointer")
}

func TestGetEnv_FallbackWhenMissing(t *testing.T) {
	os.Unsetenv("TEST_KEY_NOT_SET")
	result := getEnv("TEST_KEY_NOT_SET", "fallback-value")
	assert.Equal(t, "fallback-value", result)
}

func TestGetEnv_UsesEnvWhenPresent(t *testing.T) {
	os.Setenv("TEST_KEY_PRESENT", "env-value")
	defer os.Unsetenv("TEST_KEY_PRESENT")

	result := getEnv("TEST_KEY_PRESENT", "fallback-value")
	assert.Equal(t, "env-value", result)
}

func TestGetEnv_EmptyEnvPreferredOverFallback(t *testing.T) {
	// An empty env var that IS set should return "" rather than the fallback
	os.Setenv("TEST_KEY_EMPTY", "")
	defer os.Unsetenv("TEST_KEY_EMPTY")

	result := getEnv("TEST_KEY_EMPTY", "fallback")
	assert.Equal(t, "", result)
}
