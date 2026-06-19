package com.instagram.analyze.application.support;

import org.springframework.stereotype.Component;

import com.instagram.analyze.application.store.ImportReadStore;

/**
 * {@link ImportGuard} 기본 구현 — 보관소 상태를 읽어 G3/owner 게이트를 판정한다.
 * 게이트·status() 가 모두 같은 {@link ImportReadStore} 한 소스를 보므로 두 진실이 생기지 않는다.
 */
@Component
public class DefaultImportGuard implements ImportGuard {

    private final ImportReadStore store;

    public DefaultImportGuard(ImportReadStore store) {
        this.store = store;
    }

    @Override
    public void requireCompleted() {
        if (!store.isCompleted()) {
            throw new ImportNotCompletedException();
        }
    }

    @Override
    public boolean isImportRequired() {
        return !store.isCompleted();
    }

    @Override
    public void requireOwnerResolved() {
        if (!store.isOwnerResolved()) {
            throw new OwnerNotResolvedException();
        }
    }
}
