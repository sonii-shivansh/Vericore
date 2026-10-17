package com.vericore.cli

import com.vericore.server.module
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Starts the local Vericore HTTP API. Remote binding is an explicitly unsafe opt-in. */
class ServerCommand :
    CliktCommand(name = "server", help = "Start the Vericore HTTP API (loopback by default)") {
    private val host by option("-h", "--host", help = "Host to bind to (loopback by default)").default("127.0.0.1")
    private val port by option("-p", "--port", help = "Port to listen on").int().default(8080)
    private val allowRemoteHost by option(
        "--allow-remote-host",
        help = "Explicitly allow a non-loopback bind; use only behind a trusted authentication/TLS boundary"
    ).flag(default = false)

    override fun run() {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        val loopback = validateServerBindHost(host, allowRemoteHost)
        if (!loopback) {
            System.err.println(
                "SECURITY WARNING: Vericore REST has no built-in authentication, authorization, or TLS. " +
                    "Remote binding is unsafe on an untrusted network; place it behind a trusted authenticated TLS proxy."
            )
        }
        echo("🌍 Starting Vericore Server on http://$host:$port")
        embeddedServer(Netty, host = host, port = port, module = Application::module).start(wait = true)
    }
}

/**
 * Refuse accidental exposure of the unauthenticated REST API.
 *
 * Only literal loopback addresses and the exact localhost hostname are trusted here;
 * arbitrary hostnames are not DNS-resolved to make a security decision.
 */
internal fun validateServerBindHost(host: String, allowRemoteHost: Boolean): Boolean {
    require(host.isNotBlank() && host == host.trim()) { "Host must not be blank or contain surrounding whitespace" }
    val normalized = host.lowercase().removePrefix("[").removeSuffix("]")
    val loopback = when {
        normalized == "localhost" || normalized == "::1" -> true
        else -> {
            val octets = normalized.split('.').map { it.toIntOrNull() }
            octets.size == 4 &&
                octets.all { it != null && it in 0..255 } &&
                octets[0] == 127
        }
    }
    require(loopback || allowRemoteHost) {
        "Refusing non-loopback host '$host' because the REST API has no built-in authentication or TLS. " +
            "Use a loopback host, or explicitly add --allow-remote-host only behind a trusted authenticated TLS boundary."
    }
    return loopback
}
