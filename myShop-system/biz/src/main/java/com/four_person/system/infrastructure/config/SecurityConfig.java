package com.four_person.system.infrastructure.config;

import com.four_person.system.infrastructure.security.JwtAuthenticationFilter;
import com.four_person.system.infrastructure.security.RestAccessDeniedHandler;
import com.four_person.system.infrastructure.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 无状态安全配置：JWT 认证 + 基于角色的接口授权。
 * <p>
 * 角色映射：角色编码 {@code admin} → 权限 {@code ROLE_ADMIN}；
 * 所属端 {@code PLATFORM} → 权限 {@code ROLE_PLATFORM}。
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证入口
                        .requestMatchers("/auth/register", "/auth/login", "/auth/email-code",
                                "/auth/password/forgot", "/auth/password/reset").permitAll()
                        // 服务间内部调用（生产环境应通过内网或内部令牌保护）
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/error", "/actuator/**").permitAll()
                        // 个人中心：任何登录用户都可以访问自己
                        .requestMatchers("/users/me", "/users/me/**").authenticated()
                        // 平台用户管理：仅平台管理员
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        // 角色与菜单配置：平台端/商家端各自管理本端角色，
                        // 细粒度权限（角色管理、配置菜单）交由 AccessGuard 按权限码 + 所属端判定
                        .requestMatchers("/roles/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/member/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JwtAuthenticationFilter 是 Spring Bean，Boot 会自动把它注册成普通 Servlet 过滤器；
     * 这里禁用自动注册，避免它在安全过滤链之外再执行一次。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 开发阶段允许跨域；生产环境应由网关统一处理。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
