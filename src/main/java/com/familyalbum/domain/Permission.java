package com.familyalbum.domain;

/** 폴더에 대한 실효 권한. MANAGE = 가족 관리자 또는 폴더를 만든 사람(권한 설정·이름 변경·삭제 포함). */
public enum Permission {
    NONE, VIEW, EDIT, MANAGE;

    public boolean atLeast(Permission other) {
        return this.ordinal() >= other.ordinal();
    }
}
