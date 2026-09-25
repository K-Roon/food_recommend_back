package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** 삭제(soft delete)된 메뉴는 목록에서 빠지도록 DeletedAtIsNull 조건을 붙여 조회합니다. */
public interface MenuRepository extends JpaRepository<Menu, UUID> {

    List<Menu> findByCompanyIdAndApprovalStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID companyId, List<Menu.ApprovalStatus> statuses);

    List<Menu> findByCompanyIdAndRestaurantIdAndApprovalStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID companyId, UUID restaurantId, List<Menu.ApprovalStatus> statuses);

    List<Menu> findByCompanyIdAndApprovalStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID companyId, Menu.ApprovalStatus approvalStatus);

    /** 레스토랑 삭제 시 하위 메뉴 연쇄 삭제용 */
    List<Menu> findByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    /** 내가 등록한 메뉴 (승인 대기/반려 포함) */
    List<Menu> findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(String createdBy);
}
