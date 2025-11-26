package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.SocialProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkResponseDto {
    SocialProvider provider;
    Boolean isLinked;
}
