package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.Announcement
import java.time.LocalDate

interface AnnouncementService {
    fun list(title: String?, publishedDate: LocalDate?, page: Int, pageSize: Int): Page<Announcement>
    fun get(id: Long): Announcement
    fun create(entity: Announcement): Announcement
    fun update(id: Long, entity: Announcement): Announcement
    fun delete(id: Long)
}
