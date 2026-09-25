package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.entity.DeleteReason;
import com.kroon.food_recommend_back.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * 모든 도메인이 같은 규칙으로 수정/삭제 권한을 판단하도록 한 곳에 모아둔 헬퍼.
 * (DB RLS 정책 02_rls_policies.sql 과 같은 규칙 — 앱에서 먼저 친절한 에러를 주고, DB가 최종 방어)
 *
 * 수정: 작성자 본인 / 같은 회사 회사관리자 / 관리자
 * 삭제: 작성자 본인 → self
 *       그 외 회사관리자·관리자 → violation (사유 메모 필수)
 */
public final class Permissions {

    private Permissions() {
    }

    public static boolean isReviewer(User user) {
        return user.getRole() == User.Role.company_admin || user.getRole() == User.Role.admin;
    }

    /** 다른 회사 데이터는 존재 여부도 숨기기 위해 403 대신 404. */
    public static void requireSameCompany(User user, UUID companyId) {
        if (user.getRole() == User.Role.admin) {
            return;
        }
        if (companyId == null || !companyId.equals(user.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "찾을 수 없습니다.");
        }
    }

    public static void requireReviewer(User user) {
        if (!isReviewer(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "회사관리자 또는 관리자만 할 수 있습니다.");
        }
    }

    public static void requireAdmin(User user) {
        if (user.getRole() != User.Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 할 수 있습니다.");
        }
    }

    public static boolean canEdit(User user, String createdBy, UUID companyId) {
        if (user.getRole() == User.Role.admin) {
            return true;
        }
        if (companyId == null || !companyId.equals(user.getCompanyId())) {
            return false;
        }
        return user.getRole() == User.Role.company_admin || user.getId().equals(createdBy);
    }

    public static void requireCanEdit(User user, String createdBy, UUID companyId) {
        requireSameCompany(user, companyId);
        if (!canEdit(user, createdBy, companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자 본인 또는 회사관리자만 수정할 수 있습니다.");
        }
    }

    /**
     * 삭제 사유 결정. 작성자 본인이면 self, 아니면 회사관리자/관리자의 규정위반 삭제(사유 필수).
     */
    public static DeleteReason resolveDeleteReason(User user, String createdBy, UUID companyId, String note) {
        requireSameCompany(user, companyId);
        if (user.getId().equals(createdBy)) {
            return DeleteReason.self;
        }
        if (!isReviewer(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자 본인 또는 회사관리자만 삭제할 수 있습니다.");
        }
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "규정위반 삭제는 사유(note)를 입력해야 합니다.");
        }
        return DeleteReason.violation;
    }
}
