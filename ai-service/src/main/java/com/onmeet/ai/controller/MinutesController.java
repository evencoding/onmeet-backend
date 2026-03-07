package com.onmeet.ai.controller;

import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.service.MinutesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Minutes", description = "회의록 조회/수정/재생성 API")
@RestController
@RequestMapping("/v1/minutes")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @Operation(summary = "회의록 조회", description = "roomId로 회의록을 조회합니다.")
    @GetMapping("/{roomId}")
    public MinutesResponse get(@PathVariable Long roomId) {
        return minutesService.get(roomId);
    }

    @Operation(summary = "회의록 재생성", description = "기존 트랜스크립트를 기반으로 회의록을 재생성합니다.")
    @PostMapping("/{roomId}/regenerate")
    public MinutesResponse regenerate(
            @PathVariable Long roomId,
            @RequestBody(required = false) MinutesRegenerateRequest req
    ) {
        return minutesService.regenerate(roomId, req);
    }

    @Operation(summary = "회의록 수정", description = "회의록의 제목, 요약 등을 부분 수정합니다.")
    @PatchMapping("/{roomId}")
    public MinutesResponse patch(
            @PathVariable Long roomId,
            @RequestBody MinutesPatchRequest req
    ) {
        return minutesService.patch(roomId, req);
    }

    @Operation(summary = "트랜스크립트 원본 조회", description = "회의의 원본 트랜스크립트 JSON을 반환합니다.")
    @GetMapping(value = "/{roomId}/transcript", produces = MediaType.APPLICATION_JSON_VALUE)
    public String transcript(@PathVariable Long roomId) {
        return minutesService.getTranscriptRawJson(roomId);
    }
}
