package com.instagram.analyze.api.explorer;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.explorer.ExplorerNode;
import com.instagram.analyze.domain.explorer.RawFileContent;
import com.instagram.analyze.domain.explorer.dto.ExplorerFileResponse;
import com.instagram.analyze.domain.explorer.dto.ExplorerTreeResponse;

@Component
public class ExplorerAssembler {

    public ExplorerTreeResponse toTree(ExplorerNode root) {
        return new ExplorerTreeResponse(root);
    }

    public ExplorerFileResponse toFile(RawFileContent content) {
        return new ExplorerFileResponse(content.getPath(), content.getContent(), content.isTruncated());
    }
}
