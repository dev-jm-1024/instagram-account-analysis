package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.infrastructure.identity.DefaultAccountIdentityResolver;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class DefaultAccountIdentityResolverTest {

    @TempDir
    Path dir;

    private final DefaultAccountIdentityResolver resolver =
            new DefaultAccountIdentityResolver(new DefaultStringNormalizer());

    @Test
    void resolvesUsernameAndNameFromPersonalInformation() throws IOException {
        Path file = dir.resolve("personal_information.json");
        Files.writeString(file, """
                {"profile_user":[{"string_map_data":{
                  "Username":{"value":"jane_doe"},
                  "Name":{"value":"Jane Doe"}
                }}]}
                """);
        Map<DomainType, List<Path>> scanned = Map.of(DomainType.IMPORT, List.of(file));

        Optional<AccountIdentity> owner = resolver.resolve(scanned);
        assertTrue(owner.isPresent());
        assertEquals("jane_doe", owner.get().getUsername().getValue());
        assertEquals("Jane Doe", owner.get().getDisplayName());
    }

    @Test
    void emptyWhenNoPersonalInformation() {
        assertTrue(resolver.resolve(Map.of()).isEmpty());
    }
}
