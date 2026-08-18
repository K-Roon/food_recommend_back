package com.kroon.food_recommend_back.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.kroon.food_recommend_back.dto.SignupRequest;
import com.kroon.food_recommend_back.dto.SignupResponse;
import com.kroon.food_recommend_back.entity.Company;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.CompanyRepository;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * 초대코드 기반 회원가입.
 * 1. invite_code로 회사 조회
 * 2. Firebase Admin SDK로 계정 생성 (비밀번호는 여기서만 다루고 DB엔 저장하지 않음)
 * 3. users row 생성 (role=general_user, status=active)
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        Company company = companyRepository.findByInviteCode(request.getInviteCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다."));

        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(request.getEmail())
                .setPassword(request.getPassword())
                .setEmailVerified(false);

        UserRecord firebaseUser;
        try {
            firebaseUser = FirebaseAuth.getInstance().createUser(createRequest);
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정 생성에 실패했습니다: " + e.getMessage());
        }

        try {
            User user = new User();
            user.setId(firebaseUser.getUid());
            user.setEmail(request.getEmail());
            user.setRole(User.Role.general_user);
            user.setCompanyId(company.getId());
            user.setStatus(User.Status.active);
            user.setCreatedAt(Instant.now());
            userRepository.save(user);
        } catch (Exception e) {
            // DB row 생성 실패 시 Firebase 계정도 같이 롤백 (signup atomicity 대응)
            try {
                FirebaseAuth.getInstance().deleteUser(firebaseUser.getUid());
            } catch (FirebaseAuthException ignored) {
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "회원가입 처리 중 오류가 발생했습니다.");
        }

        return new SignupResponse(firebaseUser.getUid(), firebaseUser.getEmail());
    }
}