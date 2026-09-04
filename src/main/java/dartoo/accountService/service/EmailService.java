package dartoo.accountService.service;

import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    //단순 이메일 전송만 담당하는 서비스

    //Spring Boot에서 제공하는 메일 발송용 객체
    //application.yml에 설정한 spring.mail.* 값을 기반으로 SMTP 서버에 접속한다.
    private final JavaMailSender mailSender;
    //Thymeleaf HTML 템플릿을 문자열 HTML로 렌더링하기 위한 객체.
    //activationLink, expireMinutes 같은 변수를 넣어
    //실제 이메일 본문 HTML을 만든다.
    private final TemplateEngine templateEngine;

    @Value("${mail.from}")
    private String from;

    private static final String activationEmailName = "[Dartoo] 계정 이메일 인증";
    private static final String passwordResetEmailName = "[Dartoo] 비밀번호 재설정";

    /*
     * <이메일 전송 과정>
     * 1. Thymeleaf 템플릿을 HTML 문자열로 렌더링
     * 2. MimeMessage 생성
     * 3. MimeMessageHelper로 From, To, Subject, Body 설정
     * 4. JavaMailSender가 Brevo SMTP relay 서버로 메일 발송 요청
     */
    public void sendActivationEmail(String to, String activationLink, long expireMinutes) {
        //Thymeleaf에 담아줄 변수를 Context에 담는다
        Context context = new Context();
        context.setVariable("activationLink", activationLink);   // activation.html의 ${activationLink}
        context.setVariable("expireMinutes", expireMinutes);     // activation.html의 ${expireMinutes}
        //해당 템플릿 경로에 컨텍스트 주입
        String html = templateEngine.process("email/activation", context);
        sendEmail(to, "[Dartoo] 계정 이메일 인증", html);
    }

    public void sendPasswordResetEmail(String to, String passwordResetLink, long expireMinutes) {
        //Thymeleaf에 담아줄 변수를 Context에 담는다
        Context context = new Context();
        context.setVariable("resetLink", passwordResetLink);     // password-reset.html의 ${resetLink}
        context.setVariable("expireMinutes", expireMinutes);     // password-reset.html의 ${expireMinutes}
        //해당 템플릿 경로에 컨텍스트 주입
        String html = templateEngine.process("email/password-reset", context);
        sendEmail(to, "[Dartoo] 비밀번호 재설정", html);
    }

    public void sendEmail(String to, String subject, String html) {
        try {
            //이메일 한 통을 표현하는 Jakarta Mail 객체.
            MimeMessage message = mailSender.createMimeMessage();
            //MimeMessage를 더 쉽게 다루기 위한 Spring의 helper 클래스.
            //두번째 인자가 false면 첨부파일이나 inline image 없는 단순 메일
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);

            //이메일 본문 설정. 두 번째 인자 true면 HTML 이메일로 발송
            helper.setText(html, true);
            //실제 메시지 발송
            mailSender.send(message);
        } catch (MessagingException e){
            log.error("이메일 발송 실패: to={}, subject={}", to, subject, e);
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

}
