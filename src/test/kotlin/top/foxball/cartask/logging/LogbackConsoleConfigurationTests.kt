package top.foxball.cartask.logging

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class LogbackConsoleConfigurationTests {
    @Test
    fun `控制台使用ANSI彩色PatternLayout且文件仍使用JSON布局`() {
        val xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"))

        assertTrue("<appender name=\"CONSOLE\" class=\"ch.qos.logback.core.ConsoleAppender\">" in xml)
        assertTrue("ch.qos.logback.classic.encoder.PatternLayoutEncoder" in xml)
        assertTrue("%highlight(%-5level)" in xml)
        assertTrue("top.foxball.cartask.logging.JsonLogLayout" in xml)
    }
}
