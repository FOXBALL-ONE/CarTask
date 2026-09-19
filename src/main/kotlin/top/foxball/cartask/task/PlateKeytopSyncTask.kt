package top.foxball.cartask.task

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.cartask.service.PlateKeytopSyncService

@Component
class PlateKeytopSyncTask(
    private val plateKeytopSyncService: PlateKeytopSyncService,
) {
    @Scheduled(cron = "\${keytop.plate-sync-cron:*/30 * * * * *}")
    fun synchronize() {
        plateKeytopSyncService.processBatch()
    }

    @Scheduled(cron = "\${keytop.plate-reconcile-cron:0 0 * * * *}")
    fun reconcile() {
        plateKeytopSyncService.reconcile()
    }
}
