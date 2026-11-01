package xyz.apleax.ALogin.Config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Service.Permission.PermissionService;

import java.util.List;

/**
 * Sa-Token 权限数据源：权限节点与权限组均来自 LuckPerms（权威源）
 * <p>
 * loginId 为 8 位账号，服务端据此换取 mcUuid 后查询 LuckPerms；鉴定失败按 fail-closed 处理。
 * <p>
 * 命名说明：Sa-Token 把"权限组"固定称为 role（{@code getRoleList}、{@code StpUtil.checkRole}），这是框架接口名，
 * 无法改名；本项目里它承载的语义就是 LuckPerms 的权限组（用户所属的组，含继承得到的组）。
 *
 * @author Apleax
 * @see xyz.apleax.ALogin.POJO.PermissionNode PermissionNode
 */
@Slf4j
@Managed
@Condition(onBean = PermissionService.class)
@AllArgsConstructor
public class Permission implements StpInterface {
    private final PermissionService permissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!permissionService.enabled()) return List.of();
        return permissionService.permissions(loginId == null ? null : String.valueOf(loginId));
    }

    /**
     * Sa-Token 固定接口名（role），返回的是 LuckPerms 权限组集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!permissionService.enabled()) return List.of();
        return permissionService.groups(loginId == null ? null : String.valueOf(loginId));
    }
}
