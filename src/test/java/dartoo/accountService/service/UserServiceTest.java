package dartoo.accountService.service;

import dartoo.accountService.domain.Gender;
import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.ChangePasswordDto;
import dartoo.accountService.dto.UserRequestDto;
import dartoo.accountService.dto.UserResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.RefreshTokenRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserOAuthRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    //가짜 객체
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    UserEntityRepository userEntityRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    UserOAuthRepository userOAuthRepository;

    //가짜 객체 주입 후 테스트
    @InjectMocks
    UserService userService;

    private UserEntity testUser;

    @BeforeEach
    public void setUp() {
        //테스트 유저 설정
        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000,11,16))
                .build();
    }

    @AfterEach
    public void cleanup(){
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("이미 존재하는 회원인지 확인하기 - 존재하는 경우")
    void existUser_true(){
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail(testUser.getUserEmail());
        given(userEntityRepository.existsByUserEmail("test@test.com")).willReturn(true);
        //when
        boolean exists = userService.existUser(dto);
        //then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 회원인지 확인하기 - 존재하지 않는 경우")
    void existUser_false(){
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail(testUser.getUserEmail());
        given(userEntityRepository.existsByUserEmail("test@test.com")).willReturn(false);
        //when
        boolean exists = userService.existUser(dto);
        //then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("이미 가입한 이메일로 가입 시도시 USER_ALREADY_EXISTS 예외를 던진다")
    void addUser_UserAlreadyExists(){
        //given - 가입한줄 모르고 재가입시도
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail(testUser.getUserEmail());
        dto.setPassword("newPassword");
        dto.setNickname("newNickname");
        dto.setGender(testUser.getGender());
        dto.setBirthday(testUser.getBirthday());

        given(userEntityRepository.existsByUserEmail("test@test.com")).willReturn(true);
        //when, then
        assertThatThrownBy(()->userService.addUser(dto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode",USER_ALREADY_EXISTS);
        then(userEntityRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("신규 회원 가입 성공")
    void addUserSuccess(){
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("newUser@test.com");
        dto.setPassword("newUserPassword");
        dto.setNickname("newUserNickname");
        dto.setGender(Gender.FEMALE);
        dto.setBirthday(LocalDate.of(2002,11,1));

        given(userEntityRepository.existsByUserEmail("newUser@test.com")).willReturn(false);
        given(passwordEncoder.encode("newUserPassword")).willReturn("newUserPassword");
        //한번만 save를 호출하기 때문에 단순하게 any() 사용함
        /*
        여러번 호출하는 경우

        when(userEntityRepository.save(argThat(u -> "a@test.com".equals(u.getUserEmail()))))
            .thenAnswer(invocation -> {
                UserEntity u = invocation.getArgument(0);
                u.setId(101L);
                return u;
            });

        when(userEntityRepository.save(argThat(u -> "b@test.com".equals(u.getUserEmail()))))
            .thenAnswer(invocation -> {
                UserEntity u = invocation.getArgument(0);
                u.setId(102L);
                return u;
            });
         */
        given(userEntityRepository.save(any(UserEntity.class))).willAnswer(invocation -> {
            UserEntity input = invocation.getArgument(0);
            return UserEntity.builder()
                    .id(12L) //실제 DB가 동작하지 않기 때문에 임의로 지정
                    .userEmail(input.getUserEmail())
                    .password(passwordEncoder.encode(input.getPassword()))
                    .role(Role.USER)
                    .gender(input.getGender())
                    .nickname(input.getNickname())
                    .birthday(input.getBirthday())
                    .build();
        });
        //when
        Long userId = userService.addUser(dto);
        //then
        assertThat(userId).isEqualTo(12L);
        then(userEntityRepository).should().save(argThat(userEntity->
                userEntity.getUserEmail().equals("newUser@test.com") &&
                userEntity.getPassword().equals("newUserPassword") &&
                userEntity.getNickname().equals("newUserNickname") &&
                userEntity.getRole().equals(Role.USER) &&
                userEntity.getGender().equals(Gender.FEMALE) &&
                userEntity.getBirthday().equals(LocalDate.of(2002,11,1))));
    }

    @Test
    @DisplayName("ID로 사용자 조회 성공")
    void getUserById_success() {
        // given
        given(userEntityRepository.findById(1L)).willReturn(Optional.of(testUser));
        // when
        UserEntity found = userService.getUserById(1L);
        // then
        assertThat(found).isEqualTo(testUser);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 사용자 조회 시 예외")
    void getUserById_notFound() {
        // given
        given(userEntityRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 정보 조회 성공")
    void readUser_success() {
        // given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        // when
        UserResponseDto response = userService.readUser();

        // then
        assertThat(response.getUserEmail()).isEqualTo("test@test.com");
        assertThat(response.getNickname()).isEqualTo("테스터");
        assertThat(response.getGender()).isEqualTo(Gender.MALE);
        assertThat(response.getBirthday()).isEqualTo(LocalDate.of(2000, 11, 16));
    }

    @Test
    @DisplayName("회원 정보 조회 실패 시 USER_NOT_FOUND 에러 던지기")
    void readUser_fail() {
        //given
        mockSecurityContext("doesnotexist@test.com");
        given(userEntityRepository.findByUserEmail("doesnotexist@test.com")).willReturn(Optional.empty());
        //when, then
        assertThatThrownBy(() -> userService.readUser())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 정보 수정 성공")
    void updateUserSuccess() {
        //given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserRequestDto dto = new UserRequestDto();
        dto.setNickname("newNickname");
        dto.setGender(Gender.FEMALE);
        dto.setBirthday(LocalDate.of(2002,11,1));
        //when
        UserResponseDto response = userService.updateUser(dto);
        //then
        assertThat(response.getNickname()).isEqualTo("newNickname");
        assertThat(response.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getBirthday()).isEqualTo(LocalDate.of(2002,11,1));
    }

    @Test
    @DisplayName("회원 정보 수정 성공 - DTO에 이메일이 없어도 되는데 있는 경우")
    void updateUserSuccessWithEmail() {
        //given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("test@test.com");
        dto.setNickname("newNickname");
        dto.setGender(Gender.FEMALE);
        dto.setBirthday(LocalDate.of(2002,11,1));
        //when
        UserResponseDto response = userService.updateUser(dto);
        //then
        assertThat(response.getUserEmail()).isEqualTo("test@test.com");
        assertThat(response.getNickname()).isEqualTo("newNickname");
        assertThat(response.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getBirthday()).isEqualTo(LocalDate.of(2002,11,1));
    }

    @Test
    @DisplayName("회원정보 수정 실패 - DTO의 이메일과 JWT 토큰의 이메일이 불일치 하는 경우 AccessDeniedException을 던진다")
    void updateUserFailWithEmail() {
        //given
        mockSecurityContext("test@test.com");
//        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        lenient().when(userEntityRepository.findByUserEmail("test@test.com")).thenReturn(Optional.of(testUser));

        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("wrongUser@test.com");
        dto.setNickname("newNickname");
        dto.setGender(Gender.FEMALE);
        dto.setBirthday(LocalDate.of(2002,11,1));
        //when, then
        assertThatThrownBy(() -> userService.updateUser(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인의 정보만 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("비밀번호 변경 성공하기")
    void changePasswordSuccess(){
        //given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("oldPassword", "encodedPassword")).willReturn(true);
        given(passwordEncoder.matches("newPassword", "encodedPassword")).willReturn(false);
        given(passwordEncoder.encode("newPassword")).willReturn("newEncodedPassword");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("oldPassword");
        dto.setNewPassword("newPassword");
        //when
        userService.changePassword(dto);
        //then
        assertThat(testUser.getPassword()).isEqualTo("newEncodedPassword");
        then(userEntityRepository).should().save(testUser);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 유효하지 않은 이메일 정보가 토큰에 있을 경우 USER_NOT_FOUND 예외 던지기")
    void changePasswordFailUserNotFound(){
        //given
        mockSecurityContext("wrongEmail@test.com");
        given(userEntityRepository.findByUserEmail("wrongEmail@test.com")).willReturn(Optional.empty());

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("oldPassword");
        dto.setNewPassword("newPassword");
        //when, then
        assertThatThrownBy(() -> userService.changePassword(dto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
        then(userEntityRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호를 틀린 경우 PASSWORD_MISMATCH 예외 던지기")
    void changePasswordFailPasswordMismatch(){
        //given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("oldPassword2", "encodedPassword")).willReturn(false);
//        given(passwordEncoder.matches("newPassword", "encodedPassword")).willReturn(false);
//        given(passwordEncoder.encode("newPassword")).willReturn("newEncodedPassword");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("oldPassword2");
        dto.setNewPassword("newPassword");
        //when, then
        assertThatThrownBy(() -> userService.changePassword(dto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PASSWORD_MISMATCH);
        then(userEntityRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호와 새 비밀번호가 동일한 경우 SAME_PASSWORD 예외 던지기")
    void changePasswordFailSamePassword(){
        //given
        mockSecurityContext("test@test.com");
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("oldPassword", "encodedPassword")).willReturn(true);

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("oldPassword");
        dto.setNewPassword("oldPassword");
        //when,then
        assertThatThrownBy(() -> userService.changePassword(dto))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", SAME_PASSWORD);
        then(userEntityRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 본인")
    void deleteUserSuccessUser(){
        //given
        mockSecurityContextWithRole("test@test.com", "ROLE_USER");

        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("test@test.com");

        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        //when
        userService.deleteUser(dto);
        //then
        then(refreshTokenRepository).should().deleteAllByUserEntity(testUser);
        then(userOAuthRepository).should().deleteAllByUserEntity(testUser);
        then(userEntityRepository).should().deleteByUserEmail(testUser.getUserEmail());
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 관리자")
    void deleteUserSuccessAdmin(){
        //given
        mockSecurityContextWithRole("admin@test.com", "ROLE_ADMIN");

        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("test@test.com");

        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        //when
        userService.deleteUser(dto);
        //then
        then(refreshTokenRepository).should().deleteAllByUserEntity(testUser);
        then(userOAuthRepository).should().deleteAllByUserEntity(testUser);
        then(userEntityRepository).should().deleteByUserEmail(testUser.getUserEmail());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 권한 없음")
    void deleteUserFail(){
        //given
        mockSecurityContextWithRole("user2@test.com", "ROLE_USER");

        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("test@test.com");

        //given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        //when, then
        assertThatThrownBy(()->userService.deleteUser(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("회원을 탈퇴할 권한이 없습니다.");
        then(refreshTokenRepository).should(never()).deleteAllByUserEntity(testUser);
        then(userOAuthRepository).should(never()).deleteAllByUserEntity(testUser);
        then(userEntityRepository).should(never()).deleteByUserEmail(testUser.getUserEmail());
    }

    // getSessionEmail() 관련 SecurityContext Mock 헬퍼 메서드
    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        given(securityContext.getAuthentication()).willReturn(auth);
        given(auth.getName()).willReturn(email);

        SecurityContextHolder.setContext(securityContext);
    }

    //이 메서드에선 mock으로 authentication을 진행하니 오류가 발생해 실제 auth를 사용
    private void mockSecurityContextWithRole(String email, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, AuthorityUtils.createAuthorityList(role)
        );
        SecurityContext securityContext = mock(SecurityContext.class);
        given(securityContext.getAuthentication()).willReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }
}
