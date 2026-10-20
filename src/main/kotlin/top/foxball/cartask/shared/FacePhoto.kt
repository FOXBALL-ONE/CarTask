package top.foxball.cartask.shared

import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.HexFormat

/** 同时验证文件扩展名、MIME 和签名，返回内容摘要。 */
object FacePhoto {
    fun requireValid(file: MultipartFile): String {
        require(!file.isEmpty) { "人脸照片不能为空" }
        require(file.size <= 2 * 1024 * 1024) { "人脸照片不能超过 2MB" }
        val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase()
        val mime = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> throw IllegalArgumentException("人脸照片格式只支持 JPEG、PNG、WEBP")
        }
        require(file.contentType?.lowercase() == mime) { "人脸照片 MIME 类型与扩展名不匹配" }
        val bytes = file.inputStream.use { it.readNBytes(2 * 1024 * 1024 + 1) }
        require(bytes.size.toLong() == file.size && bytes.size <= 2 * 1024 * 1024) { "人脸照片大小不正确" }
        val valid = when (mime) {
            "image/jpeg" -> bytes.size >= 4 && bytes[0] == (-1).toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == (-1).toByte()
            // PNG 的魔数是文件签名的第一层校验；尺寸和像素内容由上传存储服务处理。
            "image/png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
            else -> bytes.size >= 16 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        }
        require(valid) { "人脸照片内容不是受支持的图片" }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
