package com.instagram.analyze.domain.imports.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code POST /api/import/owner} 요청: 자동 식별 실패 시 본인 username 수동 입력 (domain.md 1.4 fallback).
 * 빈 값은 OWNER_INPUT_BLANK(400) 로 거부된다 (domain_exception G2).
 */
@Getter
@Setter
@NoArgsConstructor
public class OwnerRequest {

    @NotBlank
    private String username;
}
