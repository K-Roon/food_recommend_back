package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.ReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 평점/목록은 삭제되지 않은 리뷰(r.deletedAt is null)에 속한 항목만 집계합니다. */
public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    List<ReviewItem> findByReviewIdIn(Collection<UUID> reviewIds);

    void deleteByReviewId(UUID reviewId);

    @Query("select ri from ReviewItem ri, Review r where ri.reviewId = r.id and r.deletedAt is null "
            + "and ri.menuId = :menuId order by ri.createdAt desc")
    List<ReviewItem> findAliveByMenuId(@Param("menuId") UUID menuId);

    /** 메뉴별 [menuId, 평균, 개수] — 메뉴 목록에 평점을 한 번의 쿼리로 붙이기 위함. */
    @Query("select ri.menuId, avg(ri.rating), count(ri) from ReviewItem ri, Review r "
            + "where ri.reviewId = r.id and r.deletedAt is null and ri.menuId in :menuIds group by ri.menuId")
    List<Object[]> menuRatingStats(@Param("menuIds") Collection<UUID> menuIds);

    /** [평균, 개수] — 이 레스토랑의 (삭제되지 않은) 메뉴들에 달린 리뷰 평점. */
    @Query("select avg(ri.rating), count(ri) from ReviewItem ri, Review r, Menu m "
            + "where ri.reviewId = r.id and ri.menuId = m.id and r.deletedAt is null and m.deletedAt is null "
            + "and r.restaurantId = :restaurantId")
    List<Object[]> restaurantMenuRatingStats(@Param("restaurantId") UUID restaurantId);
}
