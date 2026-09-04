package dartoo.accountService.service;

import dartoo.accountService.domain.EmailVerificationToken;
import dartoo.accountService.domain.enums.TokenPurpose;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    EmailVerificationTokenRepository tokenRepository;
    @Mock
    EmailService emailService;

    @InjectMocks
    EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailVerificationService, "activationExpireMinutes", 30L);
        ReflectionTestUtils.setField(emailVerificationService, "passwordResetExpireMinutes", 15L);
        ReflectionTestUtils.setField(emailVerificationService, "baseUrl", "http://localhost:8080");
    }

    // ── issueActivationEmail ──────────────────────────────────────────────────

    @Test
    @DisplayName("인증 이메일 발송 성공")
    void issueActivationEmail_success() {
        // given
        given(tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(
                eq("test@test.com"), eq(TokenPurpose.ACTIVATION), any()))
                .willReturn(0L);
        given(tokenRepository.save(any(EmailVerificationToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // when
        emailVerificationService.issueActivationEmail("test@test.com");
        // then
        then(emailService).should().sendActivationEmail(
                eq("test@test.com"),
                argThat(url -> url.contains("/email-activate?token=")),
                eq(30L));
    }

    @Test
    @DisplayName("인증 이메일 발송 - 1시간 내 10회 초과 시 EMAIL_SEND_LIMIT_EXCEEDED 예외")
    void issueActivationEmail_rateLimitExceeded() {
        // given
        given(tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(
                eq("test@test.com"), eq(TokenPurpose.ACTIVATION), any()))
                .willReturn(10L);
        // when, then
        assertThatThrownBy(() -> emailVerificationService.issueActivationEmail("test@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EMAIL_SEND_LIMIT_EXCEEDED);
        then(tokenRepository).should(never()).save(any());
        then(emailService).should(never()).sendActivationEmail(any(), any(), anyLong());
    }

    // ── sendPasswordResetEmail ────────────────────────────────────────────────

    @Test
    @DisplayName("비밀번호 재설정 이메일 발송 성공")
    void sendPasswordResetEmail_success() {
        // given
        given(tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(
                eq("test@test.com"), eq(TokenPurpose.RESET_PASSWORD), any()))
                .willReturn(0L);
        given(tokenRepository.save(any(EmailVerificationToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // when
        emailVerificationService.sendPasswordResetEmail("test@test.com");
        // then
        then(emailService).should().sendPasswordResetEmail(
                eq("test@test.com"),
                argThat(url -> url.contains("/password-reset?token=")),
                eq(15L));
    }

    @Test
    @DisplayName("비밀번호 재설정 이메일 발송 - 1시간 내 10회 초과 시 EMAIL_SEND_LIMIT_EXCEEDED 예외")
    void sendPasswordResetEmail_rateLimitExceeded() {
        // given
        given(tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(
                eq("test@test.com"), eq(TokenPurpose.RESET_PASSWORD), any()))
                .willReturn(10L);
        // when, then
        assertThatThrownBy(() -> emailVerificationService.sendPasswordResetEmail("test@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EMAIL_SEND_LIMIT_EXCEEDED);
        then(tokenRepository).should(never()).save(any());
        then(emailService).should(never()).sendPasswordResetEmail(any(), any(), anyLong());
    }

    // ── verifyToken ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("토큰 검증 성공 - 이메일 반환 및 used 처리")
    void verifyToken_success() {
        // given
        EmailVerificationToken token = EmailVerificationToken.builder()
                .email("test@test.com")
                .token("valid-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(Instant.now().plusSeconds(1800))
                .build();
        given(tokenRepository.findByTokenAndPurpose("valid-token", TokenPurpose.ACTIVATION))
                .willReturn(Optional.of(token));
        // when
        String email = emailVerificationService.verifyToken("valid-token", TokenPurpose.ACTIVATION);
        // then
        assertThat(email).isEqualTo("test@test.com");
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    @DisplayName("토큰 검증 실패 - 존재하지 않는 토큰이면 EMAIL_TOKEN_NOT_FOUND 예외")
    void verifyToken_notFound() {
        // given
        given(tokenRepository.findByTokenAndPurpose("invalid-token", TokenPurpose.ACTIVATION))
                .willReturn(Optional.empty());
        // when, then
        assertThatThrownBy(() -> emailVerificationService.verifyToken("invalid-token", TokenPurpose.ACTIVATION))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EMAIL_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("토큰 검증 실패 - 이미 사용된 토큰이면 EMAIL_TOKEN_ALREADY_USED 예외")
    void verifyToken_alreadyUsed() {
        // given
        EmailVerificationToken token = EmailVerificationToken.builder()
                .email("test@test.com")
                .token("used-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(Instant.now().plusSeconds(1800))
                .build();
        token.markAsUsed();
        given(tokenRepository.findByTokenAndPurpose("used-token", TokenPurpose.ACTIVATION))
                .willReturn(Optional.of(token));
        // when, then
        assertThatThrownBy(() -> emailVerificationService.verifyToken("used-token", TokenPurpose.ACTIVATION))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EMAIL_TOKEN_ALREADY_USED);
    }

    @Test
    @DisplayName("토큰 검증 실패 - 만료된 토큰이면 EMAIL_TOKEN_EXPIRED 예외")
    void verifyToken_expired() {
        // given
        EmailVerificationToken token = EmailVerificationToken.builder()
                .email("test@test.com")
                .token("expired-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(Instant.now().minusSeconds(1))
                .build();
        given(tokenRepository.findByTokenAndPurpose("expired-token", TokenPurpose.ACTIVATION))
                .willReturn(Optional.of(token));
        // when, then
        assertThatThrownBy(() -> emailVerificationService.verifyToken("expired-token", TokenPurpose.ACTIVATION))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EMAIL_TOKEN_EXPIRED);
    }
}
