package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;
import xyz.apleax.ALogin.POJO.PermissionNode;

import java.util.List;

/**
 * 批量修改 LuckPerms 权限节点的请求体
 *
 * @author Apleax
 */
@Data
public class PermissionNodeBatchVO {
    /**
     * 目标账号（8 位账号，服务端据此换取 mcUuid）
     */
    @NotBlank(message = "目标账号不能为空")
    String target;
    /**
     * 需要新增的权限节点
     */
    List<PermissionNode> add;
    /**
     * 需要删除的权限节点
     */
    List<PermissionNode> remove;
}
