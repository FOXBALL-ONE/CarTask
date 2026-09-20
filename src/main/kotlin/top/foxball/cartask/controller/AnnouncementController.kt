package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Announcement
import top.foxball.cartask.service.AnnouncementService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/announcements")
class AnnouncementController(
    private val service: AnnouncementService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/public")
    fun listPublic(): ResponseEntity<Response> {
        data class AnnouncementData(
            val id: Long,
            val title: String,
            val content: String,
            @param:JsonProperty("published_at") val publishedAt: String,
        )
        data class Response(val items: List<AnnouncementData>)

        val announcements = service.list(null, null, 1, 100).content
        val rs = Response(announcements.map {
            AnnouncementData(requireNotNull(it.id), it.title, it.content, it.publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        })
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun list(
        @RequestParam(required = false) title: String?,
        @RequestParam(name = "published_date", required = false) publishedDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): ResponseEntity<Response> {
        data class AnnouncementData(
            val id: Long,
            val title: String,
            val content: String,
            @param:JsonProperty("published_at") val publishedAt: String,
        )
        data class Response(val items: List<AnnouncementData>, val total: Long)

        val result = service.list(title, publishedDate, page, pageSize)
        val rs = Response(
            result.content.map {
                AnnouncementData(requireNotNull(it.id), it.title, it.content, it.publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            },
            result.totalElements,
        )
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val title: String,
            val content: String,
            @param:JsonProperty("published_at") val publishedAt: String,
        )

        val announcement = service.get(id)
        val rs = Response(requireNotNull(announcement.id), announcement.title, announcement.content, announcement.publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun create(@RequestBody announcement: Announcement): ResponseEntity<Response> {
        data class Response(val id: Long)

        val saved = service.create(announcement)
        val rs = Response(requireNotNull(saved.id))
        return responseBuilder.created().data(rs).build()
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun update(@PathVariable id: Long, @RequestBody announcement: Announcement): ResponseEntity<Response> {
        data class Response(val id: Long)

        val saved = service.update(id, announcement)
        val rs = Response(requireNotNull(saved.id))
        return responseBuilder.ok().data(rs).build()
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)

        service.delete(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
}
