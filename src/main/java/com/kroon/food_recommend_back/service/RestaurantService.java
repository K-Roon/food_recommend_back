package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.RestaurantRequest;
import com.kroon.food_recommend_back.dto.RestaurantResponse;
import com.kroon.food_recommend_back.entity.DeleteReason;
import com.kroon.food_recommend_back.entity.Menu;
import com.kroon.food_recommend_back.entity.Restaurant;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.MenuRepository;
import com.kroon.food_recommend_back.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * restaurants는 company_id로 스코프가 나뉩니다 (RLS + 앱 레이어 이중 확인).
 *
 * CRUD
 * - 조회/등록: 같은 회사 누구나
 * - 수정: 등록자 본인 / 회사관리자 / 관리자
 * - 삭제(soft): 등록자 본인 → self, 회사관리자/관리자 → violation(사유 필수).
 *   레스토랑이 지워지면 그 레스토랑의 메뉴도 cascade 사유로 같이 지워집니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuRepository menuRepository;
    private final RlsContextService rlsContextService;

    public List<RestaurantResponse> list() {
        User user = currentUser();
        return restaurantRepository.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(user.getCompanyId()).stream()
                .map(r -> toResponse(user, r))
                .toList();
    }

    public RestaurantResponse get(UUID restaurantId) {
        User user = currentUser();
        return toResponse(user, findAliveOrThrow(user, restaurantId));
    }

    /**
     * 등록 전 중복 체크. 프론트엔드는 레스토랑 등록 폼 제출 전에 이 API를
     * 먼저 호출해서, 이미 비슷한 게 있으면 그걸 쓰도록 사용자를 유도해야
     * 합니다 (강제는 아님 — 최종 등록 여부는 사용자 선택).
     */
    public List<RestaurantResponse> searchDuplicates(String name, String address) {
        User user = currentUser();
        return restaurantRepository
                .findByCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
                        user.getCompanyId(), name, address)
                .stream()
                .map(r -> toResponse(user, r))
                .toList();
    }

    @Transactional
    public RestaurantResponse create(RestaurantRequest request) {
        User user = currentUser();

        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setName(request.getName().trim());
        restaurant.setAddress(request.getAddress().trim());
        restaurant.setLatitude(request.getLatitude());
        restaurant.setLongitude(request.getLongitude());
        restaurant.setCompanyId(user.getCompanyId());
        restaurant.setCreatedBy(user.getId());
        restaurant.setCreatedAt(Instant.now());

        return toResponse(user, restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantResponse update(UUID restaurantId, RestaurantRequest request) {
        User user = currentUser();
        Restaurant restaurant = findAliveOrThrow(user, restaurantId);
        Permissions.requireCanEdit(user, restaurant.getCreatedBy(), restaurant.getCompanyId());

        restaurant.setName(request.getName().trim());
        restaurant.setAddress(request.getAddress().trim());
        restaurant.setLatitude(request.getLatitude());
        restaurant.setLongitude(request.getLongitude());
        restaurant.setUpdatedAt(Instant.now());
        return toResponse(user, restaurantRepository.save(restaurant));
    }

    @Transactional
    public void delete(UUID restaurantId, String note) {
        User user = currentUser();
        Restaurant restaurant = findAliveOrThrow(user, restaurantId);
        DeleteReason reason = Permissions.resolveDeleteReason(user, restaurant.getCreatedBy(), restaurant.getCompanyId(), note);

        restaurant.markDeleted(user.getId(), reason, note);
        restaurantRepository.save(restaurant);

        // 하위 메뉴도 같이 숨김 (리뷰는 레스토랑이 안 보이면 자연히 안 보임 — 데이터는 보존)
        for (Menu menu : menuRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId)) {
            menu.markDeleted(user.getId(), DeleteReason.cascade, "레스토랑 삭제: " + restaurant.getName());
            menuRepository.save(menu);
        }
    }

    private Restaurant findAliveOrThrow(User user, UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "레스토랑을 찾을 수 없습니다."));
        Permissions.requireSameCompany(user, restaurant.getCompanyId());
        return restaurant;
    }

    private RestaurantResponse toResponse(User user, Restaurant r) {
        return new RestaurantResponse(r, user.getId().equals(r.getCreatedBy()),
                Permissions.canEdit(user, r.getCreatedBy(), r.getCompanyId()));
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
