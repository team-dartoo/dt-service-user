package dartoo.accountService.service;

import dartoo.accountService.domain.*;
import dartoo.accountService.dto.AgreedSettingsDto;
import dartoo.accountService.dto.PreferenceSettingsDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserAgreedRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.*;

import java.time.LocalDate;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class UserSettingsServiceTest {
    @Mock
    UserEntityRepository userEntityRepository;
    @Mock
    UserAgreedRepository userAgreedRepository;
    @Mock
    UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    UserSettingsService userSettingsService;

    UserEntity testUser;
    UserPreference testPreference;
    UserAgreed testAgreed;

    @BeforeEach
    public void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000,11,16))
                .build();

        testAgreed = UserAgreed.builder()
                .user(testUser)
                .tosAgreed(true)
                .tosVersion("1.0.0")
                .privacyAgreed(true)
                .privacyVersion("1.0.0")
                .marketingAgreed(false)
                .build();

        testPreference = UserPreference.builder()
                .user(testUser)
                .pushEnabled(true)
                .emailEnabled(true)
                .alertDelay(15)
                .build();
    }

    @Test
    @DisplayName("이메일 정보로 UserAgreed 조회 성공하기")
    void getUserAgreedSettingsSuccess(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userAgreedRepository.findById(testUser.getId())).willReturn(Optional.of(testAgreed));
        //when
        AgreedSettingsDto dto = userSettingsService.getUserAgreedSettings("test@test.com");
        //then
        assertThat(dto.getTosAgreed()).isTrue();
        assertThat(dto.getTosVersion()).isEqualTo("1.0.0");
        assertThat(dto.getPrivacyAgreed()).isTrue();
        assertThat(dto.getPrivacyVersion()).isEqualTo("1.0.0");
        assertThat(dto.getMarketingAgreed()).isFalse();
    }

    @Test
    @DisplayName("이메일 정보로 UserAgreed 조회 시 이메일 정보가 없을 경우 USER_NOT_FOUND 예외를 던진다")
    void getUserAgreedSettingsUserNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());
        //when,then
        assertThatThrownBy(()->userSettingsService.getUserAgreedSettings("unknown@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_NOT_FOUND);
    }

    @Test
    @DisplayName("이메일 정보로 UserAgreed 조회 시 이용약관 동의 정보가 없을 경우 USER_AGREED_NOT_FOUND 예외를 던진다")
    void getUserAgreedSettingsUserAgreedNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userAgreedRepository.findById(testUser.getId())).willReturn(Optional.empty());
        //when,then
        assertThatThrownBy(()->userSettingsService.getUserAgreedSettings("test@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_AGREED_NOT_FOUND);
    }

    @Test
    @DisplayName("이메일 정보로 UserPreference 조회 성공하기")
    void getUserPreferenceSettingsSuccess(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPreferenceRepository.findById(testUser.getId())).willReturn(Optional.of(testPreference));
        //when
        PreferenceSettingsDto dto = userSettingsService.getUserPreferenceSettings("test@test.com");
        //then
        assertThat(dto.getPushEnabled()).isTrue();
        assertThat(dto.getEmailEnabled()).isTrue();
        assertThat(dto.getAlertDelay()).isEqualTo(15);
    }

    @Test
    @DisplayName("이메일 정보로 UserPreference 조회 시 이메일 정보가 없을 경우 USER_NOT_FOUND 예외를 던진다")
    void getUserPreferenceSettingsUserNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());
        //when,then
        assertThatThrownBy(()->userSettingsService.getUserPreferenceSettings("unknown@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_NOT_FOUND);
    }

    @Test
    @DisplayName("이메일 정보로 UserPreference 조회 시 사용자 설정 정보가 없을 경우 USER_PREFERENCE_NOT_FOUND 예외를 던진다")
    void getUserPreferenceSettingsUserAgreedNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPreferenceRepository.findById(testUser.getId())).willReturn(Optional.empty());
        //when,then
        assertThatThrownBy(()->userSettingsService.getUserPreferenceSettings("test@test.com"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_PREFERENCE_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 약관 동의 정보가 있을 경우 업데이트")
    void updateUserAgreedSettingsThatExists(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userAgreedRepository.findById(testUser.getId())).willReturn(Optional.of(testAgreed));

        AgreedSettingsDto newSettings = new AgreedSettingsDto();
        newSettings.setTosAgreed(true);
        newSettings.setTosVersion("1.0.1");
        newSettings.setPrivacyAgreed(true);
        newSettings.setPrivacyVersion("1.0.1");
        newSettings.setMarketingAgreed(true);

        //when
        AgreedSettingsDto result = userSettingsService.updateUserAgreedSettings("test@test.com", newSettings);
        //then
        assertThat(result.getTosAgreed()).isEqualTo(newSettings.getTosAgreed());
        assertThat(result.getTosVersion()).isEqualTo(newSettings.getTosVersion());
        assertThat(result.getPrivacyAgreed()).isEqualTo(newSettings.getPrivacyAgreed());
        assertThat(result.getPrivacyVersion()).isEqualTo(newSettings.getPrivacyVersion());
        assertThat(result.getMarketingAgreed()).isEqualTo(newSettings.getMarketingAgreed());
    }

    @Test
    @DisplayName("신규 약관 동의 정보 등록")
    void updateUserAgreedSettingsNew(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userAgreedRepository.findById(testUser.getId())).willReturn(Optional.empty());

        AgreedSettingsDto newSettings = new AgreedSettingsDto();
        newSettings.setTosAgreed(true);
        newSettings.setTosVersion("1.0.1");
        newSettings.setPrivacyAgreed(true);
        newSettings.setPrivacyVersion("1.0.1");
        newSettings.setMarketingAgreed(true);

        //when
        AgreedSettingsDto result = userSettingsService.updateUserAgreedSettings("test@test.com", newSettings);
        //then
        assertThat(result.getTosAgreed()).isEqualTo(newSettings.getTosAgreed());
        assertThat(result.getTosVersion()).isEqualTo(newSettings.getTosVersion());
        assertThat(result.getPrivacyAgreed()).isEqualTo(newSettings.getPrivacyAgreed());
        assertThat(result.getPrivacyVersion()).isEqualTo(newSettings.getPrivacyVersion());
        assertThat(result.getMarketingAgreed()).isEqualTo(newSettings.getMarketingAgreed());
    }

    @Test
    @DisplayName("사용자 약관 동의 정보 업데이트 호출 시 사용자 정보가 없을 경우 USER_NOT_FOUND 예외를 던진다")
    void updateUserAgreedSettingsUserNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());

        AgreedSettingsDto newSettings = new AgreedSettingsDto();
        newSettings.setTosAgreed(true);
        newSettings.setTosVersion("1.0.1");
        newSettings.setPrivacyAgreed(true);
        newSettings.setPrivacyVersion("1.0.1");
        newSettings.setMarketingAgreed(true);
        //when,then
        assertThatThrownBy(()->userSettingsService.updateUserAgreedSettings("unknown@test.com",newSettings))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 환경 설정 정보가 있을 경우 업데이트")
    void updateUserPreferenceSettingsThatExists(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPreferenceRepository.findById(testUser.getId())).willReturn(Optional.of(testPreference));

        PreferenceSettingsDto preferenceSettingsDto = new PreferenceSettingsDto();
        preferenceSettingsDto.setPushEnabled(false);
        preferenceSettingsDto.setEmailEnabled(false);
        preferenceSettingsDto.setAlertDelay(15);

        //when
        PreferenceSettingsDto result = userSettingsService.updateUserPreferenceSettings("test@test.com", preferenceSettingsDto);
        //then
        assertThat(result.getPushEnabled()).isEqualTo(preferenceSettingsDto.getPushEnabled());
        assertThat(result.getEmailEnabled()).isEqualTo(preferenceSettingsDto.getEmailEnabled());
        assertThat(result.getAlertDelay()).isEqualTo(preferenceSettingsDto.getAlertDelay());
    }

    @Test
    @DisplayName("신규 환경 설정 정보 등록")
    void updateUserPreferenceSettingsNew(){
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPreferenceRepository.findById(testUser.getId())).willReturn(Optional.empty());

        PreferenceSettingsDto preferenceSettingsDto = new PreferenceSettingsDto();
        preferenceSettingsDto.setPushEnabled(false);
        preferenceSettingsDto.setEmailEnabled(false);
        preferenceSettingsDto.setAlertDelay(15);

        //when
        PreferenceSettingsDto result = userSettingsService.updateUserPreferenceSettings("test@test.com", preferenceSettingsDto);
        //then
        assertThat(result.getPushEnabled()).isEqualTo(preferenceSettingsDto.getPushEnabled());
        assertThat(result.getEmailEnabled()).isEqualTo(preferenceSettingsDto.getEmailEnabled());
        assertThat(result.getAlertDelay()).isEqualTo(preferenceSettingsDto.getAlertDelay());
    }

    @Test
    @DisplayName("사용자 환경 설정 정보 업데이트 호출 시 사용자 정보가 없을 경우 USER_NOT_FOUND 예외를 던진다")
    void updateUserPreferenceSettingsUserNotFound(){
        //given
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());

        PreferenceSettingsDto preferenceSettingsDto = new PreferenceSettingsDto();
        preferenceSettingsDto.setPushEnabled(false);
        preferenceSettingsDto.setEmailEnabled(false);
        preferenceSettingsDto.setAlertDelay(15);
        //when,then
        assertThatThrownBy(()->userSettingsService.updateUserPreferenceSettings("unknown@test.com",preferenceSettingsDto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_NOT_FOUND);
    }
}
