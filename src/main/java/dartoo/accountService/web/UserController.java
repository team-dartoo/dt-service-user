package dartoo.accountService.web;

import dartoo.accountService.dto.ChangePasswordDto;
import dartoo.accountService.dto.UserRequestDto;
import dartoo.accountService.dto.UserResponseDto;
import dartoo.accountService.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
//로그인 이외 인증된 회원 관련 컨트롤러 (회원 정보 조회, 변경 + 회원 탈퇴)
public class UserController {
    private final UserService userService;

    //회원 정보 조회
    @GetMapping("/info")
    public ResponseEntity<UserResponseDto> info() {
        //SecurityContextHolder에서 아이디를 가지고 있기 때문에, 별다른 id를 받을 필요 없음.
        return ResponseEntity.ok(userService.readUser());
    }

    //회원 정보 (프로필) 업데이트 (비밀번호 제외)
    @PatchMapping("/update")
    public ResponseEntity<UserResponseDto> update(
            @Validated(UserRequestDto.updateGroup.class) @RequestBody UserRequestDto userRequestDto) {
        return ResponseEntity.ok(userService.updateUser(userRequestDto));
    }

    //비밀번호 변경
    //변경 후 로그아웃 및 리다이렉트 로직은 프런트에서 구현하는 것이 좋을 것 같음.
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Validated(ChangePasswordDto.passwordGroup.class) @RequestBody ChangePasswordDto changePasswordDto
    ){
        userService.changePassword(changePasswordDto);
        return ResponseEntity.noContent().build();
    }

    //회원 탈퇴
    //스프링 EL을 통해 deleteUser에 있는 검사 로직을 어노테이션 하나로 대체할 수 있음. (GPT가 알려준거라 일단 주석처리)
    //@PreAuthorize("hasRole('ADMIN') or #userRequestDto.userEmail == authentication.name")
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @Validated(UserRequestDto.deleteGroup.class) @RequestBody UserRequestDto userRequestDto
    ){
        userService.deleteUser(userRequestDto);
        return ResponseEntity.noContent().build();
    }

}
