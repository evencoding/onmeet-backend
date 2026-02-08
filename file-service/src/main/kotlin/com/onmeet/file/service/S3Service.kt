package com.onmeet.file.service

import io.awspring.cloud.s3.ObjectMetadata
import io.awspring.cloud.s3.S3Template
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class S3Service(
    private val s3Template: S3Template,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucket: String
) {
    private val log = LoggerFactory.getLogger(S3Service::class.java)

    fun uploadFile(key: String, inputStream: InputStream, contentType: String): String {
        log.info("Uploading file to S3: {}", key)
        val resource = s3Template.upload(
            bucket, key, inputStream,
            ObjectMetadata.builder().contentType(contentType).build()
        )

        return try {
            resource.url.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to get S3 URL", e)
        }
    }

    fun deleteFile(fileName: String) {
        log.info("Deleting file from S3: {}", fileName)
        s3Template.deleteObject(bucket, fileName)
    }

    fun getFileUrl(fileName: String): String {
        return try {
            s3Template.download(bucket, fileName).url.toString()
        } catch (e: java.io.IOException) {
            throw RuntimeException("Failed to get S3 URL for file: $fileName", e)
        }
    }
}
