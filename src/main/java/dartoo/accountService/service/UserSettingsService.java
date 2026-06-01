package dartoo.accountService.service;

import dartoo.accountService.config.TermsConfig;
import dartoo.accountService.domain.UserAgreed;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPreference;
import dartoo.accountService.dto.account.AgreedSettingsDto;
import dartoo.accountService.dto.account.PreferenceSettingsDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserAgreedRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dartoo.accountService.error.ErrorCode.*;

//사용자 설정 관련 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class UserSettingsService {
    private final TermsConfig termsConfig;
    private final UserEntityRepository userEntityRepository;
    private final UserAgreedRepository userAgreedRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional(readOnly = true)
    public AgreedSettingsDto getUserAgreedSettings(String email){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ApiException(USER_NOT_FOUND));
        UserAgreed useragreed = userAgreedRepository.findById(user.getId())
                .orElseThrow(()-> new ApiException(USER_AGREED_NOT_FOUND));
        return new AgreedSettingsDto(useragreed.getTosAgreed(), useragreed.getTosVersion(),
                useragreed.getPrivacyAgreed(), useragreed.getPrivacyVersion(), useragreed.getMarketingAgreed());
    }

    @Transactional(readOnly = true)
    public PreferenceSettingsDto getUserPreferenceSettings(String email){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ApiException(USER_NOT_FOUND));
        UserPreference userPreference = userPreferenceRepository.findById(user.getId())
                .orElseThrow(()-> new ApiException(USER_PREFERENCE_NOT_FOUND));
        return new PreferenceSettingsDto(userPreference.getPushEnabled(),userPreference.getEmailEnabled(), userPreference.getAlertDelay());
    }

    public AgreedSettingsDto updateUserAgreedSettings(String email, AgreedSettingsDto settings){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ApiException(USER_NOT_FOUND));
        UserAgreed useragreed = userAgreedRepository.findById(user.getId())
                .orElseGet(()->{
                    UserAgreed newAgreed = new UserAgreed();
                    user.attachAgreed(newAgreed);
                    return newAgreed;
                });
        if (Boolean.FALSE.equals(settings.getTosAgreed()) || Boolean.FALSE.equals(settings.getPrivacyAgreed())) {
            throw new ApiException(TERMS_REQUIRED_FIELDS_MUST_BE_AGREED);
        }
        useragreed.setTosAgreed(settings.getTosAgreed());
        useragreed.setTosVersion(termsConfig.getTosVersion());
        useragreed.setPrivacyAgreed(settings.getPrivacyAgreed());
        useragreed.setPrivacyVersion(termsConfig.getPrivacyVersion());
        useragreed.setMarketingAgreed(settings.getMarketingAgreed());
        return new AgreedSettingsDto(
                useragreed.getTosAgreed(), useragreed.getTosVersion(),
                useragreed.getPrivacyAgreed(), useragreed.getPrivacyVersion(),
                useragreed.getMarketingAgreed()
        );
    }

    public PreferenceSettingsDto updateUserPreferenceSettings(String email, PreferenceSettingsDto settings){
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ApiException(USER_NOT_FOUND));
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
