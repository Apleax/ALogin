package xyz.apleax.ALogin.Entity.POJO;

import lombok.AllArgsConstructor;
import lombok.Data;
import xyz.apleax.ALogin.Enum.AccountIndex;

/**
 * @author Apleax
 */
@Data
@AllArgsConstructor
public class AccountIndexCache {
    private AccountIndex index;
    private String value;
}