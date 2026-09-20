package top.foxball.cartask.service.impl

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.entity.Announcement
import top.foxball.cartask.repository.AnnouncementRepository
import top.foxball.cartask.service.AnnouncementService
import java.time.LocalDate

@Service
class AnnouncementServiceImpl(
    private val repository: AnnouncementRepository,
) : AnnouncementService {
    @Transactional(readOnly = true)
    override fun list(title: String?, publishedDate: LocalDate?, page: Int, pageSize: Int): Page<Announcement> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val specification = Specification<Announcement> { root, _, builder ->
            val predicates = mutableListOf<Predicate>()
            title?.trim()?.takeIf(String::isNotBlank)?.let {
                predicates += builder.like(builder.lower(root.get("title")), "%${it.lowercase()}%")
            }
            publishedDate?.let {
                predicates += builder.between(root.get("publishedAt"), it.atStartOfDay(), it.plusDays(1).atStartOfDay())
            }
            builder.and(*predicates.toTypedArray())
        }
        return repository.findAll(specification, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "publishedAt")))
    }

    @Transactional(readOnly = true)
    override fun get(id: Long): Announcement {
        require(id > 0) { "ID 必须大于 0" }
        return repository.findById(id).orElseThrow { IllegalArgumentException("通告不存在: $id") }
    }

    @Transactional
    override fun create(entity: Announcement): Announcement {
        require(entity.id == null) { "创建通告时不能指定 ID" }
        validate(entity)
        return repository.save(entity)
    }

    @Transactional
    override fun update(id: Long, entity: Announcement): Announcement {
        require(id > 0) { "ID 必须大于 0" }
        require(entity.id == null || entity.id == id) { "路径 ID 必须与请求体 ID 一致" }
        validate(entity)
        val current = get(id)
        current.title = entity.title.trim()
        current.content = entity.content.trim()
        current.publishedAt = entity.publishedAt
        return repository.save(current)
    }

    @Transactional
    override fun delete(id: Long) {
        repository.delete(get(id))
    }

    private fun validate(entity: Announcement) {
        require(entity.title.isNotBlank()) { "标题不能为空" }
        require(entity.title.length <= 160) { "标题不能超过 160 个字符" }
        require(entity.content.isNotBlank()) { "正文不能为空" }
    }
}
