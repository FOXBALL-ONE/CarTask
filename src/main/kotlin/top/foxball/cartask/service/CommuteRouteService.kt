package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.CommuteRoute

interface CommuteRouteService {
    fun list(keyword: String?, page: Int, pageSize: Int): Page<CommuteRoute>
    fun get(id: Long): CommuteRoute
    fun create(entity: CommuteRoute): CommuteRoute
    fun update(id: Long, entity: CommuteRoute): CommuteRoute
    fun delete(id: Long)
}
