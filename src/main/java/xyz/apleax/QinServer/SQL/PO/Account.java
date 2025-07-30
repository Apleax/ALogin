package xyz.apleax.QinServer.SQL.PO;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 与数据库交互的账号PO
 *
 * @author Apleax
 */
@Data
@TableName("account")
public class Account {
    /**
     * 账号
     */
    @TableId
    private Integer account;
    /**
     * 使用的加密算法
     */
    private String algorithm;
    /**
     * 用户头像存储路径
     */
    private String avatar;
    /**
     * 绑定mc时间
     */
    private Long bindMcAccountTime;
    /**
     * 绑定qq时间
     */
    private Long bindQqAccountTime;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 最后登录官网时间
     */
    private Long lastLoginWebTime;
    /**
     * 服务器内uuid
     */
    private String mcUuid;
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
}