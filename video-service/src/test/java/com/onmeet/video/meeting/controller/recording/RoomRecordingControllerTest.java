package com.onmeet.video.meeting.controller.recording;

import com.onmeet.video.meeting.dto.recording.RoomRecordingResponse;
import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RecordingType;
import com.onmeet.video.meeting.service.recording.RoomRecordingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomRecordingController.class)
@ActiveProfiles("test")
class RoomRecordingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomRecordingService recordingService;

    private RoomRecordingResponse sampleRecordingResponse() {
        return new RoomRecordingResponse(
                1L, 10L, "egress_123",
                RecordingType.PARTICIPANT_AUDIO, RecordingStatus.RECORDING,
                null, null, null, 0, "user1", "track1",
                Instant.now(), null, Instant.now()
        );
    }

    // [ITEM-6] POST /v1/rooms/{roomId}/recording/start → void (no body)
    @Test
    @WithMockUser
    void startRecording_returnsVoid() throws Exception {
        // recordingService.startRecording() returns void - no body expected in data
        mockMvc.perform(post("/v1/rooms/10/recording/start")
                        .header("X-User-Id", "100")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // [ITEM-6] GET /v1/rooms/{roomId}/recording/status → single RoomRecordingResponse
    @Test
    @WithMockUser
    void getRecordingStatus_returnsSingleObject() throws Exception {
        when(recordingService.getActiveRecording(anyLong())).thenReturn(sampleRecordingResponse());

        mockMvc.perform(get("/v1/rooms/10/recording/status")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.egressId").value("egress_123"))
                .andExpect(jsonPath("$.data").isMap());
    }
}
