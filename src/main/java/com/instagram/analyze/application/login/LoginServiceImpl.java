package com.instagram.analyze.application.login;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.login.LoginEvent;

/**
 * {@link LoginService} 구현. timestamp 내림차순(최신순). 소스 없으면 {@code Sourced.absent}.
 */
@Service
public class LoginServiceImpl implements LoginService {

    private final ImportReadStore store;
    private final ImportGuard guard;

    public LoginServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public Sourced<List<LoginEvent>> timeline() {
        guard.requireCompleted();

        List<LoginEvent> sorted = store.loginEvents().stream()
                .sorted(Comparator.comparingLong((LoginEvent e) -> e.getTimestamp().getValue()).reversed())
                .toList();

        return store.sourceExists(DomainType.LOGIN)
                ? Sourced.present(sorted)
                : Sourced.absent(List.of());   // LOGIN_HISTORY_NOT_FOUND(200, G4)
    }
}
