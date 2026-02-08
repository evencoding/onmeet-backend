package com.onmeet.file.service

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FileEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(FileEventProducer::class.java)
    fun sendFileUploadEvent(topic: String, fileId: Long, fileName: String, fileUrl: String, uploaderId: Long, correlationId: String?) {
        val event = mutableMapOf<String, Any>(
            "fileId" to fileId,
            "fileName" to fileName,
            "fileUrl" to fileUrl,
            "uploaderId" to uploaderId,
            "timestamp" to LocalDateTime.now().toString(),
            "status" to "COMPLETED"
        )
        correlationId?.let { event["correlationId"] = it }
        
        log.info("Sending file upload event to topic {}: {}", topic, event)
        kafkaTemplate.send(topic, event)
    }
}
