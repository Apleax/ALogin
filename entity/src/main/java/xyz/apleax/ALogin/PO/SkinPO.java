package xyz.apleax.ALogin.PO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.BlobTypeHandler;

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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private UUID mcUuid;
    /**
     * 皮肤数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private UUID skinUuid;
    /**
     * 皮肤文件
     */
    @TableField(typeHandler = BlobTypeHandler.class)
    private byte[] skinImg;
}
