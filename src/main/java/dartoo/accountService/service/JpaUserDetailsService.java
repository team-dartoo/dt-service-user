package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.repository.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
//스프링 시큐리티 로그인 시 사용자 정보를 불러와 관련 정보를 저장
public class JpaUserDetailsService implements UserDetailsService {
    private final UserEntityRepository userEntityRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()->new UsernameNotFoundException(email));
        GrantedAuthority grantedAuthority = new SimpleGrantedAuthority("ROLE_"+user.getRole());
        return User.withUsername(email)
                .password(user.getPassword())
                .authorities(grantedAuthority)
                .build();
    }
}
/**
 * 클라이언트 → /login 요청 (username, password)
 * → AuthController.login()
 * → AuthenticationManager.authenticate()
 * → DaoAuthenticationProvider
 * → UserDetailsService.loadUserByUsername() 호출
 * → DB에서 사용자 조회 + UserDetails 반환 + 비밀번호 검증
 * → 인증 성공 시 Authentication 객체 반환
 * → TokenService.issueTokens()
 * → AccessToken + RefreshToken 반환
 */