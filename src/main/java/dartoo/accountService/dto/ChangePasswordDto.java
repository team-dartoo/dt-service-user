package dartoo.accountService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {

    public interface passwordGroup{}

    @NotBlank(groups = {passwordGroup.class})
    private String currentPassword;

    @NotBlank(groups = {passwordGroup.class})
    @Size(min = 4)
    private String newPassword;
}

