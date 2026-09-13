package top.foxball.cartask.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 进度百分比是页面唯一的"还要等多久"依据，权重算错会让人以为卡住了或者快好了，所以把每个阶段锁住。
 */
class BackupProgressServiceTests {
    private val progress = BackupProgressService()

    @Test
    fun `空闲时是 0 且没有起止时间`() {
        val snapshot = progress.snapshot()

        assertEquals(BackupPhase.IDLE, snapshot.phase)
        assertEquals(0, snapshot.percent)
        assertNull(snapshot.startedAt)
        assertNull(snapshot.message)
    }

    @Test
    fun `统计范围阶段按已统计的表数在 0 到 5 之间推进`() {
        progress.startCounting()
        assertEquals(0, progress.snapshot().percent)

        progress.counting(1, 4)
        assertEquals(1, progress.snapshot().percent)

        progress.counting(4, 4)
        assertEquals(5, progress.snapshot().percent)
    }

    @Test
    fun `导出阶段按行数在 5 到 80 之间推进`() {
        progress.startCounting()
        progress.counting(2, 2)
        progress.startDumping(tablesTotal = 2, rowsTotal = 1000)

        assertEquals(5, progress.snapshot().percent)

        progress.dumping(tablesDone = 1, rowsDone = 500)
        assertEquals(42, progress.snapshot().percent)

        progress.dumping(tablesDone = 2, rowsDone = 1000)
        assertEquals(80, progress.snapshot().percent)
    }

    @Test
    fun `没有统计到行数时退回按表数推进，不至于卡在 5`() {
        progress.startCounting()
        progress.counting(2, 2)
        progress.startDumping(tablesTotal = 4, rowsTotal = 0)

        progress.dumping(tablesDone = 2, rowsDone = 0)

        assertEquals(42, progress.snapshot().percent)
    }

    @Test
    fun `打包阶段按附件数在 80 到 100 之间推进`() {
        progress.startArchiving(filesTotal = 4)

        assertEquals(80, progress.snapshot().percent)

        progress.archiving(3)
        assertEquals(95, progress.snapshot().percent)

        progress.archiving(4)
        assertEquals(100, progress.snapshot().percent)
    }

    @Test
    fun `没有附件时打包阶段直接到位`() {
        progress.startArchiving(filesTotal = 0)

        assertEquals(100, progress.snapshot().percent)
    }

    @Test
    fun `完成与失败都记时间，失败原因截断到 300 字`() {
        progress.startCounting()
        progress.fail("x".repeat(500))

        val failed = progress.snapshot()
        assertEquals(BackupPhase.FAILED, failed.phase)
        assertEquals(300, failed.message?.length)
        assertEquals(true, failed.finishedAt != null)

        progress.finish()
        assertEquals(BackupPhase.FINISHED, progress.snapshot().phase)
        assertEquals(100, progress.snapshot().percent)
    }
}
