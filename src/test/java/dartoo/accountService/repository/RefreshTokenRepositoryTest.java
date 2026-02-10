package dartoo.accountService.repository;

import dartoo.accountService.domain.RefreshToken;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.domain.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser;

    //시나리오별 토큰
    private RefreshToken activeToken_Mobile1;
    private RefreshToken activeToken_Mobile2;
    private RefreshToken rotatedToken1_Mobile1;
    private RefreshToken rotatedToken2_Mobile1;
    private RefreshToken revokedToken;
    private RefreshToken expiredToken;
    private RefreshToken revokedAndExpiredToken;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .userEmail("user1@test.com")
                .password("password123")
                .nickname("tester1")
                .role(Role.USER)
                .build();
        entityManager.persist(testUser);

        Instant now = Instant.now();

        //활성화된 모바일 리프레시 토큰
        activeToken_Mobile1 = RefreshToken.builder()
                .userEntity(testUser)
                .token("mobile_active_token_hash_abc123")
                .createdAt(now.minus(Duration.ofDays(1)))
                .expiredAt(now.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();
        entityManager.persist(activeToken_Mobile1);

        //활성화된 다른 모바일 기기 리프레시 토큰
        activeToken_Mobile2 = RefreshToken.builder()
                .userEntity(testUser)
                .token("mobile_active_token_hash_xyz789")
                .createdAt(now.minus(Duration.ofDays(1)))
                .expiredAt(now.plus(Duration.ofDays(13)))
                .revokedAt(null)
                .rotatedAt(null)
                .did("device-mobile-uuid-002")
                .userAgent("Dartoo/1.0 (Samsung Galaxy S23; Android 14)")
                .build();
        entityManager.persist(activeToken_Mobile2);
        
        //아이폰에서 발급되었다가 회전된 리프레시 토큰 - 5일전 로그인
        rotatedToken1_Mobile1 = RefreshToken.builder()
                .userEntity(testUser)
                .token("rotated_mobile_token_hash_abc123")
                .createdAt(now.minus(Duration.ofDays(5)))
                .expiredAt(now.plus(Duration.ofDays(9)))
                .revokedAt(now.minus(Duration.ofDays(2)))
                .rotatedAt(now.minus(Duration.ofDays(2)))
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();

        entityManager.persist(rotatedToken1_Mobile1);

        //아이폰에서 발급된, 2일전 로그인으로 가장 최근 이전에 회전된 로그인
        rotatedToken2_Mobile1 = RefreshToken.builder()
                .userEntity(testUser)
                .token("rotated_mobile_token_hash_xyz789")
                .createdAt(now.minus(Duration.ofDays(2)))
                .expiredAt(now.plus(Duration.ofDays(12)))
                .revokedAt(now.minus(Duration.ofDays(1)))
                .rotatedAt(now.minus(Duration.ofDays(1)))
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();

        entityManager.persist(rotatedToken2_Mobile1);

        //예전에 이미 로그아웃으로 revoke된 리프레시 토큰 (회전과 만료는 안됨)
        revokedToken = RefreshToken.builder()
                .userEntity(testUser)
                .token("old_mobile_revoked_token_hash_def456")
                .createdAt(now.minus(Duration.ofDays(13)))
                .expiredAt(now.plus(Duration.ofDays(1)))
                .revokedAt(now.minus(Duration.ofDays(13)).plus(Duration.ofMinutes(10)))
                .rotatedAt(null)
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();
        entityManager.persist(revokedToken);

        //로그아웃을 안하고 앱을 종료한 뒤, 자연스럽게 이미 만료된 리프레시 토큰
        expiredToken = RefreshToken.builder()
                .userEntity(testUser)
                .token("expired_mobile_token_hash_ghi789")
                .createdAt(now.minus(Duration.ofDays(15)))
                .expiredAt(now.minus(Duration.ofDays(1)))
                .revokedAt(null)
                .rotatedAt(null)
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();

        entityManager.persist(expiredToken);
        //만료도 되고, 로그아웃해서 revoke도 된 토큰
        revokedAndExpiredToken = RefreshToken.builder()
                .userEntity(testUser)
                .token("revoked_expired_mobile_token_hash_jkl012")
                .createdAt(now.minus(Duration.ofDays(80)))
                .expiredAt(now.minus(Duration.ofDays(66)))
                .revokedAt(now.minus(Duration.ofDays(80)).plus(Duration.ofMinutes(10)))
                .rotatedAt(null)
                .did("device-mobile-uuid-001")
                .userAgent("Dartoo/1.0 (iPhone; iOS 17.0)")
                .build();

        entityManager.persist(revokedAndExpiredToken);

        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("해당 사용자의 리프레시 토큰 모두를 삭제하기")
    @Test
    void deleteAllByUserEntity() {
        //given
        List<RefreshToken> before = refreshTokenRepository.findAll();
        assertThat(before).hasSize(7);
        //when
        refreshTokenRepository.deleteAllByUserEntity(testUser);
        entityManager.flush();
        //then
        List<RefreshToken> after = refreshTokenRepository.findAll();
        assertThat(after).isEmpty();
    }

    @DisplayName("해당 사용자의 리프레시 토큰 중 만료된 토큰을 삭제")
    @Test
    void deleteAllByUserEntityAndExpiredAtBefore() {
        //given
        Instant now = Instant.now();
        //when
        refreshTokenRepository.deleteAllByUserEntityAndExpiredAtBefore(testUser,now);
        entityManager.flush();
        //then
        List<RefreshToken> after = refreshTokenRepository.findAll();
        assertThat(after).hasSize(5);
        assertThat(after).allMatch(rt->rt.getExpiredAt().isAfter(now));
    }

    @DisplayName("해당 사용자의 현재 유효한 리프레시 토큰을 조회하기")
    @Test
    void findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter() {
        //given
        Instant now = Instant.now();
        //when
        List<RefreshToken> result = refreshTokenRepository.findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter(testUser,now);
        //then
        assertThat(result).hasSize(2); //첫 2개만 유효함
    }

    @DisplayName("해당 사용자의 특정 리프레시 토큰 정보를 조회하기")
    @Test
    void findByUserEntityAndToken() {
        //given
        String target= "mobile_active_token_hash_abc123";
        //when
        RefreshToken result = refreshTokenRepository.findByUserEntityAndToken(testUser,"mobile_active_token_hash_abc123").get();
        //then
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo(target);
        //다른 객체로 인식해 객체끼리 비교한 에러가 난다.
        assertThat(result.getUserEntity().getUserEmail()).isEqualTo(testUser.getUserEmail());
        assertThat(result.getRevokedAt()).isNull();
        assertThat(result.getUserAgent()).isEqualTo("Dartoo/1.0 (iPhone; iOS 17.0)");
        assertThat(result.getDid()).isEqualTo("device-mobile-uuid-001");
    }

    @DisplayName("해당 사용자의 특정 기기의 유효한 토큰을 조회하기")
    @Test
    void findAllByUserEntityAndDidAndRevokedAtIsNullAndExpiredAtAfter() {
        //given
        String did = "device-mobile-uuid-001";
        Instant now = Instant.now();
        //when
        List<RefreshToken> result = refreshTokenRepository.findAllByUserEntityAndDidAndRevokedAtIsNullAndExpiredAtAfter(testUser,did,now);
        //then
        assertThat(result).hasSize(1); //첫번째 것만
        assertThat(result.get(0).getDid()).isEqualTo(did);
        assertThat(result.get(0).getExpiredAt()).isAfter(now);
        assertThat(result.get(0).getRevokedAt()).isNull();
        assertThat(result.get(0).getUserAgent()).isEqualTo("Dartoo/1.0 (iPhone; iOS 17.0)");
    }

    @DisplayName("이미 만료된 리프레시 토큰을 모두 삭제하기")
    @Test
    void deleteByExpiredAtBefore() {
        //given
        Instant now = Instant.now();
        //when
        long deleted = refreshTokenRepository.deleteByExpiredAtBefore(now);
        //then
        assertThat(deleted).isEqualTo(2); //맨 마지막 2개
        assertThat(refreshTokenRepository.findAll()).hasSize(5);

        List<RefreshToken> after = refreshTokenRepository.findAll();
        assertThat(after).allMatch(rt->rt.getExpiredAt().isAfter(now));
    }

    //엔티티에 있는 rotate와 revoke 메서드 테스트

    @DisplayName("해당 리프레시 토큰을 회전시키기")
    @Test
    void rotateRefreshToken() {
        //given - 현재 active한 첫번째 토큰을 회전시키기
        Long tokenId = activeToken_Mobile1.getId();
        RefreshToken before = refreshTokenRepository.findById(tokenId).get();
        Instant now = Instant.now();
        //when
        before.rotate(now);
        entityManager.flush();
        entityManager.clear();
        //then
        RefreshToken after = refreshTokenRepository.findById(tokenId).get();
        //Instant와 DB 저장시의 시간 오차 관련 허용
        //asserThat(after.getRotatedAt()).isEqualTo(now); -> 틀렸다고 판정
        assertThat(after.getRotatedAt()).isCloseTo(now, within(1, ChronoUnit.MICROS));
    }

    @DisplayName("해당 리프레시 토큰을 revoke하기")
    @Test
    void revokeRefreshToken() {
        //given
        Long tokenId = activeToken_Mobile1.getId();
        RefreshToken before = refreshTokenRepository.findById(tokenId).get();
        Instant now = Instant.now();
        //when
        before.revoke(now);
        entityManager.flush();
        entityManager.clear();
        //then
        RefreshToken after = refreshTokenRepository.findById(tokenId).get();
        assertThat(after.getRevokedAt()).isCloseTo(now, within(1, ChronoUnit.MICROS));
    }
}