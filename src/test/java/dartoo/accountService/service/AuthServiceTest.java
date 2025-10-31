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
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static io.jsonwebtoken.SignatureAlgorithm.HS256;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    //가짜 객체
    @Mock JwtConfig jwtConfig;
    @Mock TokenService tokenService;
    @Mock UserEntityRepository userEntityRepository;
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
    public void setUp() {
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

        // Config는 테스트마다 일부만 쓰기 때문에 lenient 사용
        //가급적 지양해야 하지만, 테스트 메서드마다 사용하는 config가 전부 상이해서 이렇게 사용
        lenient().when(jwtConfig.getIssuer()).thenReturn("dartoo");
        lenient().when(jwtConfig.getAccessTtlSeconds()).thenReturn(3600L);
        lenient().when(jwtConfig.getRefreshTtlSeconds()).thenReturn(1209600L);
        lenient().when(jwtConfig.getRefreshKey()).thenReturn(testRefreshKey);
        lenient().when(jwtConfig.getRefreshPepper()).thenReturn("Refresh-Pepper-with-minimum-256-bits");
    }

    @AfterEach
    void tearDown() {
        instantMock.close();
    }

    //@Tag("needsJwtConfig")
    @DisplayName("로그인 성공시 AccessToken과 RefreshToken 정상 발급")
    @Test
    public void loginIssueSuccess() {
        //given - testUser와 기존 RefreshToken을 DB에 저장
        String did = "test-did";
        String userAgent = "testUserAgent";
        String accessToken = "test-access-token";
        String refreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);
        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));

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
        //실제 객체엔 assertThat, Mock 객체엔 then을 사용하여 테스트할 수 있다.
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

    @DisplayName("DB에서 사용자를 찾을 수 없을 시, USER_NOT_FOUND 예외를 던진다")
    @Test
    public void loginIssueFail() {
        // Given - 사용자 DB에 존재하지 않는 이메일
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());

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

    @DisplayName("Refresh Token 회전 성공 (일반 갱신)")
    @Test
    void refreshSuccessNormal(){
        //given
        String did = "test-did";
        String userAgent = "testUserAgent";
        String oldRefreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED.minus(Duration.ofSeconds(3000)));
        String newAccess = "new-access-token";
        String newRefreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);

        RefreshToken rt = RefreshToken.builder()
                .userEntity(testUser)
                .token(oldRefreshToken)
                .createdAt(FIXED.minus(Duration.ofDays(1)))
                .expiredAt(FIXED.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .did(did)
                .userAgent(userAgent)
                .build();

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findByUserEntityAndToken(eq(testUser),any())).willReturn(Optional.of(rt));
        given(tokenService.createAccessToken(testUser.getUserEmail(),testUser.getNickname(),FIXED,testUser.getRole())).willReturn(newAccess);
        given(tokenService.createRefreshTokenFixed(eq(testUser.getUserEmail()),eq(did),any())).willReturn(newRefreshToken);

        //when
        TokenResponseDto result = authService.refresh(oldRefreshToken,false,response);

        //then
        //1. 새로운 액세스 토큽 발급 확인
        assertThat(result.getAccessToken()).isEqualTo(newAccess);
        //2. 기존 RefreshToken을 Rotate + Revoke 했는지 확인
        assertThat(rt.getRotatedAt()).isNotNull();
        assertThat(rt.getRevokedAt()).isNotNull();
        //3. 관련 RefreshToken을 제대로 DB에 저장했는지 확인
        //refreshToken 생성시 만료 시간 정보가 잘 들어갔는지 확인 (제일 중요한 정보니까)
        then(refreshTokenRepository).should().save(argThat(saved ->
                saved.getExpiredAt().equals(rt.getExpiredAt())));
    }

    @DisplayName("Refresh Token 회전 성공 (자동 로그인)")
    @Test
    void refreshSuccessAutoLogin(){
        //given
        String did = "test-did";
        String userAgent = "testUserAgent";
        String oldRefreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED.minus(Duration.ofSeconds(3000)));
        String newAccess = "new-access-token";
        String newRefreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);

        RefreshToken rt = RefreshToken.builder()
                .userEntity(testUser)
                .token(oldRefreshToken)
                .createdAt(FIXED.minus(Duration.ofDays(1)))
                .expiredAt(FIXED.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .did(did)
                .userAgent(userAgent)
                .build();

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findByUserEntityAndToken(eq(testUser),any())).willReturn(Optional.of(rt));
        given(tokenService.createAccessToken(testUser.getUserEmail(),testUser.getNickname(),FIXED,testUser.getRole())).willReturn(newAccess);
        given(tokenService.createRefreshToken(eq(testUser.getUserEmail()),eq(did),any())).willReturn(newRefreshToken);

        //when
        TokenResponseDto result = authService.refresh(oldRefreshToken,true,response);

        //then
        //1. 새로운 액세스 토큽 발급 확인
        assertThat(result.getAccessToken()).isEqualTo(newAccess);
        //2. 기존 RefreshToken을 Rotate + Revoke 했는지 확인
        assertThat(rt.getRotatedAt()).isNotNull();
        assertThat(rt.getRevokedAt()).isNotNull();
        //3. 관련 RefreshToken을 제대로 DB에 저장했는지 확인
        //refreshToken 생성시 만료 시간 정보가 잘 들어갔는지 확인 (제일 중요한 정보니까)
        then(refreshTokenRepository).should().save(argThat(saved ->
                saved.getExpiredAt().equals(FIXED.plusSeconds(jwtConfig.getRefreshTtlSeconds()))));
    }

    @DisplayName("refresh 호출 시 토큰이 null / blank 인 경우 INVALID_REFRESH_TOKEN 에러 코드 호출")
    @Test
    public void refreshInvalidRefreshToken(){
        //given 생략
        //when, then
        assertThatThrownBy(() ->
                authService.refresh("", false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, INVALID_REFRESH_TOKEN의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", INVALID_REFRESH_TOKEN);
        assertThatThrownBy(() ->
                authService.refresh(null, true, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, INVALID_REFRESH_TOKEN의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", INVALID_REFRESH_TOKEN);
    }

    @DisplayName("refresh 호출 시 parseAndValidateRefresh 에서 INVALID_REFRESH_TOKEN_JWT 에러 코드 호출")
    @Test
    public void refreshInvalidRefreshTokenJwt(){
        //given
        String invalidRefreshToken = createRefreshTokenDifferentIssuer(testUser.getUserEmail(), "test-did", FIXED);
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(invalidRefreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, INVALID_REFRESH_TOKEN_JWT의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", INVALID_REFRESH_TOKEN_JWT);
    }

    @DisplayName("refresh 호출 시 parseAndValidateRefresh 에서 REFRESH_TOKEN_EXPIRED_JWT 에러 코드 호출")
    @Test
    public void refreshRefreshTokenExpiredJwt(){
        //given
        String expiredRefreshToken = createTestRefreshToken(testUser.getUserEmail(), "test-did", FIXED.minus(Duration.ofDays(15)));
        //when,then
        assertThatThrownBy(() ->
                authService.refresh(expiredRefreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, REFRESH_TOKEN_EXPIRED_JWT의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", REFRESH_TOKEN_EXPIRED_JWT);
    }

    @DisplayName("refresh 호출 시 deviceId가 null이거나 빈 문자열인 경우 INVALID_DEVICE_ID 에러 코드 호출")
    @Test
    public void refreshInvalidDeviceId(){
        //given
        String invalidDidRefreshTokenNull = createTestRefreshToken(testUser.getUserEmail(), null, FIXED);
        String invalidDidRefreshTokenBlank = createTestRefreshToken(testUser.getUserEmail(), "", FIXED);
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(invalidDidRefreshTokenNull, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, INVALID_DEVICE_ID의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", INVALID_DEVICE_ID);
        assertThatThrownBy(() ->
                authService.refresh(invalidDidRefreshTokenBlank, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, INVALID_DEVICE_ID의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", INVALID_DEVICE_ID);
    }

    @DisplayName("refresh 호출 시 사용자가 없어 USER_NOT_FOUND 에러 코드 호출")
    @Test
    public void refreshUserNotFound(){
        //given
        String did = "test-did";
        String wrongUser = "notavailable@test.com";
        String wrongUserRefreshToken = createTestRefreshToken(wrongUser, did, FIXED);

        given(userEntityRepository.findByUserEmail(wrongUser)).willReturn(Optional.empty());
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(wrongUserRefreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, USER_NOT_FOUND의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @DisplayName("refresh 호출 시 DB에 저장 정보가 없어 REFRESH_TOKEN_NOT_FOUND 에러 코드 호출")
    @Test
    public void refreshRefreshTokenNotFound(){
        //given
        String fakeRefreshToken = createTestRefreshToken(testUser.getUserEmail(), "test-did", FIXED);

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findByUserEntityAndToken(eq(testUser),any())).willReturn(Optional.empty());
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(fakeRefreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, REFRESH_TOKEN_NOT_FOUND의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", REFRESH_TOKEN_NOT_FOUND);
    }

    @DisplayName("refresh 호출 시 이미 rotated된 경우 REFRESH_TOKEN_ALREADY_ROTATED 에러 코드 호출")
    @Test
    public void refreshRefreshTokenAlreadyRotated(){
        //given
        String did = "test-did";
        String userAgent = "testUserAgent";
        String refreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);
        RefreshToken rt = RefreshToken.builder()
                .userEntity(testUser)
                .token(refreshToken)
                .did(did)
                .createdAt(FIXED)
                .expiredAt(FIXED.plus(Duration.ofDays(14)))
                .rotatedAt(FIXED.minus(Duration.ofSeconds(300))) //이미 회전됨
                .revokedAt(null)
                .userAgent(userAgent)
                .build();

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findByUserEntityAndToken(eq(testUser),any())).willReturn(Optional.of(rt));
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(refreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, REFRESH_TOKEN_ALREADY_ROTATED의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", REFRESH_TOKEN_ALREADY_ROTATED);

    }


    @DisplayName("refresh 호출 시 해당 정보를 기반으로 DB에서 불러온 토큰이 이미 만료된 경우 REFRESH_TOKEN_EXPIRED 에러 코드 호출")
    @Test
    public void refreshRefreshTokenExpired(){
        //given
        String did = "test-did";
        String userAgent = "testUserAgent";
        String refreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);
        RefreshToken rt = RefreshToken.builder()
                .userEntity(testUser)
                .token(refreshToken)
                .did(did)
                .createdAt(FIXED)
                .expiredAt(FIXED.minus(Duration.ofSeconds(60))) //이미 만료됨
                .rotatedAt(null)
                .revokedAt(null)
                .userAgent(userAgent)
                .build();

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findByUserEntityAndToken(eq(testUser),any())).willReturn(Optional.of(rt));
        //when, then
        assertThatThrownBy(() ->
                authService.refresh(refreshToken, false, response))
                .isInstanceOf(ApiException.class)
                //ApiException 클래스가 errorCode 필드를 가지고 있고, REFRESH_TOKEN_EXPIRED의 값을 가지는지 확인
                .hasFieldOrPropertyWithValue("errorCode", REFRESH_TOKEN_EXPIRED);
    }

    //@Tag("needsJwtConfig")
    @DisplayName("로그아웃 시 해당 기기의 모든 RefreshToken을 revoke한다")
    @Test
    public void revokeRefreshTokenSuccess(){
        //given
        String did = "test-did";
        String userAgent = "testUserAgent";
        String refreshToken = createTestRefreshToken(testUser.getUserEmail(), did, FIXED);

        RefreshToken rt1 = RefreshToken.builder()
                .userEntity(testUser)
                .token("rt1_hash_abc123")
                .did(did)
                .userAgent(userAgent)
                .createdAt(FIXED.minus(Duration.ofDays(1)))
                .expiredAt(FIXED.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .build();

        RefreshToken rt2 = RefreshToken.builder()
                .userEntity(testUser)
                .token("rt2_hash_abc123")
                .did(did)
                .userAgent(userAgent)
                .createdAt(FIXED.minus(Duration.ofDays(5)))
                .expiredAt(FIXED.plus(Duration.ofDays(9)))
                .revokedAt(null)
                .rotatedAt(null)
                .build();

        given(userEntityRepository.findByUserEmail(testUser.getUserEmail())).willReturn(Optional.of(testUser));
        given(refreshTokenRepository.findAllByUserEntityAndDidAndRevokedAtIsNullAndExpiredAtAfter(testUser,did,FIXED))
                .willReturn(java.util.List.of(rt1,rt2));

        //when
        authService.revokeRefreshToken(refreshToken,did);

        //then
        assertThat(rt1.getRevokedAt()).isNotNull();
        assertThat(rt2.getRevokedAt()).isNotNull();
        then(refreshTokenRepository).should(times(2)).save(any(RefreshToken.class));
    }

    @DisplayName("리프레시 토큰이 비어있거나 없을 경우 아무 일도 발생하지 않는다")
    @Test
    public void revokeRefreshTokenEmpty(){
        //assertThatCode - 코드의 실행 여부를 테스트해준다
        assertThatCode(()->authService.revokeRefreshToken("","test-did"))
        .doesNotThrowAnyException();
        then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
        assertThatCode(()->authService.revokeRefreshToken(null,"test-did"))
                .doesNotThrowAnyException();
        then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
    }

    //유틸리티 메서드들
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

    private String createRefreshTokenDifferentIssuer(String email, String did, Instant now) {
        return Jwts.builder()
                .setIssuer("Wrong-Issuer")
                .setSubject(email)
                .claim("did", did)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(Duration.ofDays(14))))
                .signWith(testRefreshKey, HS256)
                .compact();
    }
}
