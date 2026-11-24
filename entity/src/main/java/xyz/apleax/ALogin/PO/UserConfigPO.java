package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户配置（一列一设置）
 *
 * @author Apleax
 */
@Data
@TableName("user_config")
@AllArgsConstructor
@NoArgsConstructor
public class UserConfigPO {
    /**
     * 雪花主键（与表结构无 AUTO_INCREMENT 匹配，见 SQL 模板）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 账号
     */
    private String account;
    /**
     * Java 正版免密自动登录开关
     */
    private Boolean autoLoginJava;
    /**
     * 基岩版免密自动登录开关
     */
    private Boolean autoLoginBedrock;
    /**
     * 最后修改时间（毫秒）
     */
    private Long updateTime;

    /**
     * 默认配置：两个开关都开启。仅作为内存对象返回，不落库。
     */
    public static UserConfigPO defaults(String account) {
        UserConfigPO po = new UserConfigPO();
        po.setAccount(account);
        po.setAutoLoginJava(Boolean.TRUE);
        po.setAutoLoginBedrock(Boolean.TRUE);
        return po;
    }
}
