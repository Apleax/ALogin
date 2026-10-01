package xyz.apleax.ALogin.TypeHandler;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UUID 与数据库字符串列的互转处理器：写 36 位小写，读兼容 32 位与历史引号格式。
 *
 * @author Apleax
 */
@Slf4j
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * 写入参数；null 时直接写 SQL NULL。
     */
    @Override
    public void setParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) ps.setNull(i, Types.VARCHAR);
        else setNonNullParameter(ps, i, parameter, jdbcType);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getString(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getString(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getString(columnIndex));
    }

    /**
     * 解析 UUID；兼容引号与 32 位格式，非法值记日志并返回 null。
     */
    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) return null;
        String uuid = value.trim();
        if (uuid.length() >= 2 && uuid.charAt(0) == '"' && uuid.charAt(uuid.length() - 1) == '"')
            uuid = uuid.substring(1, uuid.length() - 1).trim();
        if (uuid.length() == 32 && uuid.chars().allMatch(c -> Character.digit(c, 16) >= 0))
            uuid = uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16)
                    + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20);
        if (!UUID_PATTERN.matcher(uuid).matches()) {
            log.error("非法的 UUID 数据，已忽略: '{}'", value);
            return null;
        }
        return UUID.fromString(uuid);
    }
}
