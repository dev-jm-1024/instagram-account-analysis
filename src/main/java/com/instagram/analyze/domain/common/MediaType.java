package com.instagram.analyze.domain.common;

import java.util.Locale;

/**
 * 스캐너·탐색기에서 제외하는 미디어 확장자 (domain.md 1.1 / 10).
 */
public enum MediaType {
    JPG, MP4, PNG, MOV, WEBP;

    /** 주어진 파일명이 미디어 확장자인지 판별한다. */
    public static boolean isMedia(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return false;
        }
        String ext = filename.substring(dot + 1).toUpperCase(Locale.ROOT);
        for (MediaType type : values()) {
            if (type.name().equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
