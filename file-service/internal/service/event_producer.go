package service

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"com.onmeet.file/internal/config"
	"github.com/segmentio/kafka-go"
)

type EventProducer interface {
	SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error
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

func (p *kafkaEventProducer) SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error {
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

	err := p.writer.WriteMessages(context.Background(), kafka.Message{
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
