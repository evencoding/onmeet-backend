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

    @GetMapping("/code/{roomCode}")
    fun getRoomByCode(
        @PathVariable roomCode: String,
        @RequestHeader("X-Internal-Secret") secret: String
    ): RoomResponse
}
