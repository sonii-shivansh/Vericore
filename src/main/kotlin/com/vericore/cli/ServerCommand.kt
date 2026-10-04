package com.vericore.cli

import com.vericore.server.module
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.vericore.core.exceptions.ConfigurationException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Starts the local Vericore HTTP API. */
class ServerCommand :
    CliktCommand(name = "server", help = "Start Vericore as a local API server") {
    private val host by option("-h", "--host", help = "Host to bind to").default("127.0.0.1")
    private val port by option("-p", "--port", help = "Port to listen on").int().default(8080)

    override fun run() {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        validateHost(host)
        echo("🌍 Starting Vericore Server on http://$host:$port")
        try {
            embeddedServer(Netty, host = host, port = port, module = Application::module).start(wait = true)
        } catch (e: UnresolvedAddressException) {
            throw ConfigurationException("Unable to bind server to host '$host': the address could not be resolved.", e)
        } catch (e: UnknownHostException) {
            throw ConfigurationException("Unable to bind server to host '$host': the address could not be resolved.", e)
        }
    }

    private fun validateHost(value: String) {
        require(value.isNotBlank()) { "Host must not be blank" }
        runCatching {
            InetAddress.getByName(value)
        }.getOrElse {
            throw ConfigurationException("Unable to resolve server host '$value'.")
        }
    }
}
