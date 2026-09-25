package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.Size;

/**
 * 삭제 요청 본문 (선택). 본인 삭제는 비워도 되고,
 * 회사관리자/관리자가 남의 글을 지우는 규정위반 삭제는 note(사유)가 필수입니다.
 */
public record DeleteRequest(@Size(max = 500) String note) {

    public static String noteOf(DeleteRequest request) {
        return request == null || request.note() == null || request.note().isBlank() ? null : request.note().trim();
    }
}
