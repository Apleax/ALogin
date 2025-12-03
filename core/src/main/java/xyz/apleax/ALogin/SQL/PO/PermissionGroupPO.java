package xyz.apleax.ALogin.SQL.PO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import xyz.apleax.ALogin.Entity.POJO.PermissionNode;

import java.util.List;

/**
 *
 *
 * @author Apleax
 */
@Data
@TableName(value = "permission_group", autoResultMap = true)
public class PermissionGroupPO {
    /**
     * 组名
     */
    private String groupName;
    /**
     * 权限列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<PermissionNode> permissionList;
}
