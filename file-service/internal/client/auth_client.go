package client

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"com.onmeet.file/internal/config"
)

// UserPermissionResponse는 auth-service의 동명 DTO와 매칭되는 구조체입니다.
type UserPermissionResponse struct {
	UserID    int64    `json:"userId"`
	Roles     []string `json:"roles"`
	CompanyID *int64   `json:"companyId"`
	TeamIDs   []int64  `json:"teamIds"`
}

// AuthClient는 auth-service와 통신하여 유저 정보를 가져오는 역할을 합니다.
type AuthClient interface {
	GetUserPermissions(userId int64, cookie string) (*UserPermissionResponse, error)
}

type authClient struct {
	baseURL string
	client  *http.Client
}

func NewAuthClient(cfg *config.Config) AuthClient {
	return &authClient{
		baseURL: cfg.AuthServiceURL,
		client: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

func (c *authClient) GetUserPermissions(userId int64, cookie string) (*UserPermissionResponse, error) {
	url := fmt.Sprintf("%s/users/internal/%d/permissions", c.baseURL, userId)

	// http.NewRequest를 사용하여 헤더를 제어할 수 있는 요청 객체를 생성합니다.
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create auth request: %w", err)
	}

	// 획득한 쿠키를 요청 헤더에 설정합니다. (BaseServiceClient.kt의 역할과 동일)
	if cookie != "" {
		req.Header.Set("Cookie", cookie)
	}

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("auth-service connection error: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("auth-service returned status: %d", resp.StatusCode)
	}

	var result UserPermissionResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &result, nil
}
