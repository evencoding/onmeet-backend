package handler

import (
	"net/http"
	"strconv"

	"com.onmeet.file/internal/service"
	"github.com/gin-gonic/gin"
)

type FileHandler struct {
	svc service.FileService
}

func NewFileHandler(svc service.FileService) *FileHandler {
	return &FileHandler{svc: svc}
}

func (h *FileHandler) Upload(c *gin.Context) {
	form, _ := c.MultipartForm()
	files := form.File["files"]
	category := c.PostForm("category")
	ownerType := c.PostForm("ownerType")
	ownerId := c.PostForm("ownerId")

	var uploaderId *int64
	if idStr, exists := c.Get("userId"); exists {
		id, _ := strconv.ParseInt(idStr.(string), 10, 64)
		uploaderId = &id
	}

	results, err := h.svc.UploadFiles(files, category, uploaderId, ownerType, ownerId)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

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

	c.JSON(http.StatusAccepted, gin.H{
		"message":   "Batch file upload started asynchronously",
		"fileCount": len(files),
	})
}

func (h *FileHandler) GetFileInfo(c *gin.Context) {
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

	err := h.svc.DeleteFile(uint(id), requesterId)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.Status(http.StatusNoContent)
}
