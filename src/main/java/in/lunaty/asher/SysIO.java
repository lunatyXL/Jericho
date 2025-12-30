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
    private final Map<String, String> dataSims = new ConcurrentHashMap<>();
    private final Set<String> regOnly = ConcurrentHashMap.newKeySet();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    private final ExecutorService asyncExec = Executors.newSingleThreadExecutor();
    private volatile boolean dirty = false;

    public SysIO(Path d, Logger l) {
        this.l = l;
        this.p = d.resolve("sys_internal.conf");
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
        if (root.node("protocol", "blacklist").virtual()) {
            root.node("protocol", "blacklist").setList(String.class, List.of("bungeeguard:*", "minecraft:brand", "MC|Brand"));
        }
        
        if (root.node("protocol", "simulations").virtual()) {
            Map<String, String> defaults = new HashMap<>();
            defaults.put("wurst:hax", "DATA|Enabled");
            defaults.put("lunar:staff", "REG|");
            root.node("protocol", "simulations").set(defaults);
        }
        
        if (root.node("cache", "discovered").virtual()) {
            root.node("cache", "discovered").setList(String.class, List.of("minecraft:brand"));
        }
        ldr.save(root);
    }

    private void cache() {
        try {
            blockedPatterns.clear();
            List<String> rawBlocked = root.node("protocol", "blacklist").getList(String.class, Collections.emptyList());
            for (String s : rawBlocked) {
                blockedPatterns.add(Pattern.compile(s.replace("*", ".*")));
            }

            dataSims.clear();
            regOnly.clear();
            Map<Object, Object> rawSims = root.node("protocol", "simulations").childrenMap().entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().getString()), HashMap::putAll);

            rawSims.forEach((k, v) -> {
                String channel = (String) k;
                String val = (String) v;
                if (val.startsWith("DATA|")) {
                    dataSims.put(channel, val.substring(5));
                } else {
                    regOnly.add(channel);
                }
            });
            
            List<String> loadedKnown = root.node("cache", "discovered").getList(String.class, Collections.emptyList());
            known.addAll(loadedKnown);
            
        } catch (Exception e) { l.error("Cache Err", e); }
    }

    public void record(String id) {
        if (!known.contains(id)) {
            known.add(id);
            dirty = true;
        }
    }

    public void forceSave() {
        if (!dirty) return;
        try {
            HoconConfigurationLoader tempLoader = HoconConfigurationLoader.builder().path(p).build();
            CommentedConfigurationNode tempRoot = tempLoader.load();
            List<String> current = new ArrayList<>(known);
            Collections.sort(current);
            tempRoot.node("cache", "discovered").setList(String.class, current);
            tempLoader.save(tempRoot);
            dirty = false;
        } catch (Exception e) { l.error("Save Err", e); }
    }

    private void flush() {
        if (dirty) asyncExec.submit(this::forceSave);
    }

    public boolean isBad(String id) {
        for (Pattern p : blockedPatterns) {
            if (p.matcher(id).matches()) return true;
        }
        return false;
    }

    public Set<String> getRegList() { return regOnly; }
    public Map<String, String> getDataSims() { return dataSims; }
}