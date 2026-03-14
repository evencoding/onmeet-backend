package handler

import (
	"net/http"
	"strconv"

	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/service"
	"github.com/gin-gonic/gin"
)

// FileHandler는 HTTP 요청을 처리하는 컨트롤러 역할을 합니다.
type FileHandler struct {
	svc service.FileService
}

// GenerateProfileRequest defines the body for generating a default profile image.
type GenerateProfileRequest struct {
	Name      string `json:"name" example:"홍길동"`
	Color     string `json:"color" example:"#FF5733"`
	OwnerType string `json:"ownerType" example:"USER"`
	OwnerId   string `json:"ownerId" example:"123"`
}

func NewFileHandler(svc service.FileService) *FileHandler {
	return &FileHandler{svc: svc}
}

// getUserIdFromContext는 Gin 컨텍스트에서 userId를 파싱하는 공통 헬퍼입니다.
func getUserIdFromContext(c *gin.Context) *int64 {
	if idStr, exists := c.Get("userId"); exists {
		if id, err := strconv.ParseInt(idStr.(string), 10, 64); err == nil {
			return &id
		}
	}
	return nil
}

// respondError는 에러가 *model.AppError이면 해당 코드/상태를 사용하고,
// 아니면 defaultAppErr으로 응답합니다.
func respondError(c *gin.Context, err error, defaultAppErr *model.AppError) {
	if appErr, ok := err.(*model.AppError); ok {
		c.JSON(appErr.Status, model.ErrorResponseFromAppError(appErr))
	} else {
		c.JSON(defaultAppErr.Status, model.ErrorResponseFromAppError(defaultAppErr))
	}
}

// Upload godoc
// @Summary      파일 업로드
// @Description  멀티파트 폼으로 하나 이상의 파일을 S3에 업로드합니다.
// @Description  업로드된 파일의 메타데이터(URL, 크기, 타입 등)가 PostgreSQL에 저장됩니다.
// @Description  category, ownerType, ownerId를 지정하지 않으면 기본값(USER / 업로더 ID)이 사용됩니다.
// @Tags         file
// @Accept       multipart/form-data
// @Produce      json
// @Param        files     formData  file    true   "업로드할 파일 (복수 가능)"
// @Param        category  formData  string  false  "파일 카테고리 (예: TEAM_PROFILE, MEETING_SUMMARY)"
// @Param        ownerType formData  string  false  "소유자 타입 (USER | TEAM | COMPANY). 기본값: USER"
// @Param        ownerId   formData  string  false  "소유자 ID. 미입력 시 업로더 ID 사용"
// @Success      200  {array}   model.FileMetadata  "업로드된 파일 메타데이터 목록"
// @Failure      400  {object}  model.ErrorResponse "FILE_001: 멀티파트 폼 데이터 파싱 실패 (Content-Type이 multipart/form-data가 아닌 경우)"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Failure      500  {object}  model.ErrorResponse "FILE_002: S3 업로드 또는 DB 저장 실패"
// @Router       /upload [post]
func (h *FileHandler) Upload(c *gin.Context) {
	form, err := c.MultipartForm()
	if err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrMultipartParseFail))
		return
	}
	files := form.File["files"]
	category := c.PostForm("category")
	ownerType := c.PostForm("ownerType")
	ownerId := c.PostForm("ownerId")

	uploaderId := getUserIdFromContext(c)

	results, err := h.svc.UploadFiles(c.Request.Context(), files, category, uploaderId, ownerType, ownerId)
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeUploadFail, http.StatusInternalServerError, "파일 업로드에 실패했습니다"))
		return
	}

	c.JSON(http.StatusOK, results)
}

