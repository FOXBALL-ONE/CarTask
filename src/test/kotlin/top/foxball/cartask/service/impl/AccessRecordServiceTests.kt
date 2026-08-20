package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyNoInteractions
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.repository.AccessRecordRepository
import org.springframework.security.access.AccessDeniedException

class AccessRecordServiceTests {
    private val repository = mock<AccessRecordRepository>()
    private val service = AccessRecordServiceImpl(repository)

    @Test
    fun `进出流水不允许物理删除`() {
        assertThrows(AccessDeniedException::class.java) { service.delete(7) }
        assertThrows(AccessDeniedException::class.java) { service.deleteBatch(listOf(7)) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `进出流水普通更新必须走更正流程`() {
        assertThrows(AccessDeniedException::class.java) {
            service.update(7, AccessRecord())
        }
        assertThrows(AccessDeniedException::class.java) {
            service.updateBatch(listOf(AccessRecord()))
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `更正原因不能为空`() {
        val entity = AccessRecord().apply { id = 7 }
        assertThrows(IllegalArgumentException::class.java) {
            service.correct(7, entity, " ")
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `进出流水不能通过管理接口创建`() {
        assertThrows(AccessDeniedException::class.java) { service.create(AccessRecord()) }
        assertThrows(AccessDeniedException::class.java) { service.createBatch(listOf(AccessRecord())) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `已有设备放行渠道的流水不能再次人工放行`() {
        val record = AccessRecord().apply {
            id = 7
            carNumber = "A12345"
            releaseChannel = AccessRecord.ReleaseChannel.AUTOMATIC
        }
        whenever(repository.findById(7)).thenReturn(java.util.Optional.of(record))

        assertThrows(IllegalArgumentException::class.java) { service.release(7, "人工核验") }
    }
}
