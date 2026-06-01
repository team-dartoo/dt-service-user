package dartoo.accountService.web;

import dartoo.accountService.domain.enums.TokenPurpose;
import dartoo.accountService.dto.account.EmailActivationRequestDto;
import dartoo.accountService.dto.account.PasswordResetConfirmDto;
import dartoo.accountService.dto.account.PasswordResetRequestDto;
import dartoo.accountService.service.EmailVerificationService;
import dartoo.accountService.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class EmailController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/email/activation")
    public ResponseEntity<Void> resendActivationEmail(@RequestBody EmailActivationRequestDto dto) {
        emailVerificationService.issueActivationEmail(dto.getEmail());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/email/activate")
    public ResponseEntity<Void> activateEmail(@RequestParam String token) {
        String email = emailVerificationService.verifyToken(token, TokenPurpose.ACTIVATION);
        userService.markEmailActivated(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody PasswordResetRequestDto dto) {
        userService.requestPasswordReset(dto.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody PasswordResetConfirmDto dto) {
        userService.confirmPasswordReset(dto.getToken(), dto.getPassword());
        return ResponseEntity.noContent().build();
    }
}