// UploadAsync godoc
// @Summary      파일 비동기 업로드
// @Description  파일을 백그라운드 고루틴으로 비동기 처리하고 즉시 202를 반환합니다.
// @Description  실제 S3 업로드 완료 후 callbackTopic으로 Kafka 이벤트가 발행됩니다.
// @Tags         file
// @Accept       multipart/form-data
// @Produce      json
// @Param        files          formData  file    true   "업로드할 파일 (복수 가능)"
// @Param        category       formData  string  false  "파일 카테고리"
// @Param        ownerType      formData  string  false  "소유자 타입 (USER | TEAM | COMPANY)"
// @Param        ownerId        formData  string  false  "소유자 ID"
// @Param        callbackTopic  formData  string  false  "업로드 완료 Kafka 토픽. 기본값: file-upload-events"
// @Param        correlationId  formData  string  false  "요청 추적을 위한 Correlation ID"
// @Success      202  {object}  map[string]interface{}  "비동기 업로드 시작 확인 (message, fileCount)"
// @Failure      400  {object}  model.ErrorResponse     "FILE_003: 멀티파트 폼 데이터 파싱 실패"
// @Failure      403  {object}  model.ErrorResponse     "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Router       /upload-async [post]
func (h *FileHandler) UploadAsync(c *gin.Context) {
	form, err := c.MultipartForm()
	if err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrAsyncMultipartParseFail))
		return
	}
	files := form.File["files"]
	category := c.PostForm("category")
	ownerType := c.PostForm("ownerType")
	ownerId := c.PostForm("ownerId")
	callbackTopic := c.PostForm("callbackTopic")
	correlationId := c.PostForm("correlationId")

	uploaderId := getUserIdFromContext(c)

	for _, file := range files {
		h.svc.UploadFileAsync(c.Request.Context(), file, category, uploaderId, ownerType, ownerId, callbackTopic, correlationId)
	}

	c.JSON(http.StatusAccepted, gin.H{
		"message":   "Batch file upload started asynchronously",
		"fileCount": len(files),
	})
}

// GetFileInfo godoc
// @Summary      파일 메타데이터 조회
// @Description  파일 ID로 DB에 저장된 파일 메타데이터를 조회합니다.
// @Tags         file
// @Produce      json
// @Param        fileId  path  int  true  "파일 ID (양의 정수)"
// @Success      200  {object}  model.FileMetadata  "파일 메타데이터"
// @Failure      400  {object}  model.ErrorResponse "FILE_004: fileId가 유효한 양의 정수가 아닌 경우"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Failure      404  {object}  model.ErrorResponse "FILE_005: 해당 ID의 파일이 DB에 존재하지 않는 경우 | FILE_042: DB 레코드 없음"
// @Failure      500  {object}  model.ErrorResponse "FILE_041: DB 연결 오류 등 내부 서버 에러"
// @Router       /{fileId} [get]
func (h *FileHandler) GetFileInfo(c *gin.Context) {
	idStr := c.Param("fileId")
	id, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrInvalidFileID))
		return
	}

	metadata, err := h.svc.GetFile(uint(id))
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeFileNotFound, http.StatusNotFound, "파일을 찾을 수 없습니다"))
		return
	}

	c.JSON(http.StatusOK, metadata)
}

// DeleteFile godoc
// @Summary      파일 삭제
// @Description  파일 ID로 파일을 삭제합니다. 삭제 권한: MANAGER 또는 ADMIN 역할 보유자만 가능합니다.
// @Tags         file
// @Param        fileId  path  int  true  "파일 ID (양의 정수)"
// @Success      204  "삭제 성공 (응답 본문 없음)"
// @Failure      400  {object}  model.ErrorResponse "FILE_006: fileId가 유효한 양의 정수가 아닌 경우"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: 게이트웨이 시크릿 오류 | FILE_020: MANAGER/ADMIN 권한 없음 | FILE_021: 다른 회사 파일 접근 불가"
// @Failure      404  {object}  model.ErrorResponse "FILE_042: 삭제 대상 파일이 DB에 존재하지 않는 경우"
// @Failure      500  {object}  model.ErrorResponse "FILE_007: S3 삭제 또는 DB 삭제 실패 | FILE_019: auth-service 권한 조회 실패"
// @Router       /{fileId} [delete]
func (h *FileHandler) DeleteFile(c *gin.Context) {
	idStr := c.Param("fileId")
	id, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrDeleteInvalidFileID))
		return
	}

	var requesterId int64
	if idPtr := getUserIdFromContext(c); idPtr != nil {
		requesterId = *idPtr
	}

	err = h.svc.DeleteFile(c.Request.Context(), uint(id), requesterId)
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeDeleteFail, http.StatusInternalServerError, "파일 삭제에 실패했습니다"))
		return
	}

	c.Status(http.StatusNoContent)
}

