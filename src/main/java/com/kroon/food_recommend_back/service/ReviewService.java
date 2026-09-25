package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.MenuReviewsResponse;
import com.kroon.food_recommend_back.dto.RestaurantRatingResponse;
import com.kroon.food_recommend_back.dto.RestaurantReviewsResponse;
import com.kroon.food_recommend_back.dto.ReviewItemRequest;
import com.kroon.food_recommend_back.dto.ReviewItemResponse;
import com.kroon.food_recommend_back.dto.ReviewRequest;
import com.kroon.food_recommend_back.dto.ReviewResponse;
import com.kroon.food_recommend_back.entity.DeleteReason;
import com.kroon.food_recommend_back.entity.Menu;
import com.kroon.food_recommend_back.entity.Restaurant;
import com.kroon.food_recommend_back.entity.Review;
import com.kroon.food_recommend_back.entity.ReviewItem;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.MenuRepository;
import com.kroon.food_recommend_back.repository.RestaurantRepository;
import com.kroon.food_recommend_back.repository.ReviewItemRepository;
import com.kroon.food_recommend_back.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 리뷰 규칙
 * - 한 번의 제출 = 레스토랑 1곳 + 그 레스토랑 메뉴 1개 이상 (레스토랑 단독 리뷰 불가)
 * - 레스토랑 자체 평점은 선택. 코멘트만 있고 평점이 없으면 거부
 * - 메뉴는 전부 그 레스토랑 소속이어야 하고, 공개된(auto_approved/approved) 메뉴만 리뷰 가능
 * - 한 제출 안에서 같은 메뉴 중복 불가
 * - 같은 레스토랑에 여러 번 리뷰하는 건 허용 (재방문 후기)
 *
 * CRUD: 작성 / 조회 / 수정(작성자만, 항목 전체 교체) / 삭제(soft — 본인 self, 회사관리자·관리자 violation)
 * 삭제된 리뷰는 모든 목록과 평점 집계에서 제외됩니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class ReviewService {

    private static final Set<Menu.ApprovalStatus> PUBLISHED =
            Set.of(Menu.ApprovalStatus.auto_approved, Menu.ApprovalStatus.approved);

    private final ReviewRepository reviewRepository;
    private final ReviewItemRepository reviewItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuRepository menuRepository;
    private final RatingService ratingService;
    private final RlsContextService rlsContextService;

    @Transactional
    public ReviewResponse create(ReviewRequest request) {
        User user = currentUser();
        Restaurant restaurant = findRestaurantOrThrow(user, request.restaurantId());
        Map<UUID, Menu> menus = validate(request, restaurant);

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setUserId(user.getId());
        review.setRestaurantId(restaurant.getId());
        review.setCreatedAt(Instant.now());
        applyRestaurantPart(review, request);
        reviewRepository.save(review);

        return ReviewResponse.of(review, restaurant.getName(), true, true, saveItems(review, request, menus));
    }

    public ReviewResponse get(UUID reviewId) {
        User user = currentUser();
        Review review = findAliveOrThrow(user, reviewId);
        Restaurant restaurant = findRestaurantOrThrow(user, review.getRestaurantId());
        return toResponses(List.of(review), user, Map.of(restaurant.getId(), restaurant.getName())).get(0);
    }

    /**
     * 리뷰 수정 — 작성자 본인만. 레스토랑은 바꿀 수 없고(다른 가게면 새로 작성),
     * 레스토랑 평점/코멘트와 메뉴 항목 전체를 요청 내용으로 교체합니다.
     */
    @Transactional
    public ReviewResponse update(UUID reviewId, ReviewRequest request) {
        User user = currentUser();
        Review review = findAliveOrThrow(user, reviewId);
        if (!user.getId().equals(review.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "리뷰는 작성자 본인만 수정할 수 있습니다.");
        }
        if (!review.getRestaurantId().equals(request.restaurantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "리뷰의 레스토랑은 바꿀 수 없습니다. 새 리뷰로 작성해 주세요.");
        }
        Restaurant restaurant = findRestaurantOrThrow(user, review.getRestaurantId());
        Map<UUID, Menu> menus = validate(request, restaurant);

        applyRestaurantPart(review, request);
        review.setUpdatedAt(Instant.now());
        reviewRepository.save(review);

        reviewItemRepository.deleteByReviewId(review.getId());
        reviewItemRepository.flush();
        return ReviewResponse.of(review, restaurant.getName(), true, true, saveItems(review, request, menus));
    }

    /**
     * 리뷰 삭제(soft) — 작성자 본인이면 self,
     * 같은 회사 회사관리자/관리자가 남의 리뷰를 지우면 violation(사유 필수).
     * (탈퇴 시 삭제는 MeService.withdraw → DB 함수 withdraw_current_user()가 처리)
     */
    @Transactional
    public void delete(UUID reviewId, String note) {
        User user = currentUser();
        Review review = findAliveOrThrow(user, reviewId);
        Restaurant restaurant = findRestaurantOrThrow(user, review.getRestaurantId());
        DeleteReason reason = Permissions.resolveDeleteReason(user, review.getUserId(), restaurant.getCompanyId(), note);
        review.markDeleted(user.getId(), reason, note);
        reviewRepository.save(review);
    }

    private Map<UUID, Menu> validate(ReviewRequest request, Restaurant restaurant) {
        if (request.restaurantRating() == null && hasText(request.restaurantComment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "레스토랑 코멘트를 남기려면 레스토랑 평점도 입력해 주세요.");
        }

        Set<UUID> seen = new HashSet<>();
        for (ReviewItemRequest item : request.items()) {
            if (!seen.add(item.menuId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 메뉴를 한 리뷰에 두 번 넣을 수 없습니다.");
            }
        }

        Map<UUID, Menu> menus = menuRepository.findAllById(seen).stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        for (UUID menuId : seen) {
            Menu menu = menus.get(menuId);
            if (menu == null || menu.isDeleted() || !menu.getRestaurantId().equals(restaurant.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 레스토랑의 메뉴가 아닙니다: " + menuId);
            }
            if (!PUBLISHED.contains(menu.getApprovalStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아직 승인되지 않은 메뉴는 리뷰할 수 없습니다: " + menu.getName());
            }
        }
        return menus;
    }

    private static void applyRestaurantPart(Review review, ReviewRequest request) {
        review.setRestaurantRating(request.restaurantRating() == null ? null : request.restaurantRating().shortValue());
        review.setRestaurantComment(request.restaurantRating() == null ? null : trimToNull(request.restaurantComment()));
    }

    private List<ReviewItemResponse> saveItems(Review review, ReviewRequest request, Map<UUID, Menu> menus) {
        Instant now = Instant.now();
        return request.items().stream().map(req -> {
            ReviewItem item = new ReviewItem();
            item.setId(UUID.randomUUID());
            item.setReviewId(review.getId());
            item.setMenuId(req.menuId());
            item.setRating(req.rating().shortValue());
            item.setComment(trimToNull(req.comment()));
            item.setCreatedAt(now);
            reviewItemRepository.save(item);
            return ReviewItemResponse.of(item, menus.get(req.menuId()).getName());
        }).toList();
    }

    private Review findAliveOrThrow(User user, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."));
        findRestaurantOrThrow(user, review.getRestaurantId()); // 회사 스코프 확인
        return review;
    }

    /** 레스토랑 검색/상세 → 레스토랑 평점 + 그 레스토랑 메뉴 리뷰 전체(최신순). */
    public RestaurantReviewsResponse listForRestaurant(UUID restaurantId) {
        User user = currentUser();
        Restaurant restaurant = findRestaurantOrThrow(user, restaurantId);
        List<Review> reviews = reviewRepository.findByRestaurantIdAndDeletedAtIsNullOrderByCreatedAtDesc(restaurantId);
        Map<UUID, String> restaurantNames = Map.of(restaurant.getId(), restaurant.getName());
        return new RestaurantReviewsResponse(
                restaurant.getId(),
                restaurant.getName(),
                ratingService.restaurantRating(restaurantId),
                toResponses(reviews, user, restaurantNames));
    }

    public RestaurantRatingResponse restaurantRating(UUID restaurantId) {
        User user = currentUser();
        findRestaurantOrThrow(user, restaurantId);
        return ratingService.restaurantRating(restaurantId);
    }

    /** 메뉴 상세 → 이 메뉴의 평점 + 메뉴별 코멘트(최신순). */
    public MenuReviewsResponse listForMenu(UUID menuId) {
        User user = currentUser();
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
        Permissions.requireSameCompany(user, menu.getCompanyId());
        boolean reviewer = user.getRole() == User.Role.company_admin || user.getRole() == User.Role.admin;
        if (!PUBLISHED.contains(menu.getApprovalStatus()) && !reviewer && !menu.getCreatedBy().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다.");
        }

        List<ReviewItem> items = reviewItemRepository.findAliveByMenuId(menuId);
        Set<UUID> reviewIds = items.stream().map(ReviewItem::getReviewId).collect(Collectors.toSet());
        Map<UUID, String> authorByReview = reviewRepository.findAllById(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, Review::getUserId));

        List<MenuReviewsResponse.Entry> entries = items.stream()
                .map(i -> new MenuReviewsResponse.Entry(i.getId(), i.getReviewId(), i.getRating(), i.getComment(),
                        user.getId().equals(authorByReview.get(i.getReviewId())), i.getCreatedAt()))
                .toList();
        return new MenuReviewsResponse(menuId, ratingService.menuRating(menuId), entries);
    }

    /** 내가 쓴 리뷰 (최신순). */
    public List<ReviewResponse> listMine() {
        User user = currentUser();
        List<Review> reviews = reviewRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId());
        Set<UUID> restaurantIds = reviews.stream().map(Review::getRestaurantId).collect(Collectors.toSet());
        Map<UUID, String> names = restaurantRepository.findAllById(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
        return toResponses(reviews, user, names);
    }

    private List<ReviewResponse> toResponses(List<Review> reviews, User user, Map<UUID, String> restaurantNames) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<UUID> reviewIds = reviews.stream().map(Review::getId).toList();
        List<ReviewItem> allItems = reviewItemRepository.findByReviewIdIn(reviewIds);
        Set<UUID> menuIds = allItems.stream().map(ReviewItem::getMenuId).collect(Collectors.toSet());
        Map<UUID, String> menuNames = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getName));
        Map<UUID, List<ReviewItem>> itemsByReview = allItems.stream()
                .collect(Collectors.groupingBy(ReviewItem::getReviewId));

        return reviews.stream()
                .map(r -> ReviewResponse.of(
                        r,
                        restaurantNames.get(r.getRestaurantId()),
                        user.getId().equals(r.getUserId()),
                        user.getId().equals(r.getUserId()) || Permissions.isReviewer(user),
                        itemsByReview.getOrDefault(r.getId(), List.of()).stream()
                                .map(i -> ReviewItemResponse.of(i, menuNames.get(i.getMenuId())))
                                .toList()))
                .toList();
    }

    private Restaurant findRestaurantOrThrow(User user, UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "레스토랑을 찾을 수 없습니다."));
        Permissions.requireSameCompany(user, restaurant.getCompanyId());
        return restaurant;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
