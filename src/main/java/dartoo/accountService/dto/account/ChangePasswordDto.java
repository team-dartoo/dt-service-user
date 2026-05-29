package dartoo.accountService.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {

    public interface passwordGroup{}

    @NotBlank(groups = {passwordGroup.class})
    private String currentPassword;

    @NotBlank(groups = {passwordGroup.class})
    @Size(min = 8, groups = {passwordGroup.class}, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String newPassword;
}

