package dartoo.accountService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    private Long accessTokenTtl;
    private String refreshToken;
    private Long refreshTokenTtl;
}
