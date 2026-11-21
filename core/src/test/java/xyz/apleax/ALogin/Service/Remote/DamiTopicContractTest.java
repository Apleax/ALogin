package xyz.apleax.ALogin.Service.Remote;

import org.junit.jupiter.api.Test;
import org.noear.dami2.solon.annotation.DamiTopic;
import xyz.apleax.ALogin.Service.Account.LoginServiceImpl;
import xyz.apleax.ALogin.Service.Account.LogoutServiceImpl;
import xyz.apleax.ALogin.Service.Account.RegisterServiceImpl;
import xyz.apleax.ALogin.Service.Account.ResetPasswordServiceImpl;
import xyz.apleax.ALogin.Service.Account.VerifyCodeServiceImpl;
import xyz.apleax.ALogin.Service.Bedrock.BedrockServiceImpl;
import xyz.apleax.ALogin.Service.Premium.PremiumServiceImpl;
import xyz.apleax.ALogin.Service.User.AvatarServiceImpl;
import xyz.apleax.ALogin.Service.User.ChangeNickNameServiceImpl;
import xyz.apleax.ALogin.Service.User.GameProfileServiceImpl;
import xyz.apleax.ALogin.Service.User.GetUserInfoServiceImpl;
import xyz.apleax.ALogin.Service.User.UploadSkinServiceImpl;
import xyz.apleax.ALogin.Service.User.UserConfigServiceImpl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dami 远程服务契约测试：实现类与接口按主题字符串 + 方法名/参数耦合，
 * 编译期无法校验，这里在构建期兜底（签名漂移会导致构建失败）
 */
class DamiTopicContractTest {
    private static final Map<Class<?>, List<Class<?>>> CONTRACT = Map.of(
            AccountService.class, List.of(LoginServiceImpl.class, LogoutServiceImpl.class,
                    RegisterServiceImpl.class, ResetPasswordServiceImpl.class, VerifyCodeServiceImpl.class),
            BedrockService.class, List.of(BedrockServiceImpl.class),
            PremiumService.class, List.of(PremiumServiceImpl.class),
            UserService.class, List.of(GetUserInfoServiceImpl.class, UploadSkinServiceImpl.class,
                    ChangeNickNameServiceImpl.class, GameProfileServiceImpl.class, AvatarServiceImpl.class,
                    UserConfigServiceImpl.class));

    @Test
    void implTopicsMatchInterface() {
        for (Map.Entry<Class<?>, List<Class<?>>> entry : CONTRACT.entrySet()) {
            String topic = entry.getKey().getAnnotation(DamiTopic.class).value();
            for (Class<?> impl : entry.getValue()) {
                DamiTopic anno = impl.getAnnotation(DamiTopic.class);
                assertTrue(anno != null, impl.getSimpleName() + " 缺少 @DamiTopic");
                assertEquals(topic, anno.value(),
                        impl.getSimpleName() + " 的主题与 " + entry.getKey().getSimpleName() + " 不一致");
            }
        }
    }

    @Test
    void everyInterfaceMethodHasMatchingImpl() {
        for (Map.Entry<Class<?>, List<Class<?>>> entry : CONTRACT.entrySet()) {
            for (Method method : entry.getKey().getMethods()) {
                boolean matched = entry.getValue().stream().anyMatch(impl -> {
                    try {
                        impl.getMethod(method.getName(), method.getParameterTypes());
                        return true;
                    } catch (NoSuchMethodException e) {
                        return false;
                    }
                });
                assertTrue(matched, entry.getKey().getSimpleName() + "#" + method.getName()
                        + " 没有任何实现类提供同名同参方法");
            }
        }
    }

    /**
     * 接口方法与实现方法的返回类型必须一致。
     * <p>
     * Dami LPC 按主题字符串 + 反射派发，调用方拿的是接口签名里的返回类型，
     * 实现类返回类型不一致编译器查不出来，运行期会变成调用方的 ClassCastException。
     */
    @Test
    void implReturnTypesMatchInterface() {
        for (Map.Entry<Class<?>, List<Class<?>>> entry : CONTRACT.entrySet()) {
            for (Method method : entry.getKey().getMethods()) {
                boolean matched = false;
                for (Class<?> impl : entry.getValue()) {
                    Method implMethod;
                    try {
                        // 按 方法名 + 参数类型 精确查，避免实现类同名重载（如 PremiumServiceImpl.bind）误匹配
                        implMethod = impl.getMethod(method.getName(), method.getParameterTypes());
                    } catch (NoSuchMethodException e) {
                        continue;
                    }
                    matched = true;
                    assertEquals(method.getReturnType(), implMethod.getReturnType(),
                            impl.getSimpleName() + "#" + method.getName() + " 的返回类型与 "
                                    + entry.getKey().getSimpleName() + " 不一致");
                }
                assertTrue(matched, entry.getKey().getSimpleName() + "#" + method.getName()
                        + " 没有任何实现类提供同名同参方法");
            }
        }
    }

    /**
     * Dami 提供方类不得存在同名 public 重载。
     * <p>
     * Dami2 按"主题 + 方法名"派发，同名重载会让提供方挑到参数不匹配的那个方法，运行期抛
     * {@code IllegalArgumentException: argument type mismatch}（已实测发生）。编译器不报错，
     * 上面两条断言也只按"方法名+参数"精确匹配，同样发现不了重载歧义，因此单独兜底。
     */
    @Test
    void implsHaveNoPublicOverloads() {
        for (List<Class<?>> impls : CONTRACT.values()) {
            for (Class<?> impl : impls) {
                TreeSet<String> duplicated = new TreeSet<>();
                Arrays.stream(impl.getDeclaredMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .collect(java.util.stream.Collectors.groupingBy(Method::getName,
                                java.util.stream.Collectors.counting()))
                        .forEach((name, count) -> {
                            if (count > 1) duplicated.add(name);
                        });
                assertTrue(duplicated.isEmpty(),
                        impl.getSimpleName() + " 存在同名 public 重载，Dami2 按方法名派发会挑错方法: " + duplicated);
            }
        }
    }
}
