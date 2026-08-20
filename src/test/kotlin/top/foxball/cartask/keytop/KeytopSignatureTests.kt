package top.foxball.cartask.keytop

import kotlin.test.Test
import kotlin.test.assertEquals

class KeytopSignatureTests {
    @Test
    fun `signs the request from the API document`() {
        val request = mapOf(
            "appId" to 12250,
            "parkId" to "591007282",
            "serviceCode" to "getCarCardList",
            "ts" to 1718000000000,
            "reqId" to "abc123",
            "pageIndex" to 1,
            "pageSize" to 100,
        )

        val signature = KeytopSignature.paramsSign(request, "3e3fc3c957dc43b58c299005dcb673b8")

        assertEquals("4FAFDB43EBB3DAFACD2ABD1A1E7D192C", signature)
    }

    @Test
    fun `excludes the fields and value types defined by the platform`() {
        val request = mapOf(
            "appId" to 12250,
            "key" to "old-signature",
            "empty" to "",
            "nullValue" to null,
            "array" to arrayOf(1, 2),
            "primitiveArray" to intArrayOf(1, 2),
            "iterable" to listOf(1, 2),
            "map" to mapOf("nested" to true),
            "enabled" to false,
        )

        val signature = KeytopSignature.paramsSign(request, "secret")

        assertEquals(md5("enabled=false&secret"), signature)
    }

    private fun md5(value: String): String = java.security.MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }
}
