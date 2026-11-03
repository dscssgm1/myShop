package com.four_person.system.infrastructure.security;

import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 解析 {@code Authorization: Bearer <token>}，校验用户状态并写入 SecurityContext。
 * <p>
 * 每次请求都会回查用户状态，保证「禁用账号」立即生效；令牌无效时直接放行，
 * 由 {@link RestAuthenticationEntryPoint} 返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            authenticate(header.substring(PREFIX.length()).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = tokenProvider.parse(token);
            SysUser user = userRepository.findById(JwtTokenProvider.userIdOf(claims)).orElse(null);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return;
            }
            String groupCode = roleRepository.findByCode(user.getRoleCode())
                    .map(SysRole::getGroupCode)
                    .orElse(JwtTokenProvider.groupOf(claims));

            SecurityUser principal = new SecurityUser(
                    user.getId(), user.getUsername(), user.getNickname(), user.getRoleCode(), groupCode,
                    user.getShopId(), user.getCompanyId(), user.getCourierId(), JwtTokenProvider.sessionIdOf(claims));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities(user.getRoleCode(), groupCode));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 解析失败: {}", ex.getMessage());
        }
    }

    /**
     * 同时授予角色权限与所属端权限：
     * 例如角色 admin 获得 ROLE_ADMIN 与 ROLE_PLATFORM。
     */
    private List<SimpleGrantedAuthority> authorities(String roleCode, String groupCode) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(2);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalize(roleCode)));
        if (StringUtils.hasText(groupCode)) {
            SimpleGrantedAuthority groupAuthority = new SimpleGrantedAuthority("ROLE_" + normalize(groupCode));
            if (!authorities.contains(groupAuthority)) {
                authorities.add(groupAuthority);
            }
        }
        return authorities;
    }

    private String normalize(String code) {
        return code.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
