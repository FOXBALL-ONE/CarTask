package top.foxball.cartask.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.repository.ParkingLotRepository
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/parking-lot")
@PreAuthorize("hasAuthority('dictionary:read')")
/** 停车场详情的查询接口，数据由停车区域同步任务维护。 */
class ParkingLotController(
    private val parkingLotRepository: ParkingLotRepository,
    private val properties: KeytopProperties,
    private val responseBuilder: ResponseBuilder,
) {
    /** 获取当前车场的详情；尚未同步时返回空数据。 */
    @GetMapping
    fun get(): ResponseEntity<Response> =
        responseBuilder.ok().data(parkingLotRepository.findByParkCode(properties.parkId.trim())).build()
}
