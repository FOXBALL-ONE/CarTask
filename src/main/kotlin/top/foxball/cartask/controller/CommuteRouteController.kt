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
import top.foxball.cartask.entity.CommuteRoute
import top.foxball.cartask.service.CommuteRouteService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/commute-routes")
class CommuteRouteController(
    private val service: CommuteRouteService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): ResponseEntity<Response> {
        data class StopData(val name: String, val time: String)
        data class RouteData(
            val id: Long,
            @param:JsonProperty("route_name") val routeName: String,
            @param:JsonProperty("start_address") val startAddress: String,
            @param:JsonProperty("end_address") val endAddress: String,
            @param:JsonProperty("route_stops") val routeStops: List<StopData>,
            val remark: String?,
        )
        data class Response(val items: List<RouteData>, val total: Long)

        val result = service.list(keyword, page, pageSize)
        val rs = Response(
            result.content.map {
                RouteData(
                    requireNotNull(it.id), it.routeName, it.startAddress, it.endAddress,
                    it.routeStops.map { stop -> StopData(stop.name, stop.time) }, it.remark,
                )
            },
            result.totalElements,
        )
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/public")
    fun listPublic(): ResponseEntity<Response> {
        data class StopData(val name: String, val time: String)
        data class RouteData(
            val id: Long,
            @param:JsonProperty("route_name") val routeName: String,
            @param:JsonProperty("start_address") val startAddress: String,
            @param:JsonProperty("end_address") val endAddress: String,
            @param:JsonProperty("route_stops") val routeStops: List<StopData>,
            val remark: String?,
        )
        data class Response(val items: List<RouteData>)

        val routes = service.list(null, 1, 100).content
        val rs = Response(routes.map {
            RouteData(
                requireNotNull(it.id), it.routeName, it.startAddress, it.endAddress,
                it.routeStops.map { stop -> StopData(stop.name, stop.time) }, it.remark,
            )
        })
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class StopData(val name: String, val time: String)
        data class Response(
            val id: Long,
            @param:JsonProperty("route_name") val routeName: String,
            @param:JsonProperty("start_address") val startAddress: String,
            @param:JsonProperty("end_address") val endAddress: String,
            @param:JsonProperty("route_stops") val routeStops: List<StopData>,
            val remark: String?,
        )

        val route = service.get(id)
        val rs = Response(
            requireNotNull(route.id), route.routeName, route.startAddress, route.endAddress,
            route.routeStops.map { StopData(it.name, it.time) }, route.remark,
        )
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun create(@RequestBody route: CommuteRoute): ResponseEntity<Response> {
        data class Response(val id: Long)

        val saved = service.create(route)
        val rs = Response(requireNotNull(saved.id))
        return responseBuilder.created().data(rs).build()
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    fun update(@PathVariable id: Long, @RequestBody route: CommuteRoute): ResponseEntity<Response> {
        data class Response(val id: Long)

        val saved = service.update(id, route)
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
