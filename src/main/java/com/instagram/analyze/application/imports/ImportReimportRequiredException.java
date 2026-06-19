package com.instagram.analyze.application.imports;

/**
 * 수동 본인식별(resolveOwner) 시 원본 export 폴더가 디스크에 없어 message 재파싱이 불가할 때 던진다
 * (interface_plan §4.1 C). 사용자에게 재임포트를 안내한다.
 */
public class ImportReimportRequiredException extends RuntimeException {

    public ImportReimportRequiredException() {
        super("original export folder is gone; re-import required to resolve owner");
    }
}
