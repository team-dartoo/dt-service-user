package dartoo.accountService.service;

import dartoo.accountService.domain.EmailVerificationToken;
import dartoo.accountService.domain.enums.TokenPurpose;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.ErrorCode;
import dartoo.accountService.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    @Value("${mail.verification.activation-expire-minutes}")
    private long activationExpireMinutes;

    @Value("${mail.verification.password-reset-expire-minutes}")
    private long passwordResetExpireMinutes;

    @Value("${mail.base-url}")
    private String baseUrl;

    //의도적으로 짧은 시간 내 다량의 메일 재전송 요청하는 것을 방지
    private static final int RATE_LIMIT_MAX = 10;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 3600; //단위 시간

    //활성화 이메일 발송
    public void issueActivationEmail(String email){
        checkRateLimit(email,TokenPurpose.ACTIVATION);
        String token = createToken(email,TokenPurpose.ACTIVATION,activationExpireMinutes);
        String activationLink = baseUrl + "/email-activate?token=" + token;
        emailService.sendActivationEmail(email,activationLink,activationExpireMinutes);
    }

    //비밀번호 재설정 이메일 발송
    public void sendPasswordResetEmail(String email){
        checkRateLimit(email, TokenPurpose.RESET_PASSWORD);
        String token = createToken(email, TokenPurpose.RESET_PASSWORD, passwordResetExpireMinutes);
        String resetLink = baseUrl + "/password-reset?token=" + token;
        emailService.sendPasswordResetEmail(email, resetLink, passwordResetExpireMinutes);
    }

    //할당량 체크
    private void checkRateLimit(String email, TokenPurpose purpose){
        Instant since = Instant.now().minusSeconds(RATE_LIMIT_WINDOW_SECONDS);
        long count = tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(email,purpose,since);
        //이미 발송할 수 있을 만큼 보냈다면 예외
        if (count >= RATE_LIMIT_MAX) {
            throw new ApiException(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }
    }

    //토큰 검증 -> 성공 시 해당 이메일 주소 반환
    public String verifyToken(String token, TokenPurpose purpose){
        EmailVerificationToken record = findValidToken(token,purpose);
        record.markAsUsed();
        return record.getEmail();
    }

    //토큰 찾기
    private EmailVerificationToken findValidToken(String token, TokenPurpose purpose) {
        EmailVerificationToken record = tokenRepository
                .findByTokenAndPurpose(token,purpose)
                .orElseThrow(()->new ApiException(ErrorCode.EMAIL_TOKEN_NOT_FOUND));
        if(record.isUsed()){
            throw new ApiException(ErrorCode.EMAIL_TOKEN_ALREADY_USED);
        }
        if(record.getExpiredAt().isBefore(Instant.now())){
            throw new ApiException(ErrorCode.EMAIL_TOKEN_EXPIRED);
        }
        return record;
    }

    //만들어진 토큰을 DB에 저장
    private String createToken(String email, TokenPurpose purpose, long expireMinutes){
        String token = generateToken();
        Instant expiredAt = Instant.now().plusSeconds(expireMinutes* 60L);
        EmailVerificationToken record = EmailVerificationToken.builder()
                .email(email)
                .token(token)
                .purpose(purpose)
                .expiredAt(expiredAt)
                .build();
        return tokenRepository.save(record).getToken();
    }

    //Base 64 기반 토큰 생성
    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
