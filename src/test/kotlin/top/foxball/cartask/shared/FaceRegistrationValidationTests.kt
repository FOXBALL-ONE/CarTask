package top.foxball.cartask.shared

import org.springframework.mock.web.MockMultipartFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FaceRegistrationValidationTests {
    @Test
    fun `身份证归一化并校验日期和校验码`() {
        assertEquals("11010519491231002X", GatePersonFields.requireIdCard("11010519491231002x"))
        assertFailsWith<IllegalArgumentException> {
            GatePersonFields.requireIdCard("110101199001010006")
        }
        assertFailsWith<IllegalArgumentException> {
            GatePersonFields.requireIdCard("110101209901010005")
        }
    }

    @Test
    fun `照片同时校验扩展名 MIME 和文件签名`() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        )
        val file = MockMultipartFile("photo", "face.png", "image/png", png)
        assertEquals(64, FacePhoto.requireValid(file).length)
        assertFailsWith<IllegalArgumentException> {
            FacePhoto.requireValid(MockMultipartFile("photo", "face.jpg", "image/jpeg", png))
        }
    }
}
