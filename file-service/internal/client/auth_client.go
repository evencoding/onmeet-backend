package client

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
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
	GetUserPermissions(userId int64) (*UserPermissionResponse, error)
}

type authClient struct {
	baseURL             string
	gatewaySharedSecret string
	client              *http.Client
}

func NewAuthClient(cfg *config.Config) AuthClient {
	return &authClient{
		baseURL:             cfg.AuthServiceURL,
		gatewaySharedSecret: cfg.GatewaySharedSecret,
		client: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

func (c *authClient) GetUserPermissions(userId int64) (*UserPermissionResponse, error) {
	url := fmt.Sprintf("%s/users/internal/%d/permissions", c.baseURL, userId)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, model.NewAppError(model.CodeAuthRequestFail, http.StatusInternalServerError, "auth-service 요청 객체 생성에 실패했습니다")
	}

	req.Header.Set("X-Gateway-Secret", c.gatewaySharedSecret)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, model.NewAppError(model.CodeAuthConnFail, http.StatusInternalServerError, "auth-service 연결에 실패했습니다")
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, model.NewAppError(model.CodeAuthBadResponse, http.StatusInternalServerError, "auth-service가 비정상 응답을 반환했습니다")
	}

	var result UserPermissionResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, model.NewAppError(model.CodeAuthParseFail, http.StatusInternalServerError, "auth-service 응답 파싱에 실패했습니다")
	}

	return &result, nil
}
