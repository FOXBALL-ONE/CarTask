package top.foxball.cartask.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.entity.SyncCheckpoint
import top.foxball.cartask.repository.SyncCheckpointRepository


@Service
/**
 * 同步检查点的读写入口。
 *
 * 单独抽出来只为一件事——事务边界。SynCarCapInfoTask 的入口方法刻意不包 @Transactional：
 * 内部要分页调科拓接口、逐条下载抓拍图片，外层事务会把数据库连接占住几十秒到几分钟，
 * 把登录等请求一起饿死。而 findBySyncKey 带 PESSIMISTIC_WRITE 悲观锁，JPA 要求锁查询必须
 * 跑在事务里，没有事务直接抛 TransactionRequiredException。
 *
 * 于是把检查点的每次读写都收敛到这里，各自开一个独立的短事务：慢 IO 仍在事务外，
 * 锁查询也有自己的事务。代价是锁只覆盖单次读写、不再横跨整个同步过程，进程内的并发
 * 仍由 SynCarCapInfoTask 的 executionLock 挡住。
 */
class SyncCheckpointService(
    private val repository: SyncCheckpointRepository,
) {


    /** loadOrCreate：取出检查点并加悲观写锁，不存在时返回尚未持久化的新实例；锁随本方法的事务释放。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun loadOrCreate(syncKey: String): SyncCheckpoint =
        repository.findBySyncKey(syncKey) ?: SyncCheckpoint().apply { this.syncKey = syncKey }


    /** find：不加锁地读取检查点，用于预检这类只看不改的场景。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun find(syncKey: String): SyncCheckpoint? = repository.findFirstBySyncKey(syncKey)


    /**
     * save：保存检查点。
     *
     * 首次保存时 JPA 的 persist 会把自增主键回填到入参对象上，后续再保存会走 merge，
     * 因此调用方不需要接返回值。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(checkpoint: SyncCheckpoint) {
        repository.save(checkpoint)
    }
}
