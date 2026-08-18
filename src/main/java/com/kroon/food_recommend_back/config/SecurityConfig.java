package com.kroon.food_recommend_back.config;

import com.kroon.food_recommend_back.filter.FirebaseAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * role 기반 세밀한 권한 체크(@PreAuthorize 등)는 지금 단계에서는 안 걸어뒀습니다.
 * role/company_id가 DB에만 있어서, 이 필터 체인 시점엔 아직 모르기 때문입니다
 * (RlsContextService.bootstrap()에서 그 값을 조회하고 나서야 알 수 있음).
 * 세밀한 권한 체크가 더 필요해지면, 로그인 성공 시 Firebase Custom Claims에
 * role/company_id를 심어두는 방식으로 업그레이드하는 걸 추천합니다 — 그러면
 * 이 필터에서 바로 GrantedAuthority를 채울 수 있어서 @PreAuthorize도 자연스럽게 됩니다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless REST API라 CSRF 토큰 불필요
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/api/signup").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
