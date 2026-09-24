package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.RestaurantRequest;
import com.kroon.food_recommend_back.dto.RestaurantResponse;
import com.kroon.food_recommend_back.entity.Restaurant;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * restaurants는 company_id로 스코프가 나뉩니다. 이 서비스의 모든 메서드는
 * RlsContextService.bootstrap()이 이미 실행되어 SecurityContext에 uid가
 * 있다는 전제 하에, 그 uid로 User를 다시 조회해 companyId를 얻습니다.
 * (컨트롤러 진입 전에 필터가 인증만 해주고, company_id/role은 필터 시점엔
 * 아직 모르기 때문 — MeController와 동일한 패턴입니다.)
 */
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RlsContextService rlsContextService;

    public List<RestaurantResponse> list() {
        User user = currentUser();
        return restaurantRepository.findByCompanyId(user.getCompanyId()).stream()
                .map(RestaurantResponse::new)
                .toList();
    }

    /**
     * 등록 전 중복 체크. 프론트엔드는 레스토랑 등록 폼 제출 전에 이 API를
     * 먼저 호출해서, 이미 비슷한 게 있으면 그걸 쓰도록 사용자를 유도해야
     * 합니다 (강제는 아님 — 최종 등록 여부는 사용자 선택).
     */
    public List<RestaurantResponse> searchDuplicates(String name, String address) {
        User user = currentUser();
        return restaurantRepository
                .findByCompanyIdAndNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
                        user.getCompanyId(), name, address)
                .stream()
                .map(RestaurantResponse::new)
                .toList();
    }

    @Transactional
    public RestaurantResponse create(RestaurantRequest request) {
        User user = currentUser();

        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setName(request.getName());
        restaurant.setAddress(request.getAddress());
        restaurant.setLatitude(request.getLatitude());
        restaurant.setLongitude(request.getLongitude());
        restaurant.setCompanyId(user.getCompanyId());
        restaurant.setCreatedBy(user.getId());
        restaurant.setCreatedAt(Instant.now());

        return new RestaurantResponse(restaurantRepository.save(restaurant));
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}