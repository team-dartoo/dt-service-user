package dartoo.accountService.service;

import dartoo.accountService.domain.UserAgreed;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPreference;
import dartoo.accountService.dto.AgreedSettingsDto;
import dartoo.accountService.dto.PreferenceSettingsDto;
import dartoo.accountService.repository.UserAgreedRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

//사용자 설정 관련 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class UserSettingsService {
    private final UserEntityRepository userEntityRepository;
    private final UserAgreedRepository userAgreedRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional(readOnly = true)
    public AgreedSettingsDto getUserAgreedSettings(String email){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"User Not Found"));
        UserAgreed useragreed = userAgreedRepository.findById(user.getId())
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"UserAgreed Not Found"));
        return new AgreedSettingsDto(useragreed.getTosAgreed(), useragreed.getTosVersion(),
                useragreed.getPrivacyAgreed(), useragreed.getPrivacyVersion(), useragreed.getMarketingAgreed());
    }

    @Transactional(readOnly = true)
    public PreferenceSettingsDto getUserPreferenceSettings(String email){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"User Not Found"));
        UserPreference userPreference = userPreferenceRepository.findById(user.getId())
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"UserAgreed Not Found"));
        return new PreferenceSettingsDto(userPreference.getPushEnabled(),userPreference.getEmailEnabled(), userPreference.getAlertDelay());
    }

    public AgreedSettingsDto updateUserAgreedSettings(String email, AgreedSettingsDto settings){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"User Not Found"));
        UserAgreed useragreed = userAgreedRepository.findById(user.getId())
                .orElseGet(()->{
                    UserAgreed newAgreed = new UserAgreed();
                    user.attachAgreed(newAgreed);
                    return newAgreed;
                });
        useragreed.setTosAgreed(settings.getTosAgreed());
        useragreed.setTosVersion(settings.getTosVersion());
        useragreed.setPrivacyVersion(settings.getPrivacyVersion());
        useragreed.setPrivacyAgreed(settings.getPrivacyAgreed());
        useragreed.setMarketingAgreed(settings.getMarketingAgreed());
        return new AgreedSettingsDto(
                useragreed.getTosAgreed(), useragreed.getTosVersion(),
                useragreed.getPrivacyAgreed(), useragreed.getPrivacyVersion(),
                useragreed.getMarketingAgreed()
        );
    }

    public PreferenceSettingsDto updateUserPreferenceSettings(String email, PreferenceSettingsDto settings){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ResponseStatusException(NOT_FOUND,"User Not Found"));
        UserPreference userPreference = userPreferenceRepository.findById(user.getId())
                .orElseGet(()->{
                    UserPreference newPreference = new UserPreference();
                    user.attachPreference(newPreference);
                    return newPreference;
                });
        userPreference.setPushEnabled(settings.getPushEnabled());
        userPreference.setEmailEnabled(settings.getEmailEnabled());
        userPreference.setAlertDelay(settings.getAlertDelay());
        return new PreferenceSettingsDto(
                userPreference.getPushEnabled(), userPreference.getEmailEnabled(), userPreference.getAlertDelay()
        );
    }
}
