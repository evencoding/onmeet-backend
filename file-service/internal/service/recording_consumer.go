package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"com.onmeet.file/internal/config"
	"com.onmeet.file/internal/model"
	"com.onmeet.file/internal/repository"
	"github.com/segmentio/kafka-go"
)

// RecordingCompletedEvent는 video-service에서 발행하는 녹음 완료 이벤트입니다.
type RecordingCompletedEvent struct {
	RoomID              int64  `json:"roomId"`
	RecordingID         int64  `json:"recordingId"`
	EgressID            string `json:"egressId"`
	S3Path              string `json:"s3Path"`
	FileSizeBytes       int64  `json:"fileSizeBytes"`
	DurationSeconds     *int   `json:"durationSeconds"`
	ParticipantIdentity string `json:"participantIdentity"`
	TrackSid            string `json:"trackSid"`
	RecordingType       string `json:"recordingType"`
	StartedAt           string `json:"startedAt"`
	EndedAt             string `json:"endedAt"`
	Timestamp           string `json:"timestamp"`
}

// RecordingStoredEvent는 파일 등록 후 ai-service로 전달하는 이벤트입니다.
type RecordingStoredEvent struct {
	FileID              uint   `json:"fileId"`
	RoomID              int64  `json:"roomId"`
	RecordingID         int64  `json:"recordingId"`
	S3Path              string `json:"s3Path"`
	S3URL               string `json:"s3Url"`
	FileSizeBytes       int64  `json:"fileSizeBytes"`
	DurationSeconds     *int   `json:"durationSeconds"`
	ParticipantIdentity string `json:"participantIdentity"`
	Timestamp           string `json:"timestamp"`
}

const (
	recordingCompletedTopic = "recording-completed"
	recordingStoredTopic    = "recording-stored"
	consumerGroupID         = "file-recording-consumer"
)

// RecordingConsumer는 recording-completed 토픽을 구독하여 파일 메타데이터를 등록하는 컨슈머입니다.
type RecordingConsumer struct {
	reader        *kafka.Reader
	repo          repository.FileRepository
	eventProducer EventProducer
	cfg           *config.Config
}

// NewRecordingConsumer는 Kafka 컨슈머를 생성합니다.
func NewRecordingConsumer(cfg *config.Config, repo repository.FileRepository, ep EventProducer) *RecordingConsumer {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        []string{cfg.KafkaBrokers},
		Topic:          recordingCompletedTopic,
		GroupID:        consumerGroupID,
		StartOffset:    kafka.FirstOffset,
		CommitInterval: time.Second,
	})

	return &RecordingConsumer{
		reader:        reader,
		repo:          repo,
		eventProducer: ep,
		cfg:           cfg,
	}
}

// Start는 백그라운드 고루틴에서 Kafka 메시지를 계속 수신합니다.
func (c *RecordingConsumer) Start(ctx context.Context) {
	go func() {
		log.Printf("RecordingConsumer started: topic=%s, groupId=%s", recordingCompletedTopic, consumerGroupID)
		for {
			select {
			case <-ctx.Done():
				log.Println("RecordingConsumer stopping...")
				c.reader.Close()
				return
			default:
				msg, err := c.reader.ReadMessage(ctx)
				if err != nil {
					if ctx.Err() != nil {
						return
					}
					log.Printf("RecordingConsumer read error: %v", err)
					continue
				}
				c.handleMessage(msg.Value)
			}
		}
	}()
}

func (c *RecordingConsumer) handleMessage(payload []byte) {
	var event RecordingCompletedEvent
	if err := json.Unmarshal(payload, &event); err != nil {
		log.Printf("Failed to unmarshal RecordingCompletedEvent: %v", err)
		return
	}

	log.Printf("Received recording-completed: roomId=%d, recordingId=%d, s3Path=%s",
		event.RoomID, event.RecordingID, event.S3Path)

	// 파일 메타데이터를 DB에 등록 (S3 파일은 LiveKit이 이미 저장함)
	metadata := &model.FileMetadata{
		FileName:         fmt.Sprintf("recording_%d_%s.ogg", event.RecordingID, event.ParticipantIdentity),
		Category:         "recording",
		OriginalFileName: fmt.Sprintf("recording_%d_%s.ogg", event.RecordingID, event.ParticipantIdentity),
		S3URL:            fmt.Sprintf("https://%s%s", c.cfg.CloudFrontDomain, event.S3Path),
		FileSize:         event.FileSizeBytes,
		ContentType:      "audio/ogg",
		OwnerType:        "ROOM",
		OwnerID:          fmt.Sprintf("%d", event.RoomID),
	}

	if err := c.repo.Save(metadata); err != nil {
		log.Printf("Failed to save recording metadata: roomId=%d, err=%v", event.RoomID, err)
		return
	}

	log.Printf("Saved recording metadata: fileId=%d, roomId=%d", metadata.ID, event.RoomID)

	// recording-stored 이벤트 발행 → ai-service
	storedEvent := RecordingStoredEvent{
		FileID:              metadata.ID,
		RoomID:              event.RoomID,
		RecordingID:         event.RecordingID,
		S3Path:              event.S3Path,
		S3URL:               metadata.S3URL,
		FileSizeBytes:       event.FileSizeBytes,
		DurationSeconds:     event.DurationSeconds,
		ParticipantIdentity: event.ParticipantIdentity,
		Timestamp:           time.Now().Format(time.RFC3339),
	}

	storedPayload, err := json.Marshal(storedEvent)
	if err != nil {
		log.Printf("Failed to marshal RecordingStoredEvent: %v", err)
		return
	}

	writer := &kafka.Writer{
		Addr:     kafka.TCP(c.cfg.KafkaBrokers),
		Balancer: &kafka.LeastBytes{},
	}
	defer writer.Close()

	err = writer.WriteMessages(context.Background(), kafka.Message{
		Topic: recordingStoredTopic,
		Key:   []byte(fmt.Sprintf("%d", event.RoomID)),
		Value: storedPayload,
	})

	if err != nil {
		log.Printf("Failed to publish recording-stored event: %v", err)
	} else {
		log.Printf("Published recording-stored event: fileId=%d, roomId=%d", metadata.ID, event.RoomID)
	}
}
