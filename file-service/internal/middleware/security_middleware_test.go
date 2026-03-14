package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"com.onmeet.file/internal/config"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
)

func newSecurityTestRouter(cfg *config.Config, handler gin.HandlerFunc) *gin.Engine {
	router := gin.New()
	router.Use(SecurityMiddleware(cfg))
	router.GET("/test", handler)
	return router
}

func TestSecurityMiddleware_ValidSecret_ShouldPassThrough(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "correct-secret"}

	router := newSecurityTestRouter(cfg, func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "correct-secret")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSecurityMiddleware_InvalidSecret_ShouldReturn403(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "correct-secret"}

	router := newSecurityTestRouter(cfg, func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "wrong-secret")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
	assert.Contains(t, w.Body.String(), "FILE_014")
}

func TestSecurityMiddleware_EmptySecret_ShouldReturn403(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "correct-secret"}

	router := newSecurityTestRouter(cfg, func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	// No X-Gateway-Secret header
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestSecurityMiddleware_EmptyConfigSecret_AnySecretFails(t *testing.T) {
	// When GatewaySharedSecret is empty, even a non-empty header should fail
	// (empty vs empty passes, but non-empty vs empty fails)
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: ""}

	router := newSecurityTestRouter(cfg, func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "some-secret")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestSecurityMiddleware_BothEmpty_ShouldPassThrough(t *testing.T) {
	// When both config secret and header are empty, ConstantTimeCompare("","") == 1
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: ""}

	router := newSecurityTestRouter(cfg, func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	// Empty header matches empty config secret
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSecurityMiddleware_UserIdInjectedToContext(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "secret"}

	var capturedUserId interface{}
	router := gin.New()
	router.Use(SecurityMiddleware(cfg))
	router.GET("/test", func(c *gin.Context) {
		capturedUserId, _ = c.Get("userId")
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "secret")
	req.Header.Set("X-User-Id", "42")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "42", capturedUserId)
}

func TestSecurityMiddleware_UserRolesInjectedToContext(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "secret"}

	var capturedRoles interface{}
	router := gin.New()
	router.Use(SecurityMiddleware(cfg))
	router.GET("/test", func(c *gin.Context) {
		capturedRoles, _ = c.Get("userRoles")
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "secret")
	req.Header.Set("X-User-Id", "1")
	req.Header.Set("X-User-Roles", "USER,MANAGER")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	roles, ok := capturedRoles.([]string)
	assert.True(t, ok)
	assert.Equal(t, []string{"USER", "MANAGER"}, roles)
}

func TestSecurityMiddleware_NoUserIdHeader_ContextNotSet(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "secret"}

	userIdSet := false
	router := gin.New()
	router.Use(SecurityMiddleware(cfg))
	router.GET("/test", func(c *gin.Context) {
		_, userIdSet = c.Get("userId")
		c.Status(http.StatusOK)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Gateway-Secret", "secret")
	// No X-User-Id header
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.False(t, userIdSet, "userId should not be set in context when header is absent")
}

func TestSecurityMiddleware_SwaggerPathBypass(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cfg := &config.Config{GatewaySharedSecret: "secret"}

	swaggerPaths := []string{
		"/swagger/index.html",
		"/file/swagger/index.html",
		"/webjars/swagger-ui/bundle.js",
		"/file/doc.json",
		"/v3/api-docs",
	}

	for _, path := range swaggerPaths {
		router := gin.New()
		router.Use(SecurityMiddleware(cfg))
		router.GET(path, func(c *gin.Context) {
			c.Status(http.StatusOK)
		})

		w := httptest.NewRecorder()
		req, _ := http.NewRequest("GET", path, nil)
		// No X-Gateway-Secret header — swagger paths should bypass auth
		router.ServeHTTP(w, req)

		assert.Equal(t, http.StatusOK, w.Code, "Swagger path should bypass security: %s", path)
	}
}
