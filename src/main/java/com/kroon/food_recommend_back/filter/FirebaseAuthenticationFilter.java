package com.kroon.food_recommend_back.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * SecurityConfig의 permitAll 목록(/actuator/health, /api/signup, /api/health/**)과
 * 반드시 같이 맞춰야 합니다 — SecurityConfig가 인증 없이 통과시키기로 한 경로라도
 * 이 필터가 먼저 Bearer 토큰을 강제하면 여기서 401이 나버립니다.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/actuator/health",
            "/api/health",
            "/api/signup"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        log.debug("FirebaseAuthenticationFilter 진입: {} {}", request.getMethod(), request.getRequestURI());

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // 헤더 값 자체는 토큰/자격증명일 수 있어서 로그에 남기지 않습니다.
            log.debug("Authorization 헤더 없음 또는 Bearer 형식 아님: {}", request.getRequestURI());
            writeUnauthorized(response, "로그인이 필요합니다.");
            return;
        }

        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(header.substring(7));
        } catch (Exception e) {
            log.warn("토큰 검증 실패: {}", e.getMessage());
            writeUnauthorized(response, "인증 토큰이 만료되었거나 올바르지 않습니다.");
            return;
        }

        // 토큰 검증과 요청 처리를 분리: 컨트롤러 쪽 예외가 "토큰 오류(401)"로 둔갑하지 않도록
        try {
            var authentication = new UsernamePasswordAuthenticationToken(decoded.getUid(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * sendError()를 쓰면 /error로 재디스패치되면서 Security 설정에 따라 403으로 바뀌어
     * 보일 수 있어서, GlobalExceptionHandler와 같은 ProblemDetail 모양의 JSON을 직접 씁니다.
     * Flutter(dio) 인터셉터는 401을 받으면 ID 토큰을 갱신해서 재시도하면 됩니다.
     */
    private static void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + detail + "\"}");
    }
}
