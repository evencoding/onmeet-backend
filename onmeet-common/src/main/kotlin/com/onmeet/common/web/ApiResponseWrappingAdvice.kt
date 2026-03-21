package com.onmeet.common.web

import com.onmeet.common.response.ApiResponse
import org.springframework.core.MethodParameter
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@RestControllerAdvice
class ApiResponseWrappingAdvice : ResponseBodyAdvice<Any> {

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean {
        val declaringClass = returnType.declaringClass

        // Skip springdoc (Swagger) and actuator endpoints
        val packageName = declaringClass.packageName
        if (packageName.contains("springdoc") || packageName.contains("actuator")) return false

        val returnClass = returnType.method?.returnType ?: return false

        return !ResponseEntity::class.java.isAssignableFrom(returnClass) &&
               !String::class.java.isAssignableFrom(returnClass) &&
               !ByteArray::class.java.isAssignableFrom(returnClass) &&
               !Resource::class.java.isAssignableFrom(returnClass) &&
               returnClass.simpleName != "ApiResponse"
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        // SSE 스트림은 래핑 제외
        if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(selectedContentType)) return body
        // Guard against any ApiResponse class regardless of package (e.g. video-service)
        if (body is ApiResponse<*> || body?.javaClass?.simpleName == "ApiResponse") return body
        return ApiResponse.success(body)
    }
}
