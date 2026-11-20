package xyz.apleax.ALogin.Error;

/**
 * 统一错误码，成功码恒为 200。
 */
public enum ErrorCode {
    SUCCEED(200, "成功"),
    PARAM_MISSING(400001, "请求参数缺失"),
    PARAM_INVALID(400002, "参数格式错误"),
    BODY_MISSING(400003, "请求体为空"),
    EMAIL_INVALID(400004, "邮箱格式错误"),
    EMAIL_EMPTY(400005, "邮箱不能为空"),
    PASSWORD_EMPTY(400006, "密码不能为空"),
    NICKNAME_EMPTY(400007, "昵称不能为空"),
    NICKNAME_INVALID(400008, "用户名只能包含英文字母、数字和下划线，长度需为3-16个字符"),
    AVATAR_UUID_MISSING(400009, "uuid 不能为空"),
    AVATAR_UUID_INVALID(400010, "uuid 格式非法"),
    AVATAR_SIZE_INVALID(400011, "size 必须是 1-512 的整数"),
    AVATAR_OVERLAY_INVALID(400012, "overlay 只支持 true 或 false"),
    LOGIN_TYPE_MISSING(400013, "登录类型缺失"),
    LOGIN_TYPE_UNSUPPORTED(400014, "暂不支持该登录方式"),
    BEDROCK_XUID_INVALID(400015, "XUID 非法"),
    NEW_PASSWORD_EMPTY(400016, "新密码不能为空"),
    BIND_TYPE_INVALID(400017, "不支持的绑定类型"),
    NOT_LOGIN(401001, "未登录"),
    IP_UNAVAILABLE(401002, "无法获取客户端 IP，请稍后再试"),
    GAME_TOKEN_MISSING(401003, "缺少进服会话 token"),
    GAME_TOKEN_INVALID(401004, "会话已过期，请重新进入服务器"),
    CREDENTIAL_MISMATCH(401005, "%s或密码错误"),
    CLIENT_KEY_MISMATCH(401006, "密钥校验失败", 403),
    LOGIN_FAILED(401007, "登录失败，请稍后再试"),
    ALGORITHM_UNAVAILABLE(401008, "该账号使用的加密算法已失效，请联系管理员"),
    LOGIN_TOKEN_INVALID(401009, "登录令牌已失效，无法绑定"),
    LOGIN_CREDENTIAL_MISSING(401010, "登录凭证缺失，请重新进入服务器"),
    PERMISSION_DENIED(403001, "你没有这个权限"),
    GROUP_DENIED(403002, "你没有这个权限组"),
    PERMISSION_WRITE_DISABLED(403003, "权限写入未开启"),
    PERMISSION_NODES_EMPTY(403004, "没有需要变更的权限节点"),
    PERMISSION_CHANGE_FAILED(403005, "权限变更失败：%s"),
    EMAIL_NOT_REGISTERED(404001, "该邮箱未注册"),
    ACCOUNT_NOT_FOUND(404002, "账号不存在"),
    VERIFY_CODE_INVALID(404003, "验证码错误"),
    PREMIUM_NOT_BOUND(404004, "该正版账号尚未绑定 ALogin 账号"),
    PREMIUM_NOT_BOUND_HINT(404005, "该正版账号尚未绑定 ALogin 账号，请先在网页登录后绑定"),
    BEDROCK_NOT_BOUND(404006, "该基岩版账号尚未绑定 ALogin 账号"),
    FLOW_NOT_FOUND(404007, "授权会话不存在或已过期，请重新发起"),
    PROFILE_NOT_FOUND(404008, "玩家档案不存在"),
    PREMIUM_BIND_NOT_FOUND(404009, "当前账号未绑定正版账号"),
    BEDROCK_BIND_NOT_FOUND(404010, "当前账号未绑定基岩版账号"),
    EMAIL_TAKEN(409001, "该邮箱已注册"),
    NICKNAME_TAKEN(409002, "该用户名已被使用，请更换其他用户名"),
    NICKNAME_CHANGE_COOLDOWN(409003, "%d日后可以再次更改昵称"),
    NICKNAME_REUSE_COOLDOWN(409004, "%d日后可以再次使用该昵称"),
    PREMIUM_TAKEN(409005, "该正版账号已绑定到其他账号，如需更换请先解绑"),
    BEDROCK_TAKEN(409006, "该基岩版账号已绑定到其他账号，如需更换请先解绑"),
    SKIN_TYPE_UNSUPPORTED(422001, "仅支持 PNG 格式的皮肤文件"),
    SKIN_TOO_LARGE(422002, "皮肤文件不能超过 200KB"),
    SKIN_EMPTY(422003, "皮肤文件为空"),
    SKIN_DIMENSION_INVALID(422004, "皮肤尺寸必须为 64x64 像素"),
    SKIN_REQUEST_INVALID(422005, "更新皮肤失败"),
    VERIFY_CODE_FREQUENT(429001, "获取失败"),
    INTERNAL_ERROR(500001, "服务器内部错误"),
    REGISTER_FAILED(500002, "注册失败，请稍后再试"),
    UPDATE_FAILED(500003, "更新失败，请稍后再试"),
    BIND_FAILED(500004, "绑定失败，请稍后再试"),
    BIND_FAILED_HINT(500005, "绑定失败，请稍后再试或联系管理员"),
    LOGIN_PERSIST_FAILED(500006, "登录失败，请稍后再试"),
    MAIL_SEND_FAILED(503001, "验证码发送失败，请稍后再试"),
    PREMIUM_SERVICE_UNAVAILABLE(503002, "微软授权服务不可用，请稍后再试"),
    PREMIUM_RESPONSE_INVALID(503003, "微软授权服务响应异常，请稍后再试"),
    PREMIUM_CONNECT_FAILED(503004, "微软授权服务连接失败，请稍后再试"),
    REQUEST_INTERRUPTED(503005, "请求被中断，请重试"),
    MICROSOFT_TOKEN_MISSING(503006, "微软授权缺失，请重新发起"),
    XBOX_AUTH_FAILED(503007, "Xbox Live 认证失败，请稍后重试"),
    XBOX_RESPONSE_INVALID(503008, "Xbox Live 认证响应异常"),
    XSTS_AUTH_FAILED(503009, "该微软账号无法登录 Xbox Live（错误码 %d）"),
    XSTS_REGION_UNSUPPORTED(503010, "该账号所在地区不支持 Xbox Live"),
    XSTS_AGE_VERIFICATION(503011, "该账号需要进行年龄验证（仅韩国地区）"),
    XSTS_FAMILY_REQUIRED(503012, "未成年账号需先加入 Microsoft 家庭组"),
    XSTS_NO_XBOX_PROFILE(503013, "该微软账号还没有 Xbox 档案，请先登录 xbox.com 创建"),
    XSTS_RESPONSE_INVALID(503014, "XSTS 授权响应异常"),
    MINECRAFT_LOGIN_FAILED(503015, "Minecraft 登录失败（HTTP %d/%d），请稍后重试或联系管理员"),
    MINECRAFT_LOGIN_FAILED_SIMPLE(503016, "Minecraft 登录失败"),
    MINECRAFT_RESPONSE_INVALID(503017, "Minecraft 登录响应异常"),
    MINECRAFT_PROFILE_ABSENT(503018, "该微软账号没有 Minecraft Java 版档案（未购买或未创建档案）"),
    MINECRAFT_PROFILE_FAILED(503019, "获取 Minecraft 档案失败，请稍后重试"),
    PREMIUM_VERIFY_FAILED(503020, "正版验证服务异常，请稍后再试"),
    PREMIUM_VERIFY_INTERRUPTED(503021, "正版验证被中断，请重试"),
    PREMIUM_VERIFY_INVALID(503022, "Minecraft 档案响应异常"),
    PREMIUM_POLL_INVALID(503023, "微软授权响应异常，请重试"),
    PREMIUM_AUTH_DECLINED(503024, "已拒绝微软账号授权"),
    PREMIUM_CODE_EXPIRED(503025, "授权码已过期，请重新发起"),
    PREMIUM_AUTH_FAILED(503026, "微软账号授权失败，请重试"),
    PREMIUM_FLOW_TIMEOUT(503027, "授权超时，请重新发起"),
    PREMIUM_FLOW_CANCELLED(503028, "授权已取消，请重新发起"),
    PREMIUM_FLOW_FAILED(503029, "授权服务异常，请重试");

    /**
     * 业务码
     */
    private final int code;
    /**
     * 提示文案模板
     */
    private final String message;
    /**
     * 对应 HTTP 状态码
     */
    private final int httpStatus;

    ErrorCode(int code, String message) {
        this(code, message, 200);
    }

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean isSuccess() {
        return code == 200;
    }
}
