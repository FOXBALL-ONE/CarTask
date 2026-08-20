package top.foxball.cartask.service

import top.foxball.cartask.entity.Position
import org.springframework.data.domain.Page

/** 职位字典的业务服务。 */
interface PositionService {
    fun create(entity: Position): Position
    fun createBatch(entities: List<Position>): List<Position>
    fun get(id: Long): Position
    fun getBatch(ids: List<Long>): List<Position>
    fun list(page: Int, pageSize: Int): Page<Position>
    fun update(id: Long, entity: Position): Position
    fun updateBatch(entities: List<Position>): List<Position>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
