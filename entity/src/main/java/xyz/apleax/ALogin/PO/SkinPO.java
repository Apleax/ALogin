package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.BlobTypeHandler;
import xyz.apleax.ALogin.TypeHandler.UuidTypeHandler;

import java.util.UUID;

/**
 *
 *
 * @author Apleax
 */
@Data
@TableName(value = "skin", autoResultMap = true)
@AllArgsConstructor
@NoArgsConstructor
public class SkinPO {
    private Long id;
    /**
     * 服务器内uuid
     */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID mcUuid;
    /**
     * 皮肤数据
     */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID skinUuid;
    /**
     * 皮肤文件
     */
    @TableField(typeHandler = BlobTypeHandler.class)
    private byte[] skinImg;
}
