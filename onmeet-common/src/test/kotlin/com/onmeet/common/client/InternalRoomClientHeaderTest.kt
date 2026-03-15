package com.onmeet.common.client

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RequestHeader
import java.lang.reflect.Method

// CHECK [video-담당자]: InternalRoomClient 헤더가 X-Gateway-Secret으로 변경됨.
// video-service GatewayPreAuthFilter에서 이 헤더를 정상 처리하는지 확인.
class InternalRoomClientHeaderTest {

    @Test
    fun `getRoomByCode uses X-Gateway-Secret header`() {
        val method: Method = InternalRoomClient::class.java.getMethod(
            "getRoomByCode", String::class.java, String::class.java
        )
        val params = method.parameters
        val secretParam = params.find { it.name == "secret" || it.type == String::class.java && it != params[0] }
            ?: params[1]

        val annotation = secretParam.getAnnotation(RequestHeader::class.java)
        assertTrue(annotation != null, "Second parameter should have @RequestHeader annotation")
        assertTrue(
            annotation.value == "X-Gateway-Secret" || annotation.name == "X-Gateway-Secret",
            "Header name should be X-Gateway-Secret but was: ${annotation.value.ifEmpty { annotation.name }}"
        )
    }
}
