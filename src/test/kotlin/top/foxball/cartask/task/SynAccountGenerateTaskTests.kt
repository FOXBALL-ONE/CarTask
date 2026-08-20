package top.foxball.cartask.task

import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.service.UserService
import kotlin.test.assertEquals

class SynAccountGenerateTaskTests {
    private val userService = mock<UserService>()
    private val carMasterInfoService = mock<CarMasterInfoService>()
    private val task = SynAccountGenerateTask(userService, carMasterInfoService)

    @Test
    fun `每天上海时区凌晨三点执行`() {
        val scheduled = SynAccountGenerateTask::class.java
            .getDeclaredMethod("synAccountGenerate")
            .getAnnotation(Scheduled::class.java)

        assertEquals("0 0 3 * * *", scheduled.cron)
        assertEquals("Asia/Shanghai", scheduled.zone)
    }

    @Test
    fun `为新手机号创建平台账户`() {
        val carMasterInfo = CarMasterInfo().apply {
            id = 1L
            carMasterName = "张三"
            carMasterPhone = " 13800138000 "
        }
        whenever(carMasterInfoService.getAllList()).thenReturn(listOf(carMasterInfo))
        whenever(userService.findExistingUsernames(setOf("13800138000"))).thenReturn(emptySet())
        val command = argumentCaptor<UserService.CreateCommand>()

        task.synAccountGenerate()

        verify(userService).create(command.capture())
        assertEquals("13800138000", command.firstValue.username)
        assertEquals("13800138000", command.firstValue.phone)
        assertEquals("13800138000@auto.local", command.firstValue.email)
        assertEquals("Fqjg20221022", command.firstValue.credential)
        assertEquals("张三", command.firstValue.nickName)
        assertEquals(229L, command.firstValue.departmentId)
    }

    @Test
    fun `跳过已有账户和空手机号`() {
        val existing = CarMasterInfo().apply {
            id = 1L
            carMasterName = "张三"
            carMasterPhone = "13800138000"
        }
        val noPhone = CarMasterInfo().apply {
            id = 2L
            carMasterName = "李四"
            carMasterPhone = " "
        }
        whenever(carMasterInfoService.getAllList()).thenReturn(listOf(existing, noPhone))
        whenever(userService.findExistingUsernames(setOf("13800138000"))).thenReturn(setOf("13800138000"))

        task.synAccountGenerate()

        verify(userService, never()).create(org.mockito.kotlin.any())
    }

    @Test
    fun `同一手机号只处理一次且单条失败不影响后续账户`() {
        val failed = CarMasterInfo().apply {
            id = 1L
            carMasterName = "张三"
            carMasterPhone = "phone-failed"
        }
        val duplicate = CarMasterInfo().apply {
            id = 2L
            carMasterName = "张三"
            carMasterPhone = " phone-failed "
        }
        val successful = CarMasterInfo().apply {
            id = 3L
            carMasterName = " 李四 "
            carMasterPhone = "phone-success"
        }
        whenever(carMasterInfoService.getAllList()).thenReturn(listOf(failed, duplicate, successful))
        whenever(userService.findExistingUsernames(setOf("phone-failed", "phone-success"))).thenReturn(setOf("phone-failed"))
        val command = argumentCaptor<UserService.CreateCommand>()

        task.synAccountGenerate()

        verify(userService).findExistingUsernames(setOf("phone-failed", "phone-success"))
        verify(userService).create(command.capture())
        assertEquals("phone-success", command.firstValue.username)
        assertEquals("李四", command.firstValue.nickName)
    }
}
