package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "commute_route")
class CommuteRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @JsonProperty("route_name")
    @Column(name = "route_name", nullable = false, length = 120)
    lateinit var routeName: String

    @JsonProperty("start_address")
    @Column(name = "start_address", nullable = false, length = 255)
    lateinit var startAddress: String

    @JsonProperty("end_address")
    @Column(name = "end_address", nullable = false, length = 255)
    lateinit var endAddress: String

    @JsonProperty("route_stops")
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commute_route_stop", joinColumns = [JoinColumn(name = "route_id")])
    @OrderColumn(name = "display_order")
    var routeStops: MutableList<CommuteRouteStop> = mutableListOf()

    @Column(length = 500)
    var remark: String? = null

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
