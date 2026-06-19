package com.instagram.analyze.api.login;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.api.common.ApiResultCode;
import com.instagram.analyze.application.login.LoginService;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.login.dto.LoginResponse;

/** GET /api/logins (부록 A). 소스 없으면 200 + LOGIN_HISTORY_NOT_FOUND (G4). */
@RestController
@RequestMapping("/api/logins")
public class LoginController {

    private final LoginService loginService;
    private final LoginAssembler assembler;

    public LoginController(LoginService loginService, LoginAssembler assembler) {
        this.loginService = loginService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<LoginResponse> logins() {
        Sourced<List<LoginEvent>> result = loginService.timeline();
        LoginResponse body = assembler.toResponse(result.getValue());
        return result.isSourceExists()
                ? ApiResponse.ok(body)
                : ApiResponse.of(ApiResultCode.LOGIN_HISTORY_NOT_FOUND, body);
    }
}
