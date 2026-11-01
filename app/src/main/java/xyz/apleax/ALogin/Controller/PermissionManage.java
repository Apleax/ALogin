package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.Service.Permission.PermissionService;
import xyz.apleax.ALogin.VO.PermissionNodeBatchVO;

/**
 * 权限管理：对 LuckPerms 的受控批量写入
 * <p>
 * 高敏操作，需同时满足：{@code Permission.write.enabled=true}、调用者具备
 * {@code Permission.write.require-permission} 指定的权限节点；变更内容会批量通知到预配置邮箱。
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@Mapping(path = "/api/web/permission", produces = "application/json", consumes = "application/json")
public class PermissionManage {

    private final PermissionService permissionService;

    public PermissionManage(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Mapping(path = "/Nodes", method = MethodType.POST,
            name = "批量修改权限节点", description = "受控批量增删 LuckPerms 权限节点，需具备编辑权限，并会发送变更通知")
    public Result<Boolean> Nodes(@Validated PermissionNodeBatchVO permissionNodeBatchVO) {
        // 权限校验放在最前：未授权请求直接拒绝，避免通过响应差异探测"功能是否开启/载荷是否为空"
        // 说明：permission.yml 里也配了同一节点的接口规则（在拦截器层最早拦截），这里是有意保留的双保险：
        //      拦截器规则受 Permission.api.enabled 总开关控制，本校验跟随 Permission.write.* 配置，
        //      即使接口规则被整体关闭，受控写入依然要求编辑权限。
        String requirePermission = permissionService.writePermission();
        if (requirePermission != null && !requirePermission.isBlank()) StpUtil.checkPermission(requirePermission);

        if (!permissionService.writeEnabled()) return Result.failure("权限写入未开启", false);
        boolean empty = (permissionNodeBatchVO.getAdd() == null || permissionNodeBatchVO.getAdd().isEmpty())
                && (permissionNodeBatchVO.getRemove() == null || permissionNodeBatchVO.getRemove().isEmpty());
        if (empty) return Result.failure("没有需要变更的权限节点", false);

        String operator = StpUtil.getLoginIdAsString();
        try {
            permissionService.applyNodes(operator, permissionNodeBatchVO.getTarget(),
                    permissionNodeBatchVO.getAdd(), permissionNodeBatchVO.getRemove());
        } catch (IllegalStateException e) {
            log.warn("权限变更失败，操作者={}，目标={}: {}", operator, permissionNodeBatchVO.getTarget(), e.getMessage());
            return Result.failure(e.getMessage(), false);
        }
        return Result.succeed(true);
    }
}
