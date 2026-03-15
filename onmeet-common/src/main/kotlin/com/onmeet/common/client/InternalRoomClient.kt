package com.onmeet.common.client

import com.onmeet.common.dto.RoomResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
    name = "video-service",
    url = "\${onmeet.video.internal-url:http://video-service:8083}",
    path = "/video/api/rooms"
)
interface InternalRoomClient {

    // CHECK [video-담당자]: InternalRoomClient 헤더가 X-Gateway-Secret으로 변경됨.
    // video-service GatewayPreAuthFilter에서 이 헤더를 정상 처리하는지 확인.
    @GetMapping("/code/{roomCode}")
    fun getRoomByCode(
        @PathVariable roomCode: String,
        @RequestHeader("X-Gateway-Secret") secret: String
    ): RoomResponse
}
