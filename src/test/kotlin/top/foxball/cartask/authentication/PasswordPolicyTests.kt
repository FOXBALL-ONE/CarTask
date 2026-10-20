package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import top.foxball.cartask.shared.InitialCredentials

class PasswordPolicyTests {
    @Test
    fun `八位密码包含任意三类或四类字符均通过`() {
        listOf("Abcdefg1", "Abcdefg!", "ABCDEF1!", "abcdef1!", "Abcdef1!").forEach { password ->
            assertNull(PasswordPolicy.errorMessage(password), password)
        }
    }

    @Test
    fun `不足八位或不足三类字符均拒绝`() {
        listOf(
            "", "Abcde1!", "abcdefgh", "ABCDEFGH", "12345678", "!!!!!!!!",
            "Abcdefgh", "abcdef12", "abcdef!!", "ABCDEF12", "ABCDEF!!", "123456!!",
        ).forEach { password ->
            assertNotNull(PasswordPolicy.errorMessage(password), password)
        }
    }

    @Test
    fun `空白字符不能充当特殊符号且不允许出现在密码中`() {
        listOf("Abcdef1 ", " Abcdef1!", "Abcd ef1!", "Abcdef1\t", "Abcdef1\n").forEach { password ->
            assertEquals("密码不能包含空白字符", PasswordPolicy.errorMessage(password), password)
        }
    }

    @Test
    fun `系统发放的初始密码满足全局策略`() {
        assertNull(PasswordPolicy.errorMessage(InitialCredentials.PASSWORD))
    }
}
