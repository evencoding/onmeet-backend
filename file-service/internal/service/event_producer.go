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
	SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error
}

type kafkaEventProducer struct {
	writer *kafka.Writer
}

func NewKafkaEventProducer(cfg *config.Config) EventProducer {
	// &kafka.Writer{} 처럼 구조체 주소를 직접 생성 및 할당합니다.
	writer := &kafka.Writer{
		Addr:     kafka.TCP(cfg.KafkaBrokers),
		Balancer: &kafka.LeastBytes{},
	}
	return &kafkaEventProducer{writer: writer}
}

func (p *kafkaEventProducer) SendFileUploadEvent(topic string, fileId uint, fileName, fileUrl string, uploaderId int64, correlationId string) error {
	// map[string]interface{}는 Java의 Map<String, Object>와 같습니다.
	// interface{}는 모든 타입을 받을 수 있는 '최상위 타입'입니다.
	event := map[string]interface{}{
		"fileId":     fileId,
		"fileName":   fileName,
		"fileUrl":    fileUrl,
		"uploaderId": uploaderId,
		"timestamp":  time.Now().Format(time.RFC3339),
		"status":     "COMPLETED",
	}

	// 조건문에 괄호()를 쓰지 않는 것이 Go의 특징입니다.
	if correlationId != "" {
		event["correlationId"] = correlationId
	}

	// json.Marshal은 객체를 JSON 바이트 배열로 변환합니다. (Jackson의 writeValueAsBytes와 유사)
	// 두 번째 리턴값(error)은 필요 없어서 _로 무시했습니다.
	payload, _ := json.Marshal(event)

	// Kafka로 메시지를 전송합니다. context.Background()는 가장 기본적인 루트 컨텍스트입니다.
	err := p.writer.WriteMessages(context.Background(), kafka.Message{
		Topic: topic,
		Value: payload,
	})

	if err != nil {
		// %v는 어떤 타입이든 출력해주는 범용 포맷팅 지시자입니다.
		log.Printf("Failed to send kafka event: %v", err)
	} else {
		log.Printf("Sent file upload event to topic %s: %s", topic, string(payload))
	}

	return err
}
