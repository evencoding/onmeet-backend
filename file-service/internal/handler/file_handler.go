package handler

import (
	"net/http"
	"strconv"

	"com.onmeet.file/internal/service"
	"github.com/gin-gonic/gin"
)

// FileHandler는 HTTP 요청을 처리하는 컨트롤러 역할을 합니다.
// Java/Kotlin의 @RestController와 같은 역할입니다.
type FileHandler struct {
	svc service.FileService
}

// GenerateProfileRequest defines the body for generating a default profile image.
type GenerateProfileRequest struct {
	Name      string `json:"name" example:"John Doe"`
	Color     string `json:"color" example:"#FF5733"`
	OwnerType string `json:"ownerType" example:"USER"`
	OwnerId   string `json:"ownerId" example:"123"`
}

func NewFileHandler(svc service.FileService) *FileHandler {
	return &FileHandler{svc: svc}
}

// Upload godoc
// @Summary      Upload files
// @Description  Upload multiple files to S3.
// @Tags         file
// @Accept       multipart/form-data
// @Produce      json
// @Param        files formData file true "Files to upload"
// @Param        category formData string false "File category (e.g., TEAM_PROFILE)"
// @Param        ownerType formData string false "Owner Type (e.g., USER, TEAM)"
// @Param        ownerId formData string false "Owner ID"
// @Success      200  {array}   model.FileMetadata
// @Failure      500  {object}  map[string]string
// @Router       /upload [post]
func (h *FileHandler) Upload(c *gin.Context) {
	// MultipartForm 데이터를 파싱합니다.
	form, _ := c.MultipartForm()
	files := form.File["files"]
	category := c.PostForm("category")
	ownerType := c.PostForm("ownerType")
	ownerId := c.PostForm("ownerId")

	var uploaderId *int64
	// c.Get("userId")는 미들웨어에서 설정한 값을 가져옵니다. (Spring의 SecurityContext와 유사)
	if idStr, exists := c.Get("userId"); exists {
		// idStr.(string)은 'Type Assertion'으로, interface{} 타입을 string으로 형변환합니다.
		id, _ := strconv.ParseInt(idStr.(string), 10, 64)
		uploaderId = &id
	}

	results, err := h.svc.UploadFiles(files, category, uploaderId, ownerType, ownerId)
	if err != nil {
		// 에러 발생 시 JSON 형태로 에러 메시지를 응답합니다.
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// 정상 완료 시 결과를 JSON으로 응답합니다. gin.H는 map의 축약형입니다.
	c.JSON(http.StatusOK, results)
}

// UploadAsync godoc
// @Summary      Upload files asynchronously
// @Description  Upload files asynchronously using Kafka.
// @Tags         file
// @Accept       multipart/form-data
// @Produce      json
// @Param        files formData file true "Files to upload"
// @Param        category formData string false "File category"
// @Param        ownerType formData string false "Owner Type"
// @Param        ownerId formData string false "Owner ID"
// @Param        callbackTopic formData string false "Callback Kafka Topic"
// @Param        correlationId formData string false "Correlation ID"
// @Success      202  {object}  map[string]interface{}
// @Router       /upload-async [post]
func (h *FileHandler) UploadAsync(c *gin.Context) {
	form, _ := c.MultipartForm()
	files := form.File["files"]
	category := c.PostForm("category")
	ownerType := c.PostForm("ownerType")
	ownerId := c.PostForm("ownerId")
	callbackTopic := c.PostForm("callbackTopic")
	correlationId := c.PostForm("correlationId")

	var uploaderId *int64
	if idStr, exists := c.Get("userId"); exists {
		id, _ := strconv.ParseInt(idStr.(string), 10, 64)
		uploaderId = &id
	}

	for _, file := range files {
		h.svc.UploadFileAsync(file, category, uploaderId, ownerType, ownerId, callbackTopic, correlationId)
	}

	// HTTP 202 (Accepted)는 요청을 수락했지만 처리는 비동기로 진행됨을 알립니다.
	c.JSON(http.StatusAccepted, gin.H{
		"message":   "Batch file upload started asynchronously",
		"fileCount": len(files),
	})
}

// GetFileInfo godoc
// @Summary      Get file info
// @Description  Get file metadata by ID.
// @Tags         file
// @Produce      json
// @Param        fileId path int true "File ID"
// @Success      200  {object}  model.FileMetadata
// @Failure      404  {object}  map[string]string
// @Router       /{fileId} [get]
func (h *FileHandler) GetFileInfo(c *gin.Context) {
	// URL 경로 파라미터(/:fileId)를 가져옵니다. @PathVariable과 같습니다.
	idStr := c.Param("fileId")
	id, _ := strconv.ParseUint(idStr, 10, 64)

	metadata, err := h.svc.GetFile(uint(id))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "File not found"})
		return
	}

	c.JSON(http.StatusOK, metadata)
}

