package main

import (
	"log"

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

func main() {
	cfg := config.LoadConfig()

	// DB Setup
	db, err := gorm.Open(postgres.Open(cfg.PostgresURL), &gorm.Config{})
	if err != nil {
		log.Fatal("Failed to connect to database:", err)
	}

	// Auto Migrate
	_ = db.AutoMigrate(&model.FileMetadata{})

	repo := repository.NewPostgresFileRepository(db)
	s3Svc, err := service.NewS3Service(cfg)
	if err != nil {
		log.Fatal("Failed to setup S3 service:", err)
	}

	ep := service.NewKafkaEventProducer(cfg)
	svc := service.NewFileService(repo, s3Svc, ep, cfg)
	h := handler.NewFileHandler(svc)

	r := gin.Default()

	// Security Middleware
	r.Use(middleware.SecurityMiddleware(cfg))

	// Routes (New structure as requested: /file/...)
	fileGroup := r.Group("/file")
	{
		fileGroup.POST("/upload", h.Upload)
		fileGroup.POST("/upload-async", h.UploadAsync)
		fileGroup.GET("/:fileId", h.GetFileInfo)
		fileGroup.DELETE("/:fileId", h.DeleteFile)
	}

	// Actuator health check (for Kubernetes)
	r.GET("/file/actuator/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "UP"})
	})

	log.Printf("File Service (PostgreSQL) starting on port %s...", cfg.Port)
	if err := r.Run(":" + cfg.Port); err != nil {
		log.Fatal("Failed to run server:", err)
	}
}
