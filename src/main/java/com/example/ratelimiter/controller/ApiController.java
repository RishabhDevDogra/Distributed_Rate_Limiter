package com.example.ratelimiter.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "Public endpoint - no limit";
    }

    @GetMapping("/limited")
    public String limitedEndpoint() {
        return "This will be rate limited soon 👀";
    }

    @PostMapping("/data")
    public String postData(@RequestBody String body) {
        return "Received: " + body;
    }
}