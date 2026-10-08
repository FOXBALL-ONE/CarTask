package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class CommuteRouteStop {
    @JsonProperty("name")
    @Column(name = "stop_name", nullable = false, length = 120)
    var name: String = "发车"

    @JsonProperty("time")
    @Column(name = "arrival_time", nullable = false, length = 5)
    var time: String = ""

    @JsonProperty("season")
    @Column(name = "season", length = 20)
    var season: String? = null

    @JsonProperty("vehicle_count")
    @Column(name = "vehicle_count")
    var vehicleCount: Int? = null
}
