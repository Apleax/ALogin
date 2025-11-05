package xyz.apleax.ALogin.Entity.BO;

import lombok.Data;
import xyz.apleax.ALogin.Entity.POJO.PermissionNode;

import java.util.List;

/**
 * @author Apleax
 */
@Data
public class PermissionGroupBO {
    private String groupName;
    private List<PermissionNode> permissionList;
}
