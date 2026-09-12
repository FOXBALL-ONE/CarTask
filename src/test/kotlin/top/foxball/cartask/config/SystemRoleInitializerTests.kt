package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository

class SystemRoleInitializerTests {
    private val roleRepository = mock<RoleRepository>()

    /** 按角色编码返回预置角色，未预置的编码返回 null（模拟全新库）。 */
    private fun prepare(vararg roles: Role) {
        val byName = roles.associateBy { it.name.uppercase() }
        whenever(roleRepository.findByNameIgnoreCase(any())).thenAnswer { invocation ->
            byName[invocation.getArgument<String>(0).uppercase()]
        }
    }

    @Test
    fun `四个内置角色缺失时按显示名补齐`() {
        prepare()
        val captor = argumentCaptor<Role>()

        SystemRoleInitializer(roleRepository).write()

        verify(roleRepository, times(4)).save(captor.capture())
        assertEquals(
            mapOf(
                "SUPER_ADMIN" to "超级管理员",
                "ADMIN" to "平台管理",
                "DEPT_ADMIN" to "部门管理",
                "USER" to "普通用户",
            ),
            captor.allValues.associate { it.name to it.description },
        )
        assertTrue(captor.allValues.all { it.enabled })
    }

    @Test
    fun `已有角色不重建也不覆盖人工改过的显示名`() {
        prepare(
            Role().apply { name = "SUPER_ADMIN"; description = "超级管理员"; enabled = true },
            Role().apply { name = "ADMIN"; description = "自定义平台管理"; enabled = true },
            Role().apply { name = "DEPT_ADMIN"; description = "部门管理"; enabled = true },
            Role().apply { name = "USER"; description = "普通用户"; enabled = true },
        )

        SystemRoleInitializer(roleRepository).write()

        verify(roleRepository, never()).save(any())
    }

    @Test
    fun `已有角色显示名为空时补默认名`() {
        val user = Role().apply { name = "USER"; enabled = true }
        prepare(
            Role().apply { name = "SUPER_ADMIN"; description = "超级管理员"; enabled = true },
            Role().apply { name = "ADMIN"; description = "平台管理"; enabled = true },
            Role().apply { name = "DEPT_ADMIN"; description = "部门管理"; enabled = true },
            user,
        )

        SystemRoleInitializer(roleRepository).write()

        assertEquals("普通用户", user.description)
        verify(roleRepository).save(user)
    }
}
