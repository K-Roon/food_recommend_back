package com.kroon.food_recommend_back.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <Firebase ID Token> 검증 필터.
 * 검증 성공 시 SecurityContext에 principal = uid로 인증 정보를 채웁니다.
 *
 * 주의: 여기서는 "로그인한 사람인가"만 확인합니다. role/company_id 기반의
 * 실제 데이터 접근 제어는 RlsContextService.bootstrap() + Postgres RLS가
 * 담당합니다 (필터 시점엔 아직 그 값을 모르기 때문).
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
            return;
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(header.substring(7));

            var authentication = new UsernamePasswordAuthenticationToken(
                    decoded.getUid(), null, List.of() // 권한(role)은 이 시점엔 모르므로 빈 리스트
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        } finally {
            SecurityContextHolder.clearContext(); // Tomcat 스레드 재사용 대비 필수
        }
    }
}
