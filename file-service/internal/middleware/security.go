package middleware

import (
	"crypto/subtle"
	"net/http"
	"strings"

	"com.onmeet.file/internal/config"
	"github.com/gin-gonic/gin"
)

func SecurityMiddleware(cfg *config.Config) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Validate Gateway Secret
		gatewaySecret := c.GetHeader("X-Gateway-Secret")
		if subtle.ConstantTimeCompare([]byte(gatewaySecret), []byte(cfg.GatewaySharedSecret)) != 1 {
			c.JSON(http.StatusForbidden, gin.H{"error": "Invalid Gateway Secret"})
			c.Abort()
			return
		}

		// Pull User Info (if exists)
		userId := c.GetHeader("X-User-Id")
		userRoles := c.GetHeader("X-User-Roles")

		if userId != "" {
			c.Set("userId", userId)
		}
		if userRoles != "" {
			roles := strings.Split(userRoles, ",")
			c.Set("userRoles", roles)
		}

		c.Next()
	}
}
