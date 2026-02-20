package com.fmt.fmt_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicTestController {

    @GetMapping("/test-sms")
    public String testSms() {
        return "This is public - no token needed";
    }
}