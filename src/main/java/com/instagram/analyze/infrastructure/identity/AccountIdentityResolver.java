package com.instagram.analyze.infrastructure.identity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;

/**
 * 본인 식별기 (domain.md 1.4) — 파일 접근을 동반하므로 {@code infrastructure} 에 둔다.
 *
 * <p>우선순위: ① {@code personal_information.json} → ② DM participants 교차 분석.
 * 둘 다 실패하면 {@code Optional.empty()} 를 반환하고, 호출 측은 {@code ownerResolved:false} 로
 * 두어 수동 입력(resolveOwner)을 유도한다.
 */
public interface AccountIdentityResolver {

    /** 스캔된 파일 목록으로 본인 식별을 시도한다. 실패 시 {@code Optional.empty()}. */
    Optional<AccountIdentity> resolve(Map<DomainType, List<Path>> scanned);
}
