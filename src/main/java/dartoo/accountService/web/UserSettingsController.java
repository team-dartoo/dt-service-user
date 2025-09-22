package dartoo.accountService.web;

import dartoo.accountService.dto.AgreedSettingsDto;
import dartoo.accountService.dto.PreferenceSettingsDto;
import dartoo.accountService.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/settings")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    //프런트에서 이메일 안받음
    private String currentEmail(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    //단순 조회
    @GetMapping("/me/agreed")
    public ResponseEntity<AgreedSettingsDto> getMyAgreed() {
        return ResponseEntity.ok().body(userSettingsService.getUserAgreedSettings(currentEmail()));
    }

    @GetMapping("/me/preference")
    public ResponseEntity<PreferenceSettingsDto> getMyPreference() {
        return ResponseEntity.ok().body(userSettingsService.getUserPreferenceSettings(currentEmail()));
    }

    //설정 수정
    @PutMapping("/me/agreed")
    public ResponseEntity<AgreedSettingsDto> setMyAgreed(@RequestBody AgreedSettingsDto settings) {
        return ResponseEntity.ok().body(userSettingsService.updateUserAgreedSettings(currentEmail(), settings));
    }

    @PutMapping("/me/preference")
    public ResponseEntity<PreferenceSettingsDto> setMyPreference(@RequestBody PreferenceSettingsDto settings) {
        return ResponseEntity.ok().body(userSettingsService.updateUserPreferenceSettings(currentEmail(), settings));
    }
}
