package com.translationapp.config;

import com.translationapp.im.security.ImUserPrincipal;
import com.translationapp.util.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements Filter {

    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        
        // 只对REQUEST类型的请求进行认证，跳过ERROR、FORWARD、INCLUDE、ASYNC等分发类型
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String path = request.getRequestURI();
        
        // 允许公开端点无需认证
        if (path.startsWith("/api/auth/") || path.startsWith("/api/public/") || path.startsWith("/api/docs") || path.startsWith("/api/images/") || path.startsWith("/api/crawl/images/") || path.startsWith("/api/crawl/assets/") || path.startsWith("/api/crawl/docs") || path.startsWith("/api/crawl/external") || path.startsWith("/h2-console/") || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractAllClaims(token).get("role", String.class);
                Long userId = jwtUtil.extractUserId(token);
                
                if (username != null && jwtUtil.validateToken(token, username)) {
                    List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                    );
                    
                    ImUserPrincipal principal = new ImUserPrincipal(userId, username, role);
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // 认证失败，不设置SecurityContext，让Spring Security的AuthorizationFilter处理401
                SecurityContextHolder.clearContext();
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
