package dartoo.accountService.config;

import dartoo.accountService.error.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@EnableMethodSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtEntryPoint;

    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                            JwtAuthenticationConverter conv) throws Exception {
        http.authorizeHttpRequests(reg->reg
                //나머지는 추후에 role별로 추가 설정
                .requestMatchers("/api/users/**").authenticated() //회원 정보 관련이니까
                .requestMatchers("/api/auth/**").permitAll() //로그인 관련이니까.
                .anyRequest().authenticated());
        http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)
                .jwtAuthenticationConverter(conv))); //자체 JwtAuthFilter.java 불필요하게 처리

        //JWT 인증 실패 예외 처리
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint));
        http.cors(Customizer.withDefaults()); //나중에 추가 설정

        return http.build();
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

    //프런트엔드 서버 주소 확정시 활성화
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