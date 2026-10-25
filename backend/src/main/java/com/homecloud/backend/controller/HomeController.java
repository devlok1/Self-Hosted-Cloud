package com.homecloud.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/api/health")
    public String health() {
        return "HomeCloud Backend is running";
    }
}
