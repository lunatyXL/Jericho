package in.lunaty.asher;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class SysIO {

    private final Path p;
    private final Logger l;
    private HoconConfigurationLoader ldr;
    private CommentedConfigurationNode root;
    
    private final List<Pattern> blockedPatterns = new ArrayList<>();
    private final Set<String> injected = ConcurrentHashMap.newKeySet();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    // Async Handling
    private final ExecutorService asyncExec = Executors.newSingleThreadExecutor();
    private volatile boolean dirty = false;

    public SysIO(Path d, Logger l) {
        this.l = l;
        this.p = d.resolve("sys_internal.conf");
        
        // Auto-save task (Every 60s)
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(this::flush, 60, 60, TimeUnit.SECONDS);
    }

    public void sync() {
        try {
            ldr = HoconConfigurationLoader.builder().path(p).build();
            if (p.toFile().exists()) {
                root = ldr.load();
            } else {
                root = ldr.createNode();
                defaults();
            }
            cache();
        } catch (Exception e) { l.error("IO Err", e); }
    }

    private void defaults() throws Exception {
        root.node("protocol", "blacklist").setList(String.class, List.of("bungeeguard:*", "wurst:options"));
        root.node("protocol", "injectors").setList(String.class, List.of("wurst:hax", "lunar:staff"));
        root.node("cache", "discovered").setList(String.class, List.of("minecraft:brand"));
        ldr.save(root);
    }

    private void cache() {
        try {
            blockedPatterns.clear();
            List<String> rawBlocked = root.node("protocol", "blacklist").getList(String.class, Collections.emptyList());
            for (String s : rawBlocked) {
                blockedPatterns.add(Pattern.compile(s.replace("*", ".*")));
            }

            injected.clear();
            injected.addAll(root.node("protocol", "injectors").getList(String.class, Collections.emptyList()));

            known.clear();
            known.addAll(root.node("cache", "discovered").getList(String.class, Collections.emptyList()));
            
            l.info("IO Synced: " + blockedPatterns.size() + " filters active.");
        } catch (Exception e) { l.error("Cache Err", e); }
    }

    public void record(String id) {
        if (!known.contains(id)) {
            known.add(id);
            dirty = true; 
        }
    }

    private void flush() {
        if (dirty) {
            forceSave();
        }
    }

    public void forceSave() {
        asyncExec.submit(() -> {
            try {
                List<String> current = new ArrayList<>(known);
                // Sort for readability
                Collections.sort(current);
                root.node("cache", "discovered").setList(String.class, current);
                ldr.save(root);
                dirty = false;
            } catch (Exception e) { l.error("Async Write Err", e); }
        });
    }

    public boolean isBad(String id) {
        for (Pattern p : blockedPatterns) {
            if (p.matcher(id).matches()) return true;
        }
        return false;
    }

    public Set<String> getInj() { return injected; }
}