// DeleteFile godoc
// @Summary      Delete file
// @Description  Delete file by ID (Soft delete in DB, Hard delete in S3).
// @Tags         file
// @Param        fileId path int true "File ID"
// @Success      204
// @Failure      500  {object}  map[string]string
// @Router       /{fileId} [delete]
func (h *FileHandler) DeleteFile(c *gin.Context) {
	idStr := c.Param("fileId")
	id, _ := strconv.ParseUint(idStr, 10, 64)

	var requesterId int64
	if idStrVal, exists := c.Get("userId"); exists {
		requesterId, _ = strconv.ParseInt(idStrVal.(string), 10, 64)
	}

	// 요청 헤더에서 쿠키 정보를 추출합니다.
	cookie := c.GetHeader("Cookie")

	err := h.svc.DeleteFile(uint(id), requesterId, cookie)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// No Content (204) 응답을 보냅니다.
	c.Status(http.StatusNoContent)
}

// DeleteMyProfile godoc
// @Summary      Delete my profile image
// @Description  Delete all profile images uploaded by the current user.
// @Tags         file
// @Success      204
// @Failure      500  {object}  map[string]string
// @Router       /me/profile [delete]
func (h *FileHandler) DeleteMyProfile(c *gin.Context) {
	var uploaderId int64
	if idStr, exists := c.Get("userId"); exists {
		uploaderId, _ = strconv.ParseInt(idStr.(string), 10, 64)
	} else {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Unauthorized"})
		return
	}

	err := h.svc.DeleteMyProfile(uploaderId)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.Status(http.StatusNoContent)
}

// GenerateProfileImage godoc
// @Summary      Generate default profile image
// @Description  Generate a default profile image based on name and color.
// @Tags         file
// @Accept       json
// @Produce      json
// @Param        request body GenerateProfileRequest true "Request Body"
// @Success      200  {object}  model.FileMetadata
// @Failure      500  {object}  map[string]string
// @Router       /profile/default [post]
func (h *FileHandler) GenerateProfileImage(c *gin.Context) {
	var req GenerateProfileRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	var uploaderId *int64
	if idStr, exists := c.Get("userId"); exists {
		id, _ := strconv.ParseInt(idStr.(string), 10, 64)
		uploaderId = &id
	}

	metadata, err := h.svc.GenerateDefaultProfileImage(req.Name, req.Color, uploaderId, req.OwnerType, req.OwnerId)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, metadata)
}

// RenderFile godoc
// @Summary      Render file
// @Description  Download or render file content.
// @Tags         file
// @Param        fileId path int true "File ID"
// @Success      200  {string}  binary
// @Failure      404  {object}  map[string]string
// @Router       /render/{fileId} [get]
func (h *FileHandler) RenderFile(c *gin.Context) {
	idStr := c.Param("fileId")
	id, _ := strconv.ParseUint(idStr, 10, 64)

	content, contentType, err := h.svc.RenderFile(uint(id))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "File not found or render error"})
		return
	}

	// 캐싱 헤더 설정 (옵션)
	c.Header("Cache-Control", "public, max-age=3600")
	c.Data(http.StatusOK, contentType, content)
}
