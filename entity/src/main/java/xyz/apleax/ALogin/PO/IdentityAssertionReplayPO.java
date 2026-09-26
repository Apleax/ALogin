package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已消费的外部身份断言，持久化后可在多台 ALogin 节点间防止重放。
 *
 * @author Apleax
 */
@Data
@TableName("identity_assertion_replay")
@AllArgsConstructor
@NoArgsConstructor
public class IdentityAssertionReplayPO {
    private Long id;
    private String jti;
    private Long expiresAt;
    private Long createdAt;
}
