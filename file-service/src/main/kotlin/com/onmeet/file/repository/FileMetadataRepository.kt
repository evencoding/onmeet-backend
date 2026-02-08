package com.onmeet.file.repository

import com.onmeet.file.entity.FileMetadata
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FileMetadataRepository : JpaRepository<FileMetadata, Long> {
    fun findByFileName(fileName: String): Optional<FileMetadata>
}
