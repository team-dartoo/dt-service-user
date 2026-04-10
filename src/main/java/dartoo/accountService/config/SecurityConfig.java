package dartoo.accountService.config;

import dartoo.accountService.error.JwtAuthenticationEntryPoint;
import dartoo.accountService.security.oauth.CustomOAuth2UserService;
import dartoo.accountService.security.oauth.OAuthLoginFailureHandler;
import dartoo.accountService.security.oauth.OAuthLoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@EnableMethodSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtEntryPoint;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http, ServiceApiKeyFilter serviceApiKeyFilter) throws Exception {
        http
            .securityMatcher("/internal/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
            .addFilterBefore(serviceApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                            JwtAuthenticationConverter conv, CustomOAuth2UserService customOAuth2UserService,
                                            OAuthLoginSuccessHandler oAuthLoginSuccessHandler,
                                            OAuthLoginFailureHandler oAuthLoginFailureHandler) throws Exception {
        http.authorizeHttpRequests(reg->reg
                //나머지는 추후에 role별로 추가 설정
                .requestMatchers("/api/users/**").authenticated() //회원 정보 관련이니까
                .requestMatchers("/api/auth/**").permitAll() //로그인 관련이니까.
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() //oauth2 로그인 관련
                .requestMatchers("/actuator/health/**").permitAll() //헬스체크 관련
                .anyRequest().authenticated());
        http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)
                .jwtAuthenticationConverter(conv))); //자체 JwtAuthFilter.java 불필요하게 처리

        //JWT 인증 실패 예외 처리
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint));
        http.cors(Customizer.withDefaults());

        // OAuth2 Login 설정 (google / naver / kakao)
        // - /oauth2/authorization/{provider} 진입 → provider 로그인 페이지로 리다이렉트
        // - provider가 redirect_uri로 code 전달 → Spring이 토큰 교환 + 사용자 프로필 조회까지 자동 처리
        // - 조회한 사용자 프로필로 Authentication을 만들 때 어떤 User 객체를 쓸지(userService)
        //   그리고 인증 성공/실패 시 후처리(successHandler / failureHandler)를 여기서 지정
        http.oauth2Login(oauth -> oauth
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)   // provider별(profile 구조) userInfo → 우리 도메인 유저 정보로 매핑
                )
                .successHandler(oAuthLoginSuccessHandler)       // 소셜 로그인 성공 시 → 우리 JWT 발급
                .failureHandler(oAuthLoginFailureHandler)       // 실패 처리 → 401 JSON 응답
        );
        return http.build();
    }

    //CORS 관련 메서드
    @Bean
    CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET","POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    //클라이언트가 액세스 토큰을 들고 왔을 때 해독
    @Bean
    JwtDecoder jwtDecoder(JwtConfig cfg) {

        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder
                .withSecretKey(cfg.getAccessKey())
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter conv = new JwtGrantedAuthoritiesConverter();
        conv.setAuthoritiesClaimName("roles");
        conv.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(conv);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    //프런트엔드 서버 주소 확정시 활성화 -> WebMvcConfigurer 방식은 사용하지 않음
//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurer() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**")
//                        .allowedOrigins("https://your-frontend-domain.com") // RN WebView 로드 도메인
//                        .allowedMethods("GET", "POST", "PUT", "DELETE")
//                        .allowCredentials(true); // 쿠키 전송 허용
//            }
//        };
//    }

    /** WebMvcConfigurer 방식을 사용하지 않는 이유
     * HTTP 요청 -> Spring Security Filter Chain -> DispatcherServlet -> Spring MVC -> Controller
     * WebMvcConfigurer 레이어는 MVC 레이어에서 작동하고, CorsConfigurationSource는 SecurityFilter 레이어에서 작동한다.
     * JWT 인증 실패나 허용되지 않은 요청은 Security Filter에서 MVC 레이어에 도달하기도 전에 차단된다.
     *
     * 브라우저는 본 API 요청을 보내기 전에 OPTIONS 메서드로 먼저 보내도 되는지 허락을 구한다.
     * 이 사전 요청(프리플라이트)에는 Authorization 헤더가 없기 때문에, WebMvcConfigurer 방식에서는
     * Security Filter의 JWT 인증 필터에서 401로 차단되어 MVC 레이어까지 도달하지 못한다.
     *
     * CorsConfigurationSource 빈을 등록하면 Security Filter Chain 내부에 CorsFilter가 추가되며,
     * JWT 인증 필터보다 먼저 실행되어 프리플라이트 요청을 정상적으로 처리한다.
     * http.cors(Customizer.withDefaults())는 컨텍스트에서 CorsConfigurationSource 빈을 자동으로 찾아 적용한다.
     */

}
/**
 * /api/auth/login (permitAll)
 *   -> AuthenticationManager.authenticate(email, password)
 *     -> JpaUserDetailsService.loadUserByUsername(email)
 *        -> DB에서 유저/해시 로드, 권한 ROLE_USER 구성
 *     <- 인증 성공 (Authentication with UserDetails)
 *   -> TokenService.issueTokens(UserDetails) : AT + RT 발급
 *   <- 응답: body(AT), Set-Cookie(HttpOnly RT)
 *
 * 보호 API 요청 (Authorization: Bearer AT)
 *   -> Resource Server JWT Filter
 *      -> JwtDecoder(HS256)로 AT 검증
 *      -> JwtAuthenticationConverter가 principal/authorities 구성
 *      -> SecurityContextHolder에 Authentication 저장
 *   -> Controller/Service: SecurityContext.getAuthentication().getName()
 *
 * /api/auth/refresh (permitAll)
 *   -> 쿠키/헤더의 RT 검증 + 회전
 *   <- 새 AT(body) + 새 RT(Set-Cookie)
 *
 * /api/auth/logout (permitAll)
 *   -> 해당 RT revoke
 *   <- 쿠키 만료 Set-Cookie
 */