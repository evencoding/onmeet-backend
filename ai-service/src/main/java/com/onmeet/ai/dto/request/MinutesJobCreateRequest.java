package com.onmeet.ai.dto.request;

public record MinutesJobCreateRequest(
        String requestedBy,
        String language,
        String style,
        String model
) {

}