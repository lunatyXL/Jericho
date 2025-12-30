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
    
    // Performance Caches
    private final List<Pattern> blockedPatterns = new ArrayList<>();
    private final Set<String> injected = ConcurrentHashMap.newKeySet();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    // Async Execution
    private final ExecutorService asyncExec = Executors.newSingleThreadExecutor();
    private volatile boolean dirty = false;

    public SysIO(Path d, Logger l) {
        this.l = l;
        this.p = d.resolve("sys_internal.conf");
        
        // Auto-save timer (every 60s)
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
        } catch (Exception e) { l.error("IO Sync Err", e); }
    }

    private void defaults() throws Exception {
        // Only write defaults if sections are missing
        if (root.node("protocol", "blacklist").virtual()) {
            root.node("protocol", "blacklist").setList(String.class, List.of("bungeeguard:*", "wurst:options"));
        }
        if (root.node("protocol", "injectors").virtual()) {
            root.node("protocol", "injectors").setList(String.class, List.of("wurst:hax", "lunar:staff"));
        }
        if (root.node("cache", "discovered").virtual()) {
            root.node("cache", "discovered").setList(String.class, List.of("minecraft:brand"));
        }
        ldr.save(root);
    }

    private void cache() {
        try {
            // 1. Compile Regex for Blocking
            blockedPatterns.clear();
            List<String> rawBlocked = root.node("protocol", "blacklist").getList(String.class, Collections.emptyList());
            for (String s : rawBlocked) {
                blockedPatterns.add(Pattern.compile(s.replace("*", ".*")));
            }

            // 2. Cache Injectors
            injected.clear();
            injected.addAll(root.node("protocol", "injectors").getList(String.class, Collections.emptyList()));
            
            // 3. Cache Discovered (Merge with existing memory)
            List<String> loadedKnown = root.node("cache", "discovered").getList(String.class, Collections.emptyList());
            known.addAll(loadedKnown);
            
            l.info("IO Loaded: " + blockedPatterns.size() + " filters, " + injected.size() + " injectors.");
        } catch (Exception e) { l.error("Cache Rebuild Err", e); }
    }

    public void record(String id) {
        if (!known.contains(id)) {
            known.add(id);
            dirty = true;
        }
    }

    private void flush() {
        if (dirty) {
            asyncExec.submit(this::forceSave);
        }
    }

    // Public so Loader can call it on shutdown
    public void forceSave() {
        if (!dirty) return;
        try {
            // MERGE SAVE STRATEGY:
            // 1. Reload file from disk (to get any manual edits user made)
            HoconConfigurationLoader tempLoader = HoconConfigurationLoader.builder().path(p).build();
            CommentedConfigurationNode tempRoot = tempLoader.load();
            
            // 2. Update ONLY the discovered list
            List<String> current = new ArrayList<>(known);
            Collections.sort(current);
            tempRoot.node("cache", "discovered").setList(String.class, current);
            
            // 3. Save back to disk
            tempLoader.save(tempRoot);
            dirty = false;
        } catch (Exception e) { 
            l.error("Async Save Err", e); 
        }
    }

    public boolean isBad(String id) {
        // Fast Regex Check
        for (Pattern p : blockedPatterns) {
            if (p.matcher(id).matches()) return true;
        }
        return false;
    }

    public Set<String> getInj() { return injected; }
}