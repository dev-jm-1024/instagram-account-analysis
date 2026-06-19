package com.instagram.analyze.application.log;

import java.util.List;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.log.LogFile;

/**
 * {@link MiscLogService} 구현. 파일별 그룹 그대로 반환. 디렉토리 없으면 {@code Sourced.absent}.
 */
@Service
public class MiscLogServiceImpl implements MiscLogService {

    private final ImportReadStore store;
    private final ImportGuard guard;

    public MiscLogServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public Sourced<List<LogFile>> logs() {
        guard.requireCompleted();
        return store.sourceExists(DomainType.MISC_LOG)
                ? Sourced.present(store.logFiles())
                : Sourced.absent(List.of());   // MISC_LOG_DIR_NOT_FOUND(200, G4)
    }
}
