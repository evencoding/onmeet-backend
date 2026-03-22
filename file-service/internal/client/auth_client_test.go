package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"github.com/stretchr/testify/assert"
)

func TestAuthClient_GetUserPermissions_Success(t *testing.T) {
	companyId := int64(10)
	expected := UserPermissionResponse{
		UserID:    42,
		Roles:     []string{"USER", "MANAGER"},
		CompanyID: &companyId,
		TeamIDs:   []int64{1, 2},
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/auth/v1/internal/users/42/permissions", r.URL.Path)
		assert.Equal(t, "test-gateway-secret", r.Header.Get("X-Gateway-Secret"))
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(expected)
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL, GatewaySharedSecret: "test-gateway-secret"}
	c := NewAuthClient(cfg)

	result, err := c.GetUserPermissions(42)

	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, int64(42), result.UserID)
	assert.Equal(t, []string{"USER", "MANAGER"}, result.Roles)
	assert.Equal(t, &companyId, result.CompanyID)
}

func TestAuthClient_GetUserPermissions_GatewaySecretForwarded(t *testing.T) {
	receivedSecret := ""
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedSecret = r.Header.Get("X-Gateway-Secret")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(UserPermissionResponse{UserID: 1, Roles: []string{"USER"}})
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL, GatewaySharedSecret: "inter-service-secret"}
	c := NewAuthClient(cfg)

	_, _ = c.GetUserPermissions(1)

	assert.Equal(t, "inter-service-secret", receivedSecret)
}

func TestAuthClient_GetUserPermissions_Non200Response(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL}
	c := NewAuthClient(cfg)

	result, err := c.GetUserPermissions(1)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeAuthBadResponse, appErr.Code)
}

func TestAuthClient_GetUserPermissions_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL}
	c := NewAuthClient(cfg)

	result, err := c.GetUserPermissions(1)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeAuthBadResponse, appErr.Code)
}

func TestAuthClient_GetUserPermissions_InvalidJSON(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("not-valid-json{{{"))
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL}
	c := NewAuthClient(cfg)

	result, err := c.GetUserPermissions(1)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeAuthParseFail, appErr.Code)
}

func TestAuthClient_GetUserPermissions_Timeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Sleep longer than the client timeout
		time.Sleep(6 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL}
	c := &authClient{
		baseURL:             cfg.AuthServiceURL,
		gatewaySharedSecret: "",
		client: &http.Client{
			Timeout: 10 * time.Millisecond,
		},
	}

	result, err := c.GetUserPermissions(1)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeAuthConnFail, appErr.Code)
}

func TestAuthClient_GetUserPermissions_ConnectionRefused(t *testing.T) {
	// Use an unreachable address to simulate connection failure
	cfg := &config.Config{AuthServiceURL: "http://localhost:1"}
	c := NewAuthClient(cfg)

	result, err := c.GetUserPermissions(1)

	assert.Nil(t, result)
	assert.Error(t, err)
	appErr, ok := err.(*model.AppError)
	assert.True(t, ok)
	assert.Equal(t, model.CodeAuthConnFail, appErr.Code)
}

func TestAuthClient_GetUserPermissions_CorrectURLPath(t *testing.T) {
	calledPath := ""
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calledPath = r.URL.Path
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(UserPermissionResponse{UserID: 99, Roles: []string{"ADMIN"}})
	}))
	defer server.Close()

	cfg := &config.Config{AuthServiceURL: server.URL}
	c := NewAuthClient(cfg)

	_, _ = c.GetUserPermissions(99)

	assert.Equal(t, "/auth/v1/internal/users/99/permissions", calledPath)
}
