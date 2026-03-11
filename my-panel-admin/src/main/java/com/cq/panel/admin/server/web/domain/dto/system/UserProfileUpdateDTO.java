package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户个人信息修改对象")
public class UserProfileUpdateDTO {
    @Schema(description = "用户昵称", example = "若依")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过30个字符")
    private String nickName;

    @Schema(description = "邮箱", example = "ry@163.com")
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    @Schema(description = "手机号码", example = "15888888888")
    @Size(min = 0, max = 11, message = "手机号码长度不能超过11个字符")
    private String phonenumber;

    @Schema(description = "用户性别（0男 1女 2未知）", example = "0")
    private String sex;

    public String getPhonenumber() {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber) {
        this.phonenumber = phonenumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
