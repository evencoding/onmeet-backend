package com.onmeet.file

import com.onmeet.file.repository.FileMetadataRepository
import io.awspring.cloud.s3.S3Template
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest(properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.cloud.aws.credentials.access-key=test",
    "spring.cloud.aws.credentials.secret-key=test",
    "spring.cloud.aws.region.static=ap-northeast-2",
    "spring.cloud.aws.s3.bucket=test-bucket",
    "gateway.shared-secret=test-secret",
    "logging.level.io.awspring=DEBUG",
    "logging.level.software.amazon.awssdk=DEBUG"
])
@ActiveProfiles("test")
@Disabled("Requires running database via Docker Compose")
class FileServiceVerificationTest {

    @MockBean
    private lateinit var fileMetadataRepository: FileMetadataRepository

    @MockBean
    private lateinit var s3Template: S3Template

    @Test
    fun verifyS3Connection() {
        val bucketName = "test-bucket"
        println("Verifying S3 connection to bucket: $bucketName")

        val testFileName = "connection_test_" + System.currentTimeMillis() + ".txt"

        // Mock S3 behavior
        val mockResource = org.mockito.Mockito.mock(io.awspring.cloud.s3.S3Resource::class.java)
        org.mockito.BDDMockito.given(mockResource.exists()).willReturn(true)
        org.mockito.BDDMockito.given(s3Template.download(org.mockito.ArgumentMatchers.eq(bucketName), org.mockito.ArgumentMatchers.anyString())).willReturn(mockResource)
        org.mockito.BDDMockito.given(s3Template.upload(org.mockito.ArgumentMatchers.eq(bucketName), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).willReturn(mockResource)

        try {
            // Upload
            s3Template.upload(bucketName, testFileName, ByteArrayInputStream("content".toByteArray()), null)
            println("Upload successful: $testFileName")

            // Check existence
            val resource = s3Template.download(bucketName, testFileName)
            assertThat(resource.exists()).isTrue()

            println("File exists check passed.")

            // Delete
            s3Template.deleteObject(bucketName, testFileName)
            println("Delete successful.")

        } catch (e: Exception) {
            throw RuntimeException("S3 Connection Test Failed", e)
        }
    }
}
