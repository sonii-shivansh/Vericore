package example;

import example.Service;

public class Controller {
    private final Service service;

    public Controller(Service service) {
        this.service = service;
    }

    public String greet(String name) {
        return service.greet(name);
    }
}
