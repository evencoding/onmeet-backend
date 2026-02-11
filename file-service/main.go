package main

import (
	"log"

	"com.onmeet.file/internal/client"
	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/handler"
	"com.onmeet.file/internal/middleware"
	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/repository"
	"com.onmeet.file/internal/service"
	"github.com/gin-gonic/gin"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

// main 함수는 프로그램의 시작점입니다. Java의 public static void main과 같습니다.
func main() {
	// 1. 설정 로드
	cfg := config.LoadConfig()

	// 2. 데이터베이스 연결 (GORM 이용)
	// postgres.Open은 연결 정보를 담은 드라이버를 생성합니다.
	db, err := gorm.Open(postgres.Open(cfg.PostgresURL), &gorm.Config{})
	if err != nil {
		// log.Fatal은 메시지를 출력하고 프로그램을 즉시 종료합니다.
		log.Fatal("Failed to connect to database:", err)
	}

	// 3. 테이블 자동 생성 (마이그레이션)
	// JPA의 hibernate.ddl-auto: update와 유사한 기능입니다.
	_ = db.AutoMigrate(&model.FileMetadata{})

	// 4. 의존성 주입 (Dependency Injection)
	// Go는 Spring처럼 자동으로 빈을 관리하지 않기 때문에, 아래처럼 직접 생성해서 연결해줍니다.
	repo := repository.NewPostgresFileRepository(db)
	s3Svc, err := service.NewS3Service(cfg)
	if err != nil {
		log.Fatal("Failed to setup S3 service:", err)
	}

	auth := client.NewAuthClient(cfg)
	ep := service.NewKafkaEventProducer(cfg)
	svc := service.NewFileService(repo, s3Svc, ep, auth, cfg)
	h := handler.NewFileHandler(svc)

	// 5. 웹 서버(Router) 설정
	// gin.Default()는 로깅과 패닉 복구 미들웨어가 포함된 기본 엔진을 생성합니다.
	r := gin.Default()

	// 6. 전역 미들웨어 설정
	r.Use(middleware.SecurityMiddleware(cfg))

	// 7. 라우팅 설정 (API 엔드포인트)
	// Spring의 @RequestMapping("/file") 처럼 그룹화가 가능합니다.
	fileGroup := r.Group("/file")
	{
		fileGroup.POST("/upload", h.Upload)
		fileGroup.POST("/upload-async", h.UploadAsync)
		fileGroup.POST("/profile/default", h.GenerateProfileImage)
		fileGroup.GET("/render/:fileId", h.RenderFile)
		fileGroup.GET("/:fileId", h.GetFileInfo)
		fileGroup.DELETE("/:fileId", h.DeleteFile)
	}

	// 상태 체크용 API (Kubernetes 등에서 사용)
	r.GET("/file/actuator/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "UP"})
	})

	// 8. 서버 실행
	log.Printf("File Service (PostgreSQL) starting on port %s...", cfg.Port)
	if err := r.Run(":" + cfg.Port); err != nil {
		log.Fatal("Failed to run server:", err)
	}
}
