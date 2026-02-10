package org.monsing.record

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Component
class RecordUploader(
    private val s3Client: S3Client,
    private val s3UrlResolver: S3UrlResolver,
    @Value("\${s3.bucket}") private val bucket: String
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-")

    fun uploadRecord(file: MultipartFile): UploadResult {
        validateFile(file)

        val key = createKey(file.originalFilename)
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.contentType)
            .build()

        s3Client.putObject(request, RequestBody.fromBytes(file.bytes))
        val url = s3UrlResolver.getObjectUrl(key)

        return UploadResult(key, url)
    }

    private fun createKey(originalFileName: String?): String {
        val timestamp = LocalDateTime.now().format(dateTimeFormatter)
        return "records/$timestamp${originalFileName ?: "unknown"}"
    }

    private fun validateFile(file: MultipartFile) {
        require(file.size <= MAX_FILE_SIZE) {
            "파일 크기는 ${MAX_FILE_SIZE / (1024 * 1024)}MB를 초과할 수 없습니다"
        }
        val contentType = file.contentType
        require(contentType != null && contentType.startsWith("audio/")) {
            "녹음 파일(audio)만 업로드할 수 있습니다"
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024
    }
    
    data class UploadResult(
        val key: String,
        val url: String
    )
}
