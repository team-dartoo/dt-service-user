package dartoo.accountService.service;

import dartoo.accountService.config.JwtConfig;
import dartoo.accountService.domain.RefreshToken;
import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.TokenResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.RefreshTokenRepository;
import dartoo.accountService.repository.UserEntityRepository;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.exceptions.misusing.UnnecessaryStubbingException;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static io.jsonwebtoken.SignatureAlgorithm.HS256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    //가짜 객체
    @Mock JwtConfig jwtConfig;
    @Mock TokenService tokenService;
    @Mock UserEntityRepository userRepo;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock HttpServletResponse response;

    //가짜 객체 주입 후 테스트
    @InjectMocks AuthService authService;

    private UserEntity testUser;
    private SecretKeySpec testRefreshKey;

    //시간 고정
    private MockedStatic<Instant> instantMock;
    private static Instant FIXED;

    @BeforeAll
    static void initFixed() {
        FIXED = Instant.now();
        assert FIXED != null; // sanity check
    }

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        //시간 설정
        instantMock = Mockito.mockStatic(Instant.class, Answers.CALLS_REAL_METHODS);
        instantMock.when(Instant::now).thenReturn(FIXED);

        //테스트 유저 설정
        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("encodedPass")
                .nickname("테스터")
                .role(Role.USER)
                .build();

        // JwtConfig Mock 기본 설정
        testRefreshKey = new SecretKeySpec("test-refresh-secret-key-with-minimum-256-bits".getBytes(), "HmacSHA256");
        if (testInfo.getTags().contains("needsJwtConfig")) {
            given(jwtConfig.getIssuer()).willReturn("dartoo");
            given(jwtConfig.getAccessTtlSeconds()).willReturn(3600L);
            given(jwtConfig.getRefreshTtlSeconds()).willReturn(1209600L);
            given(jwtConfig.getRefreshKey()).willReturn(testRefreshKey);
            given(jwtConfig.getRefreshPepper()).willReturn("Refresh-Pepper-with-minimum-256-bits");
        }
    }

    @AfterEach
    void tearDown() {
        instantMock.close(); // 꼭 닫아줘
    }

    @Tag("needsJwtConfig")
    @DisplayName("로그인 성공시 AccessToken과 RefreshToken 정상 발급")
    @Test
    public void loginIssueSuccess() {
        //given - testUser와 기존 RefreshToken을 DB에 저장
        String did = "test-did";
        String userAgent = "testUserAgent";
        String accessToken = "test-access-token";
        String refreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);
        given(userRepo.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));

        //테스트용 기존에 활성화되어있던 RefreshToken 2개
        RefreshToken oldRt1 = RefreshToken.builder()
                .userEntity(testUser)
                .token("old_refresh_token_hash_abc123")
                .did(did)
                .createdAt(FIXED.minus(Duration.ofDays(1)))
                .expiredAt(FIXED.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .userAgent(userAgent)
                .build();

        RefreshToken oldRt2 = RefreshToken.builder()
                .userEntity(testUser)
                .token("old_refresh_token_hash_abc123")
                .did(did)
                .createdAt(FIXED.minus(Duration.ofDays(2)))
                .expiredAt(FIXED.plus(Duration.ofDays(12)))
                .revokedAt(null)
                .rotatedAt(null)
                .userAgent(userAgent)
                .build();

        //반환값을 given으로 정의
        given(tokenService.createAccessToken(testUser.getUserEmail(),testUser.getNickname(),FIXED,testUser.getRole()))
                .willReturn(accessToken);
        given(tokenService.createRefreshToken(testUser.getUserEmail(),did,FIXED)).willReturn(refreshToken);
        given(refreshTokenRepository.findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter(testUser,FIXED))
                .willReturn(java.util.List.of(oldRt1,oldRt2));

        //when
        TokenResponseDto result = authService.loginIssue(testUser.getUserEmail(), did, userAgent, response);
        //then
        //1. 응답 DTO에 정상적인 AccessToken과 RefreshToken 발급 여부 확인
        assertThat(result.getAccessToken()).isEqualTo(accessToken);
        assertThat(result.getRefreshToken()).isEqualTo(refreshToken);
        //2. 만료된 RefreshToken이 삭제되었는지 확인 (함수 호출만 확인, 삭제가 잘 되는지는 Repository 테스트에서 했기 때문에)
        then(refreshTokenRepository).should().deleteAllByUserEntityAndExpiredAtBefore(testUser,FIXED);
        //3. 기존에 활성화된 RefreshToken이 revoke 되었는지 확인
        assertThat(oldRt1.getRevokedAt()).isNotNull();
        assertThat(oldRt2.getRevokedAt()).isNotNull();
        //4. 새로운 RefreshToken이 DB에 잘 저장되었는지 확인
        //호출횟수 - oldRt1, oldRt2, refreshToken 3번 업데이트니까 3번 저장
        then(refreshTokenRepository).should(times(3)).save(any(RefreshToken.class));
        //새롭게 저장된 refreshToken 확인
        then(refreshTokenRepository).should().save(argThat(rt->
                rt.getUserEntity().equals(testUser) &&
                rt.getDid().equals(did) &&
                rt.getUserAgent().equals(userAgent) &&
                rt.getExpiredAt().isAfter(FIXED) &&
                rt.getRevokedAt() == null));
        //5. Http Response의 쿠키에 의도한 옵션대로 RefreshToken이 잘 담겼는지 확인. argThat으로 조건 확인 가능
        then(response).should().addHeader(eq("Set-Cookie"),argThat(cookie->
                cookie.contains("refresh_token="+refreshToken)&&
                cookie.contains("HttpOnly") &&
                cookie.contains("Secure")
        ));
    }

    private String createTestRefreshToken(String email, String did, Instant now) {
        return Jwts.builder()
                .setIssuer("dartoo")
                .setSubject(email)
                .claim("did", did)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(Duration.ofDays(14))))
                .signWith(testRefreshKey, HS256)
                .compact();
    }

    @DisplayName("DB에서 사용자를 찾을 수 없을 시, USER_NOT_FOUND 예외를 던진다")
    @Test
    public void loginIssueFail() throws UnnecessaryStubbingException {
        // Given - 사용자 DB에 존재하지 않는 이메일
        given(userRepo.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());

        // When, Then
        //존재하지 않는 이메일로 로그인 시도시, ApiException에 해당하는 USER_NOT_FOUND 에러를 반환
        assertThatThrownBy(() ->
                authService.loginIssue("unknown@test.com", "did", "agent", response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, USER_NOT_FOUND의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        //예외가 발생했으니 토큰 생성 로직은 실행되지 않아야 한다.
        then(tokenService).should(never()).createAccessToken(any(), any(), any(), any());
    }
}
