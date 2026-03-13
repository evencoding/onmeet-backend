package middleware

import (
	"net/http"

	"com.onmeet.file/internal/model"
	"github.com/gin-gonic/gin"
)

// ErrorHandlerMiddleware는 c.Error()로 등록된 에러를 통일된 ErrorResponse로 변환합니다.
// 핸들러가 c.Error(err)를 호출하고 직접 응답을 쓰지 않은 경우 이 미들웨어가 처리합니다.
func ErrorHandlerMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Next()

		if c.Writer.Written() || len(c.Errors) == 0 {
			return
		}

		err := c.Errors.Last().Err
		if appErr, ok := err.(*model.AppError); ok {
			c.JSON(appErr.Status, model.ErrorResponseFromAppError(appErr))
		} else {
			c.JSON(http.StatusInternalServerError, model.NewErrorResponse("INTERNAL_ERROR", http.StatusInternalServerError, err.Error()))
		}
	}
}
