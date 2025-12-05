package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.UUID;

/**
 * 与数据库交互的账号PO
 *
 * @author Apleax
 */
@Data
@TableName(value = "account", autoResultMap = true)
public class AccountPO {
    private Long id;
    /**
     * 账号
     */
    private String account;
    /**
     * 使用的加密算法
     */
    private String algorithm;
    /**
     * 用户头像存储路径
     */
    private String avatar;
    /**
     * 绑定qq时间
     */
    private Long bindQqAccountTime;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 服务器内uuid
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private UUID mcUuid;
    /**
     * 昵称
     */
    private String nickName;
    /**
     * 密码
     */
    private String password;
    /**
     * 绑定的QQ号
     */
    private Long qqAccount;
    /**
     * 注册时间
     */
    private Long registrationTime;
    /**
     * 盐值
     */
    private String salt;
    /**
     * 最后登录IP
     */
    private String lastLoginIp;
}