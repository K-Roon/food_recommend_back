package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.SignupRequest;
import com.kroon.food_recommend_back.dto.SignupResponse;
import com.kroon.food_recommend_back.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @PostMapping("/api/signup")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }
}