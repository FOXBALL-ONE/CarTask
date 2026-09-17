package top.foxball.cartask.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder

@Service
class GeoIpService(
    @Value("\${amap.api.key:}") private val amapApiKey: String
) {
    private val ipCache = mutableMapOf<String, String?>()

    fun getLocation(ip: String?): String? {
        if (ip.isNullOrBlank()) return null
        if (isPrivateNetwork(ip)) return "内网"
        if (amapApiKey.isBlank()) return null

        return ipCache.getOrPut(ip) {
            try {
                queryGeoIp(ip)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun queryGeoIp(ip: String): String? {
        return try {
            println(ip)
            val encodedIp = URLEncoder.encode(ip, "UTF-8")
            val url = URL("https://restapi.amap.com/v3/ip?ip=$encodedIp&output=json&key=$amapApiKey")
            val connection = url.openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                val response = reader.readText()
                parseLocation(response)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLocation(json: String): String? {
        return try {
            
            val provinceStart = json.indexOf("\"province\":\"") + 12
            val provinceEnd = json.indexOf("\"", provinceStart)
            val cityStart = json.indexOf("\"city\":\"") + 8
            val cityEnd = json.indexOf("\"", cityStart)

            if (provinceStart < 12 || provinceEnd < 0 || cityStart < 8 || cityEnd < 0) return null

            val province = json.substring(provinceStart, provinceEnd)
            val city = json.substring(cityStart, cityEnd)

            if (province.isNotEmpty() && city.isNotEmpty() && province != "XX" && city != "XX") {
                "$province$city"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isPrivateNetwork(ip: String): Boolean {
        println(ip)
        return ip == "127.0.0.1" || ip == "::1" || ip.startsWith("10.") ||
                ip.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(ip)
    }
}
