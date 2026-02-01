package dartoo.accountService.dto.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponseDtoPublic {
    private String accessToken;
    private Long accessTokenTtl;
    private Boolean isPasswordSet;
}
/*
실제로 프런트에서 이미 쿠키로 리프레시 토큰을 받는데
Dto로 다시 주는 건 보안상의 문제가 있을 것 같아서 수정하기 위한 DTO
-> 추후 수정 예정
 */