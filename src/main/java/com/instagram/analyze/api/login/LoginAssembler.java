package com.instagram.analyze.api.login;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.login.dto.LoginResponse;

@Component
public class LoginAssembler {

    public LoginResponse toResponse(List<LoginEvent> events) {
        List<LoginResponse.LoginItem> items = events.stream()
                .map(e -> new LoginResponse.LoginItem(
                        e.getTimestamp().getValue(),
                        e.getType().name(),
                        e.getIpAddress(),
                        e.getUserAgent()))
                .toList();
        return new LoginResponse(items);
    }
}
