package xyz.apleax.ALogin.SQL.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import xyz.apleax.ALogin.Entity.POJO.PermissionNode;

import java.util.List;

/**
 *
 *
 * @author Apleax
 */
@Data
@TableName("permission_group")
public class PermissionGroupPO {
    /**
     * 组名
     */
    private String groupName;
    /**
     * 权限列表
     */
    private List<PermissionNode> permissionList;
}
