package dartoo.accountService.service;

import dartoo.accountService.domain.RefreshToken;
import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.ChangePasswordDto;
import dartoo.accountService.dto.UserRequestDto;
import dartoo.accountService.dto.UserResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.RefreshTokenRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserOAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserEntityRepository userEntityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserOAuthRepository userOAuthRepository;
    //자체 회원 가입, 탈퇴, 회원 정보 수정 관련 로직 등 -> 최대한 Long id는 사용하지 않는 방식으로 구현
    //회원 정보 업데이트 시 이메일을 DTO에 담아서 하는 것보다, SecurityContextHolder에
    //담긴 이메일 정보를 기반으로 업데이트하는 것이 보안상으로 좋은 것 같다.
    //DTO에 아무 이메일이나 담아서 회원정보 변경을 요청할 수도 있기 때문이다.

    private String getSessionEmail(){
        //SecurityContextHolder.getContext().getAuthentication()에
        //JWT 토큰 정보가 저장되도록 AuthenticationManger을설정해야 한다.
        //->SecurityConfig.java 참조
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    //이미 가입한 회원일 경우
    @Transactional(readOnly = true)
    public boolean existUser(UserRequestDto dto){
        return userEntityRepository.existsByUserEmail((dto.getUserEmail()));
    }

    //신규 회원 가입
    public Long addUser(UserRequestDto dto){
        if(userEntityRepository.existsByUserEmail(dto.getUserEmail())){
            throw new ApiException(USER_ALREADY_EXISTS);
        }
        UserEntity newUser = UserEntity.builder()
                .userEmail(dto.getUserEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.USER)
                .gender(dto.getGender())
                .nickname(dto.getNickname())
                .birthday(dto.getBirthday())
                .build();
        return userEntityRepository.save(newUser).getId();
    }

    public UserEntity getUserById(Long id) {
        return userEntityRepository.findById(id)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND));
    }

    //회원 정보 조회
    @Transactional(readOnly = true)
    public UserResponseDto readUser(){
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userEntityRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND));
        return new UserResponseDto(user.getUserEmail(),user.getNickname(),user.getBirthday(),user.getGender());
    }

    //회원 정보 수정
    public UserResponseDto updateUser(UserRequestDto dto){
        String email = getSessionEmail();

        // 보안 검증: DTO의 이메일이 있다면 세션 이메일과 일치하는지 확인
        if (dto.getUserEmail() != null && !dto.getUserEmail().isBlank()) {
            if (!email.equals(dto.getUserEmail())) {
                throw new AccessDeniedException("본인의 정보만 수정할 수 있습니다.");
            }
        }

        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
        user.changeProfile(dto.getNickname(), dto.getBirthday(), dto.getGender());
        //userEntityRepository.save(user); -> save가 없어도 수정하면 더티체킹으로 알아서 업데이트 된다.
        //UI에 바로 업데이트할 수 있게.
        return new UserResponseDto(user.getUserEmail(),user.getNickname(),user.getBirthday(),user.getGender());
    }

    //비밀번호 변경
    public void changePassword(ChangePasswordDto dto){
        String email = getSessionEmail();
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
        if(!passwordEncoder.matches(dto.getCurrentPassword(),user.getPassword())){
            throw new ApiException(PASSWORD_MISMATCH);
        }
        if(passwordEncoder.matches(dto.getNewPassword(),user.getPassword())){
            throw new ApiException(SAME_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(dto.getNewPassword()));
        userEntityRepository.save(user);

        //비밀번호 변경 시 해당 사용자의 모든 RefreshToken을 revoke
        Instant now = Instant.now();
        List<RefreshToken> actives =
                refreshTokenRepository.findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter(user, now);
        for (RefreshToken rt : actives) {
            rt.revoke(now);
        }
    }

    //회원 탈퇴 - 회원 정보를 받아, 소셜 로그인 정보, 리프레시 토큰까지 함께 삭제
    public void deleteUser(UserRequestDto dto){
        //스프링 EL의 @PreAuthorize를 통해 쉽게 구현할 수 있음.
        SecurityContext context = SecurityContextHolder.getContext();
        String sessionUsername = context.getAuthentication().getName();
        String sessionRole = context.getAuthentication().getAuthorities().iterator().next().toString();

        //요청자가 본인인지, 요청자의 역할이 관리자인지 확인
        boolean isOwner = sessionUsername.equals(dto.getUserEmail());
        boolean isAdmin = sessionRole.equals("ROLE_"+Role.ADMIN.name());

        if(!isOwner && !isAdmin){
            throw new AccessDeniedException("회원을 탈퇴할 권한이 없습니다.");
        }

        UserEntity user = userEntityRepository.findByUserEmail(dto.getUserEmail())
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));

        //1. 리프레시 토큰 제거
        refreshTokenRepository.deleteAllByUserEntity(user);
        //2. 소셜 로그인 정보 제거
        userOAuthRepository.deleteAllByUserEntity(user);
        //3. 회원 정보 완전히 제거
        userEntityRepository.deleteByUserEmail(dto.getUserEmail());
    }
}
