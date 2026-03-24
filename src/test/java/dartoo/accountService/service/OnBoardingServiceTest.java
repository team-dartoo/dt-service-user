package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.oauth.OnBoardingRequestDto;
import dartoo.accountService.dto.oauth.OnBoardingResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.USER_ALREADY_ONBOARDED;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OnBoardingServiceTest {

    @Mock
    UserEntityRepository userEntityRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    OnBoardingService onBoardingService;

    @Test
    @DisplayName("온보딩 초기화 성공")
    void initOnBoarding_success() {
        //given
        UserEntity testUser = UserEntity.builder()
                .id(1L)
                .userEmail("newuser@test.com")
                .password(null)
                .nickname(null)
                .role(Role.USER)
                .gender(null)
                .birthday(null)
                .passwordSet(false)
                .build();

        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("newuser@test.com")
                .password("newPassword123")
                .nickname("새유저")
                .birthday(LocalDate.of(2000, 5, 15))
                .gender(Gender.FEMALE)
                .build();

        given(userEntityRepository.findByUserEmail("newuser@test.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.encode("newPassword123")).willReturn("encodedPassword123");

        //when
        OnBoardingResponseDto response = onBoardingService.initOnBoarding("newuser@test.com", requestDto);

        //then
        assertThat(response.getUserEmail()).isEqualTo("newuser@test.com");
        assertThat(response.getIsPasswordSet()).isTrue();
        assertThat(response.getNickname()).isEqualTo("새유저");
        assertThat(response.getBirthday()).isEqualTo(LocalDate.of(2000, 5, 15));
        assertThat(response.getGender()).isEqualTo(Gender.FEMALE);

        then(userEntityRepository).should().findByUserEmail("newuser@test.com");
        then(passwordEncoder).should().encode("newPassword123");
        then(passwordEncoder).should(times(1)).encode(any());
    }

    @Test
    @DisplayName("온보딩 초기화 실패 - 사용자를 찾을 수 없음 (USER_NOT_FOUND)")
    void initOnBoarding_userNotFound() {
        //given
        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("nonexistent@test.com")
                .password("newPassword123")
                .nickname("새유저")
                .birthday(LocalDate.of(2000, 5, 15))
                .gender(Gender.FEMALE)
                .build();

        given(userEntityRepository.findByUserEmail("nonexistent@test.com")).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> onBoardingService.initOnBoarding("nonexistent@test.com", requestDto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("온보딩 초기화 실패 - 이미 온보딩이 완료된 사용자 (USER_ALREADY_ONBOARDED)")
    void initOnBoarding_alreadyOnboarded() {
        //given
        UserEntity onboardedUser = UserEntity.builder()
                .id(1L)
                .userEmail("onboarded@test.com")
                .password("existingEncodedPassword")
                .nickname("기존유저")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(1995, 3, 10))
                .passwordSet(true)
                .build();

        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("onboarded@test.com")
                .password("newPassword123")
                .nickname("새닉네임")
                .birthday(LocalDate.of(1995, 3, 10))
                .gender(Gender.MALE)
                .build();

        given(userEntityRepository.findByUserEmail("onboarded@test.com")).willReturn(Optional.of(onboardedUser));

        //when, then
        assertThatThrownBy(() -> onBoardingService.initOnBoarding("onboarded@test.com", requestDto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_ALREADY_ONBOARDED);

        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("온보딩 초기화 실패 - 이메일 불일치로 인한 권한 오류 (AccessDeniedException)")
    void initOnBoarding_emailMismatch() {
        //given
        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("different@test.com")
                .password("newPassword123")
                .nickname("새유저")
                .birthday(LocalDate.of(2000, 5, 15))
                .gender(Gender.FEMALE)
                .build();

        //when, then
        assertThatThrownBy(() -> onBoardingService.initOnBoarding("original@test.com", requestDto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("사용자 정보가 일치하지 않습니다.");

        then(userEntityRepository).should(never()).findByUserEmail(any());
        then(passwordEncoder).should(never()).encode(any());
    }

}
