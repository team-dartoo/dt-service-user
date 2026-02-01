package dartoo.accountService.dto.account;

import dartoo.accountService.domain.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserRequestDto {

    public interface existGroup {} // 회원 가입시 username 존재 확인
    public interface addGroup {} // 회원 가입시
    public interface updateGroup {} // 회원 수정시
    public interface deleteGroup {} // 회원 삭제시

    @Email(groups = {existGroup.class, addGroup.class, deleteGroup.class})
    @NotBlank(groups = {existGroup.class, addGroup.class, deleteGroup.class})
    private String userEmail;

    @Past(message = "생일은 과거 날짜여야 합니다", groups = {addGroup.class, updateGroup.class})
    @NotNull(message = "생일은 필수 항목입니다", groups = {addGroup.class, updateGroup.class})
    private LocalDate birthday;

    @NotBlank(groups = {addGroup.class}) @Size(min = 8, groups = {addGroup.class})
    private String password;

    @NotBlank(groups = {addGroup.class, updateGroup.class})
    private String nickname;

    @NotNull(groups = {addGroup.class, updateGroup.class})
    private Gender gender;
}
