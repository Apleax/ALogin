package xyz.apleax.ALogin.Config;

import cn.dev33.satoken.stp.StpInterface;
import xyz.apleax.ALogin.SQL.Service.Impl.IAccountServiceImpl;

import java.util.List;

/**
 * //TODO 权限控制
 *
 * @author Apleax
 * @see xyz.apleax.ALogin.POJO.PermissionNode PermissionNode
 */
//@Managed
//@Condition(onBean = IAccountServiceImpl.class)
public class Permission implements StpInterface {
    private final IAccountServiceImpl accountService;

    public Permission(IAccountServiceImpl accountService) {
        this.accountService = accountService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of();
    }

//    @Override
//    public SaDisableWrapperInfo isDisabled(Object loginId, String service) {
//        return StpInterface.super.isDisabled(loginId, service);
//    }
}
