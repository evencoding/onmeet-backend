package com.onmeet.file.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "file_metadata")
@EntityListeners(AuditingEntityListener::class)
class FileMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "file_name", nullable = false)
    var fileName: String,

    @Column(name = "original_file_name", nullable = false)
    var originalFileName: String,

    @Column(name = "s3_url", nullable = false)
    var s3Url: String,

    @Column(name = "file_size", nullable = false)
    var fileSize: Long,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Column(name = "uploader_id", nullable = false)
    var uploaderId: Long,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    fun updateMetadata(fileName: String, s3Url: String, fileSize: Long, contentType: String) {
        this.fileName = fileName
        this.s3Url = s3Url
        this.fileSize = fileSize
        this.contentType = contentType
    }
}
