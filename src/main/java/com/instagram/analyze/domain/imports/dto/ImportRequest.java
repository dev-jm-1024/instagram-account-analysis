package com.instagram.analyze.domain.imports.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code POST /api/import} 요청: 압축 해제한 export 폴더 경로.
 * 빈 값은 IMPORT_PATH_BLANK(400) 로 거부된다 (domain_exception G1).
 */
@Getter
@Setter
@NoArgsConstructor
public class ImportRequest {

    @NotBlank
    private String folderPath;
}
