package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 *
 *
 * @author Apleax
 */
@Data
@TableName(value = "librepremium_data", autoResultMap = true)
@AllArgsConstructor
@NoArgsConstructor
public class LibreLoginPO {
    @TableId(value = "uuid", type = IdType.INPUT)
    private UUID uuid;
    private String email;
    private String ip;
    private String joined;
    private String lastNickname;
}