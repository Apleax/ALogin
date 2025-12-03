package xyz.apleax.ALogin.Config;

import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.SQL.Service.Impl.IAccountServiceImpl;

import java.util.List;

/**
 * //TODO 权限控制
 *
 * @author Apleax
 * @see xyz.apleax.ALogin.Entity.POJO.PermissionNode PermissionNode
 */
@Managed
@Condition(onBean = IAccountServiceImpl.class)
public class Permission implements StpInterface {
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public SaDisableWrapperInfo isDisabled(Object loginId, String service) {
        return StpInterface.super.isDisabled(loginId, service);
    }
}
