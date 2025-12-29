package in.lunaty.asher;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NetProtocol {

    private final Logger log;
    private final SysIO io;

    public NetProtocol(Logger log, SysIO io) {
        this.log = log;
        this.io = io;
    }

    @Subscribe
    public void flow(PluginMessageEvent e) {
        ChannelIdentifier id = e.getIdentifier();
        String name = id.getId();

        // [Phase 1] Auto-Discovery
        io.record(name);

        // [Phase 2] Filtration (Traffic Blocking)
        if (io.isBad(name)) {
            e.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }

        // [Phase 3] Manipulation (Register Packets)
        // Only intercepts Server -> Player
        if (e.getSource() instanceof ServerConnection && e.getTarget() instanceof Player) {
            if (isReg(id)) {
                processStream(e, (Player) e.getTarget());
            }
        }
    }

    private boolean isReg(ChannelIdentifier id) {
        if (id instanceof MinecraftChannelIdentifier) {
            return ((MinecraftChannelIdentifier) id).getName().equals("register");
        }
        return id.getId().equals("REGISTER"); 
    }

    private void processStream(PluginMessageEvent e, Player p) {
        try {
            String raw = new String(e.getData(), StandardCharsets.UTF_8);
            Set<String> channels = new HashSet<>(Arrays.asList(raw.split("\0")));
            channels.forEach(io::record);
            channels.removeIf(io::isBad);
            channels.addAll(io.getInj());

            byte[] out = String.join("\0", channels).getBytes(StandardCharsets.UTF_8);

            e.setResult(PluginMessageEvent.ForwardResult.handled());
            p.sendPluginMessage(e.getIdentifier(), out);

        } catch (Exception ex) {
            log.error("Stream Err", ex);
        }
    }
}