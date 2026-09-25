package com.kroon.food_recommend_back.entity;

/**
 * soft delete 사유 — DB의 delete_reason 체크 제약(01_schema_crud.sql)과 값이 같아야 합니다.
 */
public enum DeleteReason {
    /** 작성자 본인이 삭제 */
    self,
    /** 회사관리자/관리자가 규정위반으로 삭제 (delete_note에 사유 필수) */
    violation,
    /** 작성자가 회원 탈퇴해서 같이 삭제 */
    withdrawal,
    /** 상위 항목(레스토랑)이 삭제돼서 같이 삭제 */
    cascade,
    /** 관리자 정리 */
    admin
}
