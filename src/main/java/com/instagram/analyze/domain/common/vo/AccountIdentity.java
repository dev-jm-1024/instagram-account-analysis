package com.instagram.analyze.domain.common.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 본인(Owner) 식별 정보 VO (domain.md 1.4).
 *
 * <p>username 은 팔로우의 {@code value} 와, displayName 은 DM 의 {@code sender_name} 과
 * 매칭하기 위해 두 형태를 모두 보관한다.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class AccountIdentity {
    private final Username username;
    private final String displayName;
}
