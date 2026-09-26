package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ALogin 账号与外部身份的绑定记录。
 *
 * @author Apleax
 */
@Data
@TableName("account_identity")
@AllArgsConstructor
@NoArgsConstructor
public class AccountIdentityPO {
    private Long id;
    /** account.id，而不是可变的登录名。 */
    private Long accountId;
    /** ExternalIdentityProvider 的稳定名称。 */
    private String provider;
    /** 提供方内的稳定主体，例如 Java Profile UUID 或 Bedrock XUID。 */
    private String subject;
    /** 绑定时的展示名称快照。 */
    private String displayName;
    /** 实际签发并验证断言的入口组件。 */
    private String issuer;
    /** ACTIVE 或 REVOKED。 */
    private String status;
    private Long verifiedAt;
    private Long lastSeenAt;
    private Long createdAt;
    private Long updatedAt;
}
