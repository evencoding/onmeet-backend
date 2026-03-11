package service

import (
	"encoding/json"
	"testing"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// mockEventProducer는 EventProducer 인터페이스의 Mock 구현체입니다.
type mockEventProducer struct {
	mock.Mock
}

func (m *mockEventProducer) SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error {
	args := m.Called(topic, fileId, fileName, fileUrl, uploaderId, correlationId)
	return args.Error(0)
}

func TestRecordingConsumer_HandleMessage_Success(t *testing.T) {
	// Setup
	mockRepo := new(mockFileRepository)
	mockEP := new(mockEventProducer)
	cfg := &config.Config{
		CloudFrontDomain: "cdn.example.com",
		KafkaBrokers:     "localhost:9092",
	}

	consumer := &RecordingConsumer{
		repo:          mockRepo,
		eventProducer: mockEP,
		cfg:           cfg,
	}

	event := RecordingCompletedEvent{
		RoomID:              100,
		RecordingID:         1,
		EgressID:            "egress-123",
		S3Path:              "/recordings/100/user1/audio_track1.ogg",
		FileSizeBytes:       2048,
		ParticipantIdentity: "user1",
		TrackSid:            "track1",
		RecordingType:       "PARTICIPANT_AUDIO",
	}

	payload, _ := json.Marshal(event)

	// Mock: repo.Save 호출 시 성공 (nil 반환)
	mockRepo.On("Save", mock.MatchedBy(func(m *model.FileMetadata) bool {
		return m.Category == "recording" &&
			m.OwnerType == "ROOM" &&
			m.OwnerID == "100" &&
			m.ContentType == "audio/ogg" &&
			m.FileSize == 2048
	})).Return(nil)

	// handleMessage 호출 (Kafka writer는 실제 연결이 필요하므로 DB 저장까지만 검증)
	consumer.handleMessage(payload)

	// Verify
	mockRepo.AssertExpectations(t)
}

func TestRecordingConsumer_HandleMessage_InvalidJson(t *testing.T) {
	mockRepo := new(mockFileRepository)
	mockEP := new(mockEventProducer)
	cfg := &config.Config{
		CloudFrontDomain: "cdn.example.com",
		KafkaBrokers:     "localhost:9092",
	}

	consumer := &RecordingConsumer{
		repo:          mockRepo,
		eventProducer: mockEP,
		cfg:           cfg,
	}

	// 잘못된 JSON 전달 → repo.Save가 호출되지 않아야 함
	consumer.handleMessage([]byte("invalid-json"))

	mockRepo.AssertNotCalled(t, "Save", mock.Anything)
}

func TestRecordingConsumer_HandleMessage_MetadataFields(t *testing.T) {
	mockRepo := new(mockFileRepository)
	mockEP := new(mockEventProducer)
	cfg := &config.Config{
		CloudFrontDomain: "cdn.example.com",
		KafkaBrokers:     "localhost:9092",
	}

	consumer := &RecordingConsumer{
		repo:          mockRepo,
		eventProducer: mockEP,
		cfg:           cfg,
	}

	event := RecordingCompletedEvent{
		RoomID:              200,
		RecordingID:         5,
		EgressID:            "egress-xyz",
		S3Path:              "/recordings/200/participant2/audio_track2.ogg",
		FileSizeBytes:       4096,
		ParticipantIdentity: "participant2",
	}

	payload, _ := json.Marshal(event)

	var savedMetadata *model.FileMetadata
	mockRepo.On("Save", mock.MatchedBy(func(m *model.FileMetadata) bool {
		savedMetadata = m
		return true
	})).Return(nil)

	consumer.handleMessage(payload)

	assert.NotNil(t, savedMetadata)
	assert.Equal(t, "recording", savedMetadata.Category)
	assert.Equal(t, "ROOM", savedMetadata.OwnerType)
	assert.Equal(t, "200", savedMetadata.OwnerID)
	assert.Equal(t, int64(4096), savedMetadata.FileSize)
	assert.Equal(t, "audio/ogg", savedMetadata.ContentType)
	assert.Equal(t, "https://cdn.example.com/recordings/200/participant2/audio_track2.ogg", savedMetadata.S3URL)
	assert.Contains(t, savedMetadata.FileName, "recording_5_participant2.ogg")
	assert.Contains(t, savedMetadata.OriginalFileName, "recording_5_participant2.ogg")
}
