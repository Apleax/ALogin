package xyz.apleax.ALogin;

import cn.dev33.satoken.exception.NotLoginException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.exception.ConstructionException;
import org.noear.solon.core.exception.StatusException;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.ValidatorException;

/**
 * @author Apleax
 */
@Slf4j
@Component
public class AppFitter implements Filter {
    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        try {
            chain.doFilter(ctx);
        } catch (NotLoginException e) {
            ctx.render(Result.failure("Not login"));
        } catch (ValidatorException e) {
            ctx.render(e.getResult());
        } catch (ConstructionException |
                 StatusException e) {
            if (e instanceof StatusException) if (!(e.getCause() instanceof InvalidTypeIdException ||
                    e.getCause() instanceof JsonParseException)) return;
            ctx.render(Result.failure("Invalid parameter"));
        } catch (Exception e) {
            ctx.render(Result.failure("Server error"));
            log.error(e.getLocalizedMessage());
        }
    }
}
