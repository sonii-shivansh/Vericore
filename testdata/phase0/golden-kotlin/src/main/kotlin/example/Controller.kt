package example

import example.Service

class Controller(private val service: Service) {
    fun greet(name: String): String = service.greet(name)
}
