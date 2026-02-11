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

func NewFileHandler(svc service.FileService) *FileHandler {
	return &FileHandler{svc: svc}
}

// Upload는 파일 업로드 요청을 처리합니다.
// (c *gin.Context)는 요청 정보와 응답 전송 기능을 모두 담고 있는 핵심 객체입니다.
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

func (h *FileHandler) GenerateProfileImage(c *gin.Context) {
	var req struct {
		Name      string `json:"name"`
		Color     string `json:"color"`
		OwnerType string `json:"ownerType"`
		OwnerId   string `json:"ownerId"`
	}

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
