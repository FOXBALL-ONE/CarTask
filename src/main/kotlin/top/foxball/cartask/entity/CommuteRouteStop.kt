package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class CommuteRouteStop {
    @JsonProperty("name")
    @Column(name = "stop_name", nullable = false, length = 120)
    lateinit var name: String

    @JsonProperty("time")
    @Column(name = "arrival_time", nullable = false, length = 5)
    lateinit var time: String
}
