package xyz.apleax.ALogin.Command;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

/**
 *
 *
 * @author Apleax
 */
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginCommand extends Command {
    public LoginCommand() {
        super("login", "l");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /command <number>"));

        var numberArgument = ArgumentType.Integer("my-number");

        // Callback executed if the argument has been wrongly used
        numberArgument.setCallback((sender, exception) -> {
            final String input = exception.getInput();
            sender.sendMessage("The number " + input + " is invalid!");
        });

        addSyntax((sender, context) -> {
            final int number = context.get(numberArgument);
            sender.sendMessage("You typed the number " + number);
        }, numberArgument);

    }
}
