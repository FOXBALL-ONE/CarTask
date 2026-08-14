package top.foxball.cartask

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CarTaskApplication

fun main(args: Array<String>) {
    runApplication<CarTaskApplication>(*args)
}
