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

/** class ParkingLotController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class ParkingLotController(
    private val parkingLotRepository: ParkingLotRepository,
    private val properties: KeytopProperties,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(): ResponseEntity<Response> =
        responseBuilder.ok().data(parkingLotRepository.findByParkCode(properties.parkId.trim())).build()
}
