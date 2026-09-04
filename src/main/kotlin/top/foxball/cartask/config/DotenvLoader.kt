package top.foxball.cartask.config

import java.nio.file.Files
import java.nio.file.Path
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/** Loads optional dotenv key/value pairs without overriding process environment values. */
object DotenvLoader {
    fun addTo(environment: ConfigurableEnvironment, path: Path) {
        if (!Files.exists(path)) return
        val values = Files.readAllLines(path).asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("export ")) it.removePrefix("export ").trim() else it }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val raw = line.substring(index + 1).trim()
                val value = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') raw.substring(1, raw.length - 1) else raw
                key to value
            }.toMap()
        if (values.isNotEmpty()) environment.propertySources.addLast(MapPropertySource("dotenv", values))
    }
}
