package in.lunaty.asher;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class NetProtocol {

    private final Logger log;
    private final SysIO io;
    private final ProxyServer srv;

    private static final MinecraftChannelIdentifier MODERN_REG = MinecraftChannelIdentifier.create("minecraft", "register");
    private static final LegacyChannelIdentifier LEGACY_REG = new LegacyChannelIdentifier("REGISTER");

    public NetProtocol(Logger log, SysIO io, ProxyServer srv) {
        this.log = log;
        this.io = io;
        this.srv = srv;
    }

    @Subscribe
    public void onConnect(ServerPostConnectEvent e) {
        Player p = e.getPlayer();
        Set<String> injectors = io.getInj();

        if (injectors.isEmpty()) return;

        byte[] payload = String.join("\0", injectors).getBytes(StandardCharsets.UTF_8);
        if (p.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_13) >= 0) {
            p.sendPluginMessage(MODERN_REG, payload);
        } else {
            p.sendPluginMessage(LEGACY_REG, payload);
        }
    }

    @Subscribe
    public void flow(PluginMessageEvent e) {
        ChannelIdentifier id = e.getIdentifier();
        String name = id.getId();

        io.record(name);
        if (io.isBad(name)) {
            e.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }

        if (e.getSource() instanceof ServerConnection && e.getTarget() instanceof Player) {
            if (isReg(id)) {
                handleRegister(e, (Player) e.getTarget());
            }
        }
    }

    private boolean isReg(ChannelIdentifier id) {
        if (id instanceof MinecraftChannelIdentifier) {
            return ((MinecraftChannelIdentifier) id).getName().equals("register");
        }
        return id.getId().equalsIgnoreCase("REGISTER");
    }

    private void handleRegister(PluginMessageEvent e, Player p) {
        try {
            String raw = new String(e.getData(), StandardCharsets.UTF_8);
            Set<String> channels = new HashSet<>(Arrays.asList(raw.split("\0")));

            channels.removeIf(io::isBad);

            channels.addAll(io.getInj());

            byte[] out = String.join("\0", channels).getBytes(StandardCharsets.UTF_8);

            e.setResult(PluginMessageEvent.ForwardResult.handled());
            p.sendPluginMessage(e.getIdentifier(), out);

        } catch (Exception ex) {
            log.error("Packet Stream Err", ex);
        }
    }
}