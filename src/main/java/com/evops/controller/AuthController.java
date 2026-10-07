package com.evops.controller;

import com.evops.aqua.dto.LoginRequest;
import com.evops.common.ApiResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<Object> login(@Validated @RequestBody LoginRequest request) {
        Subject subject = SecurityUtils.getSubject();
        subject.login(new UsernamePasswordToken(request.getUsername(), request.getPassword()));
        return ApiResponse.ok(Collections.singletonMap("username", subject.getPrincipal()));
    }
}
