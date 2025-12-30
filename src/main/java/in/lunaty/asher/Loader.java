package in.lunaty.asher;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "jericho",
    name = "Jericho",
    version = "2.2.0",
    description = "Internal network protocol handler.", 
    authors = {"Asher"}
)
public class Loader {

    private final ProxyServer srv;
    private final Logger log;
    private final Path dir;
    private SysIO io;

    @Inject
    public Loader(ProxyServer srv, Logger log, @DataDirectory Path dir) {
        this.srv = srv;
        this.log = log;
        this.dir = dir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {

        this.io = new SysIO(dir, log);
        this.io.sync();


        srv.getEventManager().register(this, new NetProtocol(log, io, srv));

        srv.getCommandManager().register(
            srv.getCommandManager().metaBuilder("sys_flush").build(),
            new AdminCmd(io)
        );
        
        log.info("Protocol layers active. Network filtration enabled.");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        if (io != null) {
            io.forceSave(); // Synchronous save on shutdown
        }
    }
}