package com.onmeet.file.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    @GetMapping("/")
    public String healthCheck() {
        return "File Service is operational";
    }
}
