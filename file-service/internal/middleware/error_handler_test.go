package middleware

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"com.onmeet.file/internal/model"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
)

func newErrorHandlerRouter(triggerFunc gin.HandlerFunc) *gin.Engine {
	router := gin.New()
	router.Use(ErrorHandlerMiddleware())
	router.GET("/test", triggerFunc)
	return router
}

func TestErrorHandlerMiddleware_AppError_ReturnsCorrectStatusAndCode(t *testing.T) {
	gin.SetMode(gin.TestMode)

	appErr := model.NewAppError("FILE_005", http.StatusNotFound, "파일을 찾을 수 없습니다")
	router := newErrorHandlerRouter(func(c *gin.Context) {
		_ = c.Error(appErr)
		// Do not write response; let middleware handle it
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.Contains(t, w.Body.String(), "FILE_005")
	assert.Contains(t, w.Body.String(), "파일을 찾을 수 없습니다")
}

func TestErrorHandlerMiddleware_UnhandledError_Returns500(t *testing.T) {
	gin.SetMode(gin.TestMode)

	router := newErrorHandlerRouter(func(c *gin.Context) {
		_ = c.Error(errors.New("unexpected internal failure"))
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)
	assert.Contains(t, w.Body.String(), "INTERNAL_ERROR")
	assert.Contains(t, w.Body.String(), "unexpected internal failure")
}

func TestErrorHandlerMiddleware_NoError_DoesNotInterfere(t *testing.T) {
	gin.SetMode(gin.TestMode)

	router := newErrorHandlerRouter(func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), "ok")
}

func TestErrorHandlerMiddleware_ResponseAlreadyWritten_DoesNotOverride(t *testing.T) {
	gin.SetMode(gin.TestMode)

	// Handler writes a response AND registers an error; the middleware should not override
	router := newErrorHandlerRouter(func(c *gin.Context) {
		c.JSON(http.StatusBadRequest, gin.H{"custom": "response"})
		_ = c.Error(model.ErrInvalidFileID)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	// Should still be the original BadRequest, not overridden by middleware
	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "custom")
}

func TestErrorHandlerMiddleware_MultipleErrors_UsesLastError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	firstErr := model.NewAppError("FILE_001", http.StatusBadRequest, "first error")
	lastErr := model.NewAppError("FILE_005", http.StatusNotFound, "last error")

	router := newErrorHandlerRouter(func(c *gin.Context) {
		_ = c.Error(firstErr)
		_ = c.Error(lastErr)
		// No response written; middleware picks up the last error
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	// ErrorHandlerMiddleware uses c.Errors.Last()
	assert.Equal(t, http.StatusNotFound, w.Code)
	assert.Contains(t, w.Body.String(), "FILE_005")
}

func TestErrorHandlerMiddleware_ForbiddenAppError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	router := newErrorHandlerRouter(func(c *gin.Context) {
		_ = c.Error(model.ErrInvalidGatewaySecret)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
	assert.Contains(t, w.Body.String(), "FILE_014")
}
