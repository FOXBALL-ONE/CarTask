package top.foxball.cartask.service.impl

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.entity.CommuteRoute
import top.foxball.cartask.repository.CommuteRouteRepository
import top.foxball.cartask.service.CommuteRouteService

@Service
class CommuteRouteServiceImpl(
    private val repository: CommuteRouteRepository,
) : CommuteRouteService {
    @Transactional(readOnly = true)
    override fun list(keyword: String?, page: Int, pageSize: Int): Page<CommuteRoute> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val specification = Specification<CommuteRoute> { root, _, builder ->
            val predicates = mutableListOf<Predicate>()
            keyword?.trim()?.takeIf(String::isNotBlank)?.let {
                val match = "%${it.lowercase()}%"
                predicates += builder.or(
                    builder.like(builder.lower(root.get("routeName")), match),
                    builder.like(builder.lower(root.get("startAddress")), match),
                    builder.like(builder.lower(root.get("endAddress")), match),
                )
            }
            builder.and(*predicates.toTypedArray())
        }
        return repository.findAll(specification, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "routeName")))
    }

    @Transactional(readOnly = true)
    override fun get(id: Long): CommuteRoute {
        require(id > 0) { "ID 必须大于 0" }
        return repository.findById(id).orElseThrow { IllegalArgumentException("通勤路线不存在: $id") }
    }

    @Transactional
    override fun create(entity: CommuteRoute): CommuteRoute {
        require(entity.id == null) { "创建路线时不能指定 ID" }
        validate(entity)
        return repository.save(entity)
    }

    @Transactional
    override fun update(id: Long, entity: CommuteRoute): CommuteRoute {
        require(id > 0) { "ID 必须大于 0" }
        require(entity.id == null || entity.id == id) { "路径 ID 必须与请求体 ID 一致" }
        validate(entity)
        val current = get(id)
        current.routeName = entity.routeName.trim()
        current.startAddress = entity.startAddress.trim()
        current.endAddress = entity.endAddress.trim()
        current.routeStops = entity.routeStops.toMutableList()
        current.remark = entity.remark?.trim()?.takeIf(String::isNotBlank)
        return repository.save(current)
    }

    @Transactional
    override fun delete(id: Long) {
        repository.delete(get(id))
    }

    private fun validate(entity: CommuteRoute) {
        require(entity.routeName.isNotBlank()) { "路线名称不能为空" }
        require(entity.routeName.length <= 120) { "路线名称不能超过 120 个字符" }
        require(entity.startAddress.isNotBlank()) { "起始地址不能为空" }
        require(entity.endAddress.isNotBlank()) { "终点站不能为空" }
        require(entity.routeStops.isNotEmpty()) { "请至少添加一个站点" }
        require(entity.routeStops.size <= 50) { "站点不能超过 50 个" }
        entity.routeStops.forEach {
            require(it.name.isNotBlank()) { "站点名称不能为空" }
            require(it.time.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))) { "站点时间必须为 HH:mm" }
        }
    }
}
