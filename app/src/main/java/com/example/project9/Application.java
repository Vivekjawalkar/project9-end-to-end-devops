package com.example.project9;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @RestController
    static class ApplicationController {

        @GetMapping("/")
        public String home() {
            return "Project 9 - Production DevOps Application - Version 2.0";
        }

        @GetMapping("/health")
        public String health() {
            return "UP";
        }
    }
}
