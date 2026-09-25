package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.MenuOptionGroupRequest;
import com.kroon.food_recommend_back.dto.MenuOptionGroupResponse;
import com.kroon.food_recommend_back.dto.MenuOptionGroupUpdateRequest;
import com.kroon.food_recommend_back.dto.MenuOptionRequest;
import com.kroon.food_recommend_back.dto.MenuOptionResponse;
import com.kroon.food_recommend_back.dto.MenuRequest;
import com.kroon.food_recommend_back.dto.MenuResponse;
import com.kroon.food_recommend_back.dto.MenuSummaryResponse;
import com.kroon.food_recommend_back.dto.MenuUpdateRequest;
import com.kroon.food_recommend_back.dto.RatingSummary;
import com.kroon.food_recommend_back.entity.Company;
import com.kroon.food_recommend_back.entity.DeleteReason;
import com.kroon.food_recommend_back.entity.Menu;
import com.kroon.food_recommend_back.entity.MenuOption;
import com.kroon.food_recommend_back.entity.MenuOptionGroup;
import com.kroon.food_recommend_back.entity.Restaurant;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.CompanyRepository;
import com.kroon.food_recommend_back.repository.MenuOptionGroupRepository;
import com.kroon.food_recommend_back.repository.MenuOptionRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * menus는 restaurants와 동일하게 company_id로 스코프가 나뉩니다.
 *
 * 승인 흐름: 회사가 review_approval_required=true면 새 메뉴는 pending으로 만들어지고,
 * 회사관리자/관리자가 승인하기 전까지 공개 목록에 안 보입니다. 승인 필요 회사에서
 * 일반 사용자가 메뉴 정보를 수정하면 다시 pending으로 돌아가 재승인을 받습니다
 * (승인된 내용을 몰래 바꾸는 것 방지). 회사관리자/관리자가 수정하면 상태 유지.
 *
 * CRUD
 * - 메뉴: 조회(공개분 누구나, 대기/반려는 등록자·승인권자) / 등록 / 수정 / 삭제(soft)
 * - 옵션 그룹·옵션: 추가 / 수정 / 삭제(실제 삭제 — 메뉴에 딸린 설정값이라 이력 불필요)
 * - 수정·삭제 권한은 Permissions 규칙(등록자 본인 / 회사관리자 / 관리자)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class MenuService {

    private static final List<Menu.ApprovalStatus> PUBLISHED_STATUSES =
            List.of(Menu.ApprovalStatus.auto_approved, Menu.ApprovalStatus.approved);

    private final MenuRepository menuRepository;
    private final MenuOptionGroupRepository menuOptionGroupRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final RestaurantRepository restaurantRepository;
    private final CompanyRepository companyRepository;
    private final RlsContextService rlsContextService;
    private final RatingService ratingService;

    // ===================================================================== 조회

    /** 기본 목록 뷰 — restaurantId를 주면 그 레스토랑의 메뉴만, 안 주면 회사 전체 메뉴. */
    public List<MenuSummaryResponse> list(UUID restaurantId) {
        User user = currentUser();
        List<Menu> menus = (restaurantId != null)
                ? menuRepository.findByCompanyIdAndRestaurantIdAndApprovalStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                        user.getCompanyId(), restaurantId, PUBLISHED_STATUSES)
                : menuRepository.findByCompanyIdAndApprovalStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                        user.getCompanyId(), PUBLISHED_STATUSES);
        return toSummaries(menus);
    }

    /** 내가 등록한 메뉴 (승인 대기/반려 포함) — 내 메뉴 관리 화면용. */
    public List<MenuSummaryResponse> listMine() {
        User user = currentUser();
        return toSummaries(menuRepository.findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()));
    }

    public MenuResponse get(UUID menuId) {
        User user = currentUser();
        Menu menu = findVisibleOrThrow(user, menuId);
        Restaurant restaurant = findRestaurantOrThrow(menu.getRestaurantId());
        return toResponse(user, menu, restaurant, loadOptionGroups(menuId), ratingService.menuRating(menuId));
    }

    /** 승인 대기 목록 — company_admin/admin 전용 (오래된 것부터). */
    public List<MenuSummaryResponse> listPending() {
        User user = currentUser();
        Permissions.requireReviewer(user);
        return toSummaries(menuRepository.findByCompanyIdAndApprovalStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                user.getCompanyId(), Menu.ApprovalStatus.pending));
    }

    // ===================================================================== 등록/수정/삭제

    @Transactional
    public MenuResponse create(MenuRequest request) {
        User user = currentUser();
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 레스토랑입니다."));
        if (!restaurant.getCompanyId().equals(user.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 회사의 레스토랑에는 메뉴를 등록할 수 없습니다.");
        }

        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setRestaurantId(restaurant.getId());
        menu.setCompanyId(user.getCompanyId());
        menu.setCreatedBy(user.getId());
        menu.setName(request.getName().trim());
        menu.setPrice(request.getPrice());
        menu.setImageUrl(request.getImageUrl());
        menu.setImageSource(request.getImageSource());
        menu.setApprovalStatus(approvalRequired(user) ? Menu.ApprovalStatus.pending : Menu.ApprovalStatus.auto_approved);
        menu.setBookable(false);   // placeholder — 예약 기능 미구현
        menu.setPriorityScore(0);  // placeholder — 우선노출 기능 미구현
        menu.setCreatedAt(Instant.now());
        menuRepository.save(menu);

        List<MenuOptionGroupResponse> optionGroups = (request.getOptionGroups() == null)
                ? List.of()
                : request.getOptionGroups().stream().map(g -> saveOptionGroup(menu.getId(), g)).toList();

        return toResponse(user, menu, restaurant, optionGroups, RatingSummary.EMPTY);
    }

    @Transactional
    public MenuResponse update(UUID menuId, MenuUpdateRequest request) {
        User user = currentUser();
        Menu menu = findAliveOrThrow(user, menuId);
        Permissions.requireCanEdit(user, menu.getCreatedBy(), menu.getCompanyId());

        menu.setName(request.name().trim());
        menu.setPrice(request.price());
        menu.setImageUrl(request.imageUrl());
        menu.setImageSource(request.imageSource());
        menu.setUpdatedAt(Instant.now());
        requeueIfNeeded(user, menu);
        menuRepository.save(menu);

        Restaurant restaurant = findRestaurantOrThrow(menu.getRestaurantId());
        return toResponse(user, menu, restaurant, loadOptionGroups(menuId), ratingService.menuRating(menuId));
    }

    /** 본인 삭제(self) 또는 회사관리자/관리자의 규정위반 삭제(violation, 사유 필수). 리뷰 데이터는 보존. */
    @Transactional
    public void delete(UUID menuId, String note) {
        User user = currentUser();
        Menu menu = findAliveOrThrow(user, menuId);
        DeleteReason reason = Permissions.resolveDeleteReason(user, menu.getCreatedBy(), menu.getCompanyId(), note);
        menu.markDeleted(user.getId(), reason, note);
        menuRepository.save(menu);
    }

    // ===================================================================== 승인

    @Transactional
    public void approve(UUID menuId) {
        setApprovalStatus(menuId, Menu.ApprovalStatus.approved);
    }

    @Transactional
    public void reject(UUID menuId) {
        setApprovalStatus(menuId, Menu.ApprovalStatus.rejected);
    }

    private void setApprovalStatus(UUID menuId, Menu.ApprovalStatus status) {
        User user = currentUser();
        Permissions.requireReviewer(user);
        Menu menu = findAliveOrThrow(user, menuId);
        menu.setApprovalStatus(status);
        menuRepository.save(menu);
    }

    // ===================================================================== 옵션 그룹 / 옵션

    @Transactional
    public MenuOptionGroupResponse addOptionGroup(UUID menuId, MenuOptionGroupRequest request) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        MenuOptionGroupResponse created = saveOptionGroup(menuId, request);
        touch(user, menu);
        return created;
    }

    @Transactional
    public MenuOptionGroupResponse updateOptionGroup(UUID menuId, UUID groupId, MenuOptionGroupUpdateRequest request) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        MenuOptionGroup group = findGroupOrThrow(menuId, groupId);
        group.setTitle(request.title().trim());
        group.setMultiSelect(request.multiSelect());
        group.setRequired(request.required());
        if (request.sortOrder() != null) {
            group.setSortOrder(request.sortOrder());
        }
        menuOptionGroupRepository.save(group);
        touch(user, menu);
        return loadOptionGroups(menuId).stream().filter(g -> g.getId().equals(groupId)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteOptionGroup(UUID menuId, UUID groupId) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        MenuOptionGroup group = findGroupOrThrow(menuId, groupId);
        menuOptionRepository.deleteByOptionGroupId(group.getId());
        menuOptionGroupRepository.delete(group);
        touch(user, menu);
    }

    @Transactional
    public MenuOptionResponse addOption(UUID menuId, UUID groupId, MenuOptionRequest request) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        MenuOptionGroup group = findGroupOrThrow(menuId, groupId);
        MenuOption option = new MenuOption();
        option.setId(UUID.randomUUID());
        option.setOptionGroupId(group.getId());
        applyOption(option, request);
        menuOptionRepository.save(option);
        touch(user, menu);
        return new MenuOptionResponse(option);
    }

    @Transactional
    public MenuOptionResponse updateOption(UUID menuId, UUID groupId, UUID optionId, MenuOptionRequest request) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        MenuOption option = findOptionOrThrow(menuId, groupId, optionId);
        applyOption(option, request);
        menuOptionRepository.save(option);
        touch(user, menu);
        return new MenuOptionResponse(option);
    }

    @Transactional
    public void deleteOption(UUID menuId, UUID groupId, UUID optionId) {
        User user = currentUser();
        Menu menu = findEditableOrThrow(user, menuId);
        menuOptionRepository.delete(findOptionOrThrow(menuId, groupId, optionId));
        touch(user, menu);
    }

    // ===================================================================== 내부 헬퍼

    private boolean approvalRequired(User user) {
        return companyRepository.findById(user.getCompanyId())
                .map(Company::isReviewApprovalRequired)
                .orElse(false);
    }

    /** 승인 필요 회사에서 일반 사용자가 수정하면 재승인 대기로 되돌림. */
    private void requeueIfNeeded(User user, Menu menu) {
        if (!Permissions.isReviewer(user) && approvalRequired(user)) {
            menu.setApprovalStatus(Menu.ApprovalStatus.pending);
        }
    }

    /** 옵션 변경도 메뉴 수정으로 취급 (수정 시각 + 재승인 규칙 동일 적용). */
    private void touch(User user, Menu menu) {
        menu.setUpdatedAt(Instant.now());
        requeueIfNeeded(user, menu);
        menuRepository.save(menu);
    }

    private Menu findAliveOrThrow(User user, UUID menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
        Permissions.requireSameCompany(user, menu.getCompanyId());
        return menu;
    }

    /** 승인 대기/반려 메뉴는 등록자 본인과 승인 권한자에게만 보임 (그 외엔 존재 자체를 숨김). */
    private Menu findVisibleOrThrow(User user, UUID menuId) {
        Menu menu = findAliveOrThrow(user, menuId);
        boolean published = PUBLISHED_STATUSES.contains(menu.getApprovalStatus());
        if (!published && !Permissions.isReviewer(user) && !menu.getCreatedBy().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다.");
        }
        return menu;
    }

    private Menu findEditableOrThrow(User user, UUID menuId) {
        Menu menu = findAliveOrThrow(user, menuId);
        Permissions.requireCanEdit(user, menu.getCreatedBy(), menu.getCompanyId());
        return menu;
    }

    private MenuOptionGroup findGroupOrThrow(UUID menuId, UUID groupId) {
        return menuOptionGroupRepository.findById(groupId)
                .filter(g -> g.getMenuId().equals(menuId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "옵션 그룹을 찾을 수 없습니다."));
    }

    private MenuOption findOptionOrThrow(UUID menuId, UUID groupId, UUID optionId) {
        findGroupOrThrow(menuId, groupId);
        return menuOptionRepository.findById(optionId)
                .filter(o -> o.getOptionGroupId().equals(groupId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "옵션을 찾을 수 없습니다."));
    }

    private Restaurant findRestaurantOrThrow(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "레스토랑을 찾을 수 없습니다."));
    }

    private static void applyOption(MenuOption option, MenuOptionRequest request) {
        option.setName(request.getName().trim());
        option.setExtraPrice(request.getExtraPrice());
        option.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
    }

    private MenuOptionGroupResponse saveOptionGroup(UUID menuId, MenuOptionGroupRequest request) {
        MenuOptionGroup group = new MenuOptionGroup();
        group.setId(UUID.randomUUID());
        group.setMenuId(menuId);
        group.setTitle(request.getTitle().trim());
        group.setMultiSelect(request.isMultiSelect());
        group.setRequired(request.isRequired());
        group.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        menuOptionGroupRepository.save(group);

        List<MenuOptionResponse> options = request.getOptions().stream()
                .map(optionRequest -> {
                    MenuOption option = new MenuOption();
                    option.setId(UUID.randomUUID());
                    option.setOptionGroupId(group.getId());
                    applyOption(option, optionRequest);
                    menuOptionRepository.save(option);
                    return new MenuOptionResponse(option);
                })
                .toList();

        return new MenuOptionGroupResponse(group, options);
    }

    private List<MenuOptionGroupResponse> loadOptionGroups(UUID menuId) {
        List<MenuOptionGroup> groups = menuOptionGroupRepository.findByMenuIdOrderBySortOrderAsc(menuId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<UUID> groupIds = groups.stream().map(MenuOptionGroup::getId).toList();
        Map<UUID, List<MenuOption>> optionsByGroupId = menuOptionRepository
                .findByOptionGroupIdInOrderBySortOrderAsc(groupIds).stream()
                .collect(Collectors.groupingBy(MenuOption::getOptionGroupId));

        return groups.stream()
                .map(group -> new MenuOptionGroupResponse(
                        group,
                        optionsByGroupId.getOrDefault(group.getId(), List.of()).stream()
                                .map(MenuOptionResponse::new)
                                .toList()))
                .toList();
    }

    private MenuResponse toResponse(User user, Menu menu, Restaurant restaurant,
                                    List<MenuOptionGroupResponse> optionGroups, RatingSummary rating) {
        return new MenuResponse(menu, restaurant, optionGroups, rating,
                ratingService.restaurantRating(restaurant.getId()),
                user.getId().equals(menu.getCreatedBy()),
                Permissions.canEdit(user, menu.getCreatedBy(), menu.getCompanyId()));
    }

    /** 레스토랑 이름 + 메뉴 평점을 각각 한 번의 쿼리로 붙입니다 (N+1 방지). */
    private List<MenuSummaryResponse> toSummaries(List<Menu> menus) {
        if (menus.isEmpty()) {
            return List.of();
        }
        List<UUID> restaurantIds = menus.stream().map(Menu::getRestaurantId).distinct().toList();
        Map<UUID, String> restaurantNames = restaurantRepository.findAllById(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
        Map<UUID, RatingSummary> ratings = ratingService.menuRatings(menus.stream().map(Menu::getId).toList());
        return menus.stream()
                .map(menu -> new MenuSummaryResponse(menu, restaurantNames.get(menu.getRestaurantId()),
                        ratings.getOrDefault(menu.getId(), RatingSummary.EMPTY)))
                .toList();
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
