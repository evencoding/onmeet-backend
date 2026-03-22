package middleware

import (
	"crypto/subtle"
	"log"
	"net/http"
	"strings"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"github.com/gin-gonic/gin"
)

// SecurityMiddleware는 들어오는 요청에 대해 보안 검증을 수행하는 미들웨어입니다.
// Spring Security의 Filter 역할을 수행한다고 보시면 됩니다.
func SecurityMiddleware(cfg *config.Config) gin.HandlerFunc {
	// 클로저를 반환하여 Gin에서 사용할 수 있는 핸들러 형태로 만듭니다.
	return func(c *gin.Context) {
		// Swagger/API docs 경로는 검증 없이 통과
		path := c.Request.URL.Path
		if strings.HasPrefix(path, "/swagger/") ||
			strings.HasPrefix(path, "/file/swagger/") ||
			strings.HasPrefix(path, "/webjars/") ||
			strings.HasPrefix(path, "/file/doc.json") ||
			strings.HasPrefix(path, "/v3/api-docs") {
			c.Next()
			return
		}

		// 1. 게이트웨이 비밀번호 검증
		secret := c.GetHeader("X-Gateway-Secret")                             // Renamed gatewaySecret to secret
		log.Printf("Security Middleware: Validating gateway secret for Path=%s\n", path)

		// subtle.ConstantTimeCompare는 타이밍 공격을 방지하기 위한 안전한 비교 함수입니다.
		// Original validation logic replaced with a placeholder for s.accessControl.ValidateGatewaySecret
		// For now, we'll keep the original validation but add the new logging.
		// If s.accessControl.ValidateGatewaySecret is intended, the SecurityMiddleware signature needs to change.
		if subtle.ConstantTimeCompare([]byte(secret), []byte(cfg.GatewaySharedSecret)) != 1 {
			log.Printf("Security Middleware: Invalid Secret for Path=%s\n", path)
			c.JSON(http.StatusForbidden, model.ErrorApiResponseFromAppError(model.ErrInvalidGatewaySecret))
			// Abort()를 호출하면 이후의 핸들러(컨트롤러) 실행이 중단됩니다.
			c.Abort()
			return
		}

		// 2. 유저 정보 추출 및 컨텍스트에 담기
		userId := c.GetHeader("X-User-Id")
		userRoles := c.GetHeader("X-User-Roles")

		if userId != "" {
			// c.Set은 Spring의 HttpServletRequest.setAttribute()처럼 요청 컨텍스트에 값을 저장합니다.
			c.Set("userId", userId)
		}
		if userRoles != "" {
			roles := strings.Split(userRoles, ",")
			c.Set("userRoles", roles)
		}

		// 다음 필터 또는 핸들러로 요청을 넘깁니다. (Filter.doFilter()와 유사)
		c.Next()
	}
}
