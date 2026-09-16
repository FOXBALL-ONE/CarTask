package top.foxball.cartask.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.JwtAuthenticationException
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.User
import top.foxball.cartask.entity.UserManagedDepartment
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository
import java.util.Optional









class WorkingDepartmentServiceTests {
    private val departmentRepository = mock<DepartmentRepository>()
    private val managedRepository = mock<UserManagedDepartmentRepository>()
    private val userRepository = mock<UserRepository>()
    private val sessionRepository = mock<RedisTokenSessionRepository>()

    private val service = WorkingDepartmentService(
        departmentRepository, managedRepository, userRepository, sessionRepository,
    )

    private fun admin(tokenId: String = "token-1") = CurrentUserPrincipal(
        userId = 690, username = "t_admin", role = "ADMIN", tokenId = tokenId,
    )

    private fun department(id: Long, name: String = "部门$id") = Department().apply {
        this.id = id
        this.name = name
        departmentNumber = "D$id"
        sortOrder = 0
    }

    @Test
    fun `会话写不进去时切换必须失败而不是假装成功`() {
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L)))
        whenever(sessionRepository.updateWorkingDepartment("token-1", 1L)).thenReturn(false)

        assertThrows(JwtAuthenticationException::class.java) { service.switchTo(admin(), 1L) }
    }

    @Test
    fun `写成功时返回的工作部门就是切换后的那个`() {
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L)))
        whenever(departmentRepository.findAll()).thenReturn(listOf(department(1L), department(2L)))
        whenever(sessionRepository.updateWorkingDepartment("token-1", 1L)).thenReturn(true)

        val state = service.switchTo(admin(), 1L)

        assertEquals(1L, state.currentId)
        assertEquals("DEPARTMENT", state.scope)
        verify(sessionRepository).updateWorkingDepartment("token-1", 1L)
    }

    @Test
    fun `切回全部时也要写到会话`() {
        whenever(departmentRepository.findAll()).thenReturn(listOf(department(1L)))
        whenever(sessionRepository.updateWorkingDepartment("token-1", null)).thenReturn(true)

        val state = service.switchTo(admin(), null)

        assertEquals(null, state.currentId)
        assertEquals("ALL", state.scope)
        verify(sessionRepository).updateWorkingDepartment("token-1", null)
    }

    @Test
    fun `普通用户没有工作部门`() {
        val user = CurrentUserPrincipal(userId = 2, username = "u1", role = "USER", tokenId = "t")

        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            service.switchTo(user, 1L)
        }
    }





    @Test
    fun `重复提交同一份管理范围时先落删除再插入`() {
        val user = User().apply { id = 692L }
        whenever(userRepository.findById(692L)).thenReturn(Optional.of(user))
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L)))
        whenever(managedRepository.findByUserId(692L)).thenReturn(emptyList())

        service.replaceManagedDepartments(692L, listOf(ManagedDepartmentInput(1L, false)))

        inOrder(managedRepository) {
            verify(managedRepository).deleteByUserId(692L)
            verify(managedRepository).flush()
            verify(managedRepository).saveAll(any<Iterable<UserManagedDepartment>>())
        }
    }
}