// DeleteMyProfile godoc
// @Summary      내 프로필 이미지 삭제
// @Description  현재 인증된 사용자가 업로드한 모든 프로필 카테고리 파일을 S3 및 DB에서 삭제합니다.
// @Tags         file
// @Success      204  "삭제 성공 (응답 본문 없음)"
// @Failure      401  {object}  model.ErrorResponse "FILE_008: X-User-Id 헤더가 없어 사용자를 식별할 수 없는 경우"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Failure      500  {object}  model.ErrorResponse "FILE_009: S3 또는 DB 삭제 중 일부 또는 전체 실패"
// @Router       /me/profile [delete]
func (h *FileHandler) DeleteMyProfile(c *gin.Context) {
	idPtr := getUserIdFromContext(c)
	if idPtr == nil {
		c.JSON(http.StatusUnauthorized, model.ErrorResponseFromAppError(model.ErrUnauthorized))
		return
	}

	err := h.svc.DeleteMyProfile(c.Request.Context(), *idPtr)
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeProfileDeleteFail, http.StatusInternalServerError, "프로필 이미지 삭제에 실패했습니다"))
		return
	}

	c.Status(http.StatusNoContent)
}

// GenerateProfileImage godoc
// @Summary      기본 프로필 이미지 생성
// @Description  이름 첫 글자와 배경색을 기반으로 SVG 프로필 이미지를 생성하고 S3에 저장합니다.
// @Tags         file
// @Accept       json
// @Produce      json
// @Param        request  body  handler.GenerateProfileRequest  true  "프로필 생성 요청"
// @Success      200  {object}  model.FileMetadata  "생성된 프로필 이미지 메타데이터"
// @Failure      400  {object}  model.ErrorResponse "FILE_010: 요청 JSON 파싱 실패 (잘못된 형식)"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Failure      500  {object}  model.ErrorResponse "FILE_011: SVG S3 업로드 또는 DB 저장 실패"
// @Router       /profile/default [post]
func (h *FileHandler) GenerateProfileImage(c *gin.Context) {
	var req GenerateProfileRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrRequestParseFail))
		return
	}

	uploaderId := getUserIdFromContext(c)

	metadata, err := h.svc.GenerateDefaultProfileImage(c.Request.Context(), req.Name, req.Color, uploaderId, req.OwnerType, req.OwnerId)
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeDefaultProfileGenFail, http.StatusInternalServerError, "기본 프로필 이미지 생성에 실패했습니다"))
		return
	}

	c.JSON(http.StatusOK, metadata)
}

// RenderFile godoc
// @Summary      파일 렌더링 (다운로드)
// @Description  파일 ID로 S3에서 실제 파일 바이너리를 가져와 반환합니다.
// @Description  1MB 미만의 파일은 서버 인메모리 캐시(TTL 1시간)에 저장되어 반복 요청 시 S3 호출 없이 응답합니다.
// @Description  1MB 이상의 파일은 S3에서 직접 스트리밍합니다.
// @Tags         file
// @Produce      application/octet-stream
// @Param        fileId  path  int  true  "파일 ID (양의 정수)"
// @Success      200  {string}  binary  "파일 바이너리 데이터 (Cache-Control: public, max-age=3600)"
// @Failure      400  {object}  model.ErrorResponse "FILE_012: fileId가 유효한 양의 정수가 아닌 경우"
// @Failure      403  {object}  model.ErrorResponse "FILE_014: X-Gateway-Secret 헤더 누락 또는 불일치"
// @Failure      404  {object}  model.ErrorResponse "FILE_013: 파일 메타데이터 없음 또는 S3 다운로드 실패 | FILE_033: DB에서 파일을 찾을 수 없음"
// @Failure      500  {object}  model.ErrorResponse "FILE_034: S3 GetObject 오류 | FILE_035: 파일 내용 읽기 오류"
// @Router       /render/{fileId} [get]
func (h *FileHandler) RenderFile(c *gin.Context) {
	idStr := c.Param("fileId")
	id, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, model.ErrorResponseFromAppError(model.ErrRenderInvalidFileID))
		return
	}

	reader, size, contentType, err := h.svc.RenderFile(c.Request.Context(), uint(id))
	if err != nil {
		respondError(c, err, model.NewAppError(model.CodeRenderFail, http.StatusNotFound, "파일을 찾을 수 없거나 렌더링에 실패했습니다"))
		return
	}
	defer reader.Close()

	c.Header("Cache-Control", "public, max-age=3600")
	c.DataFromReader(http.StatusOK, size, contentType, reader, nil)
}
