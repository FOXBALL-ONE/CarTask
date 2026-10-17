package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/wx/car-owners")
class WxCarOwnerController(
    private val responseBuilder: ResponseBuilder,
    private val ownerRepository: ParkingOwnerRepository,
    private val plateRepository: ParkingPlateRepository,
    private val spotRepository: ParkingSpotRepository,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
) {
    @GetMapping
    @PreAuthorize("hasRole('USER') or hasAuthority('owner:read')")
    fun listOwners(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "12") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..50) { "每页数量必须在 1 到 50 之间" }

        data class OwnerData(
            val id: Long,
            val name: String,
            val dept: String,
            val phone: String,
            @param:JsonProperty("card_id") val cardId: String,
            @param:JsonProperty("plate_count") val plateCount: Int,
            @param:JsonProperty("spot_count") val spotCount: Int,
            val status: Int,
        )
        data class Response(val items: List<OwnerData>, val total: Int, val page: Int, @param:JsonProperty("page_size") val pageSizeValue: Int)

        val scope = dataScopeResolver.forPrincipal(principal)
        val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
        val filtered = ownerRepository.findAll().filter { owner ->
            (visibleOwnerIds == null || requireNotNull(owner.id) in visibleOwnerIds) &&
                (keyword.isNullOrBlank() || owner.name.contains(keyword, true) || owner.phone.contains(keyword, true) || owner.cardId.contains(keyword, true))
        }.sortedBy { it.id }
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val items = filtered.subList(from, (from + pageSize).coerceAtMost(filtered.size)).map {
            OwnerData(requireNotNull(it.id), it.name, it.dept, it.phone, it.cardId, it.plateCount, it.spotCount, it.status)
        }
        val rs = Response(items, filtered.size, page, pageSize)
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasAuthority('owner:read')")
    fun getOwner(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Response> {
        val scope = dataScopeResolver.forPrincipal(principal)
        val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
        val owner = ownerRepository.findById(id).orElseThrow { IllegalArgumentException("车主不存在") }
        require(visibleOwnerIds == null || id in visibleOwnerIds) { "车主不存在" }

        data class PlateData(
            val plate: String,
            @param:JsonProperty("car_brand") val carBrand: String,
            val status: Int,
            @param:JsonProperty("reg_date") val regDate: String,
            @param:JsonProperty("inspection_valid_until") val inspectionValidUntil: String?,
        )
        data class SpotData(val code: String, val area: String, val type: String, val status: Int, val remark: String?)
        data class Response(
            val id: Long,
            val name: String,
            val dept: String,
            val phone: String,
            @param:JsonProperty("card_id") val cardId: String,
            val balance: String,
            val status: Int,
            val plates: List<PlateData>,
            val spots: List<SpotData>,
        )

        val plates = plateRepository.findAll().filter { it.ownerId == id }.sortedBy { it.id }.map {
            PlateData(it.plate, it.carBrand, it.status, it.regDate.toString(), it.inspectionValidUntil?.toString())
        }
        val spots = spotRepository.findAll().filter {
            it.ownerCode == owner.cardId || (it.ownerCode.isNullOrBlank() && it.owner == owner.name)
        }.sortedBy { it.id }.map {
            SpotData(it.code, it.area, it.type, it.status, it.remark)
        }
        val rs = Response(requireNotNull(owner.id), owner.name, owner.dept, owner.phone, owner.cardId, owner.balance.toPlainString(), owner.status, plates, spots)
        return responseBuilder.ok().data(rs).build()
    }
}
