package service

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"com.onmeet.file/internal/config"
	"github.com/segmentio/kafka-go"
)

// EventProducer는 Kafka로 이벤트를 발행하는 인터페이스입니다.
type EventProducer interface {
	SendFileUploadEvent(ctx context.Context, topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error
	Close() error
}

type kafkaEventProducer struct {
	writer *kafka.Writer
}

func NewKafkaEventProducer(cfg *config.Config) EventProducer {
	writer := &kafka.Writer{
		Addr:     kafka.TCP(cfg.KafkaBrokers),
		Balancer: &kafka.LeastBytes{},
	}
	return &kafkaEventProducer{writer: writer}
}

func (p *kafkaEventProducer) SendFileUploadEvent(ctx context.Context, topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error {
	event := map[string]interface{}{
		"fileId":     fileId,
		"fileName":   fileName,
		"fileUrl":    fileUrl,
		"uploaderId": uploaderId,
		"timestamp":  time.Now().Format(time.RFC3339),
		"status":     "COMPLETED",
	}

	if correlationId != "" {
		event["correlationId"] = correlationId
	}

	payload, _ := json.Marshal(event)

	err := p.writer.WriteMessages(ctx, kafka.Message{
		Topic: topic,
		Value: payload,
	})

	if err != nil {
		log.Printf("Failed to send kafka event: %v", err)
	} else {
		log.Printf("Sent file upload event to topic %s: %s", topic, string(payload))
	}

	return err
}

func (p *kafkaEventProducer) Close() error {
	return p.writer.Close()
}
