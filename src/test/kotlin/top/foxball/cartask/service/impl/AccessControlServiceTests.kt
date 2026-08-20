package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.repository.AccessControlRepository
import java.time.LocalDateTime

class AccessControlServiceTests {
    private val repository = mock<AccessControlRepository>()
    private val service = AccessControlServiceImpl(repository)

    @Test
    fun `创建门禁授权强制进入待审核且未同步`() {
        val entity = AccessControl().apply {
            name = "张三"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
            synchronizedLoading = true
        }
        whenever(repository.save(entity)).thenReturn(entity)

        service.create(entity)

        assertEquals(AccessControl.ReviewStatus.PENDING, entity.reviewStatus)
        assertFalse(entity.synchronizedLoading)
    }

    @Test
    fun `已审核申请修改后回到待审核`() {
        val current = AccessControl().apply {
            id = 7
            name = "旧名称"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
            synchronizedLoading = true
        }
        val incoming = AccessControl().apply {
            id = 7
            name = "新名称"
        }
        whenever(repository.findById(7)).thenReturn(java.util.Optional.of(current))
        whenever(repository.save(current)).thenReturn(current)

        service.update(7, incoming)

        assertEquals(AccessControl.ReviewStatus.PENDING, current.reviewStatus)
        assertFalse(current.synchronizedLoading)
    }

    @Test
    fun `设备同步未接入时不能伪造已同步状态`() {
        val current = AccessControl().apply {
            id = 7
            name = "张三"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
        }
        whenever(repository.findById(7)).thenReturn(java.util.Optional.of(current))

        assertThrows(IllegalStateException::class.java) { service.synchronize(7) }
        assertFalse(current.synchronizedLoading)
    }

    @Test
    fun `授权结束时间必须晚于开始时间`() {
        val entity = AccessControl().apply {
            name = "张三"
            upTime = LocalDateTime.of(2026, 8, 20, 9, 0)
            endTime = upTime
        }

        assertThrows(IllegalArgumentException::class.java) { service.create(entity) }
    }

    @Test
    fun `门禁授权不允许物理删除`() {
        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { service.delete(7) }
        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { service.deleteBatch(listOf(7)) }
    }
}
