package in.lunaty.asher;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AdminCmd implements SimpleCommand {

    private final SysIO io;

    public AdminCmd(SysIO io) {
        this.io = io;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("sys.internal.admin")) {
            return;
        }

        io.sync(); 
        invocation.source().sendMessage(Component.text("Protocol tables flushed.", NamedTextColor.GREEN));
    }
}