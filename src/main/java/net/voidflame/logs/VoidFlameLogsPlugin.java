package net.voidflame.logs;

import net.voidflame.core.storage.StorageService;
import net.voidflame.core.api.AuditLogService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class VoidFlameLogsPlugin extends JavaPlugin implements Listener {
    private StorageService storage;
    private LogService logs;

    public static final class LogService implements AuditLogService {
        private final VoidFlameLogsPlugin plugin;
        private LogService(VoidFlameLogsPlugin plugin){this.plugin=plugin;}
        @Override
        public CompletableFuture<Void> log(String actor,String action,String target,String metadata){
            String id=System.currentTimeMillis()+"-"+UUID.randomUUID();
            String value=String.join("|", safe(actor),safe(action),safe(target),Long.toString(System.currentTimeMillis()),safe(metadata));
            return plugin.storage.database().execute("INSERT INTO audit_logs(actor,action,target,timestamp,metadata_json) VALUES(?,?,?,?,?)", safe(actor), safe(action), safe(target), System.currentTimeMillis(), json(metadata));
        }
        public CompletableFuture<Void> log(String action,String message){return log("SYSTEM",action,"",message);}
    }

    @Override public void onEnable(){
        saveDefaultConfig();
        var r=getServer().getServicesManager().getRegistration(StorageService.class);
        if(r==null || (storage=r.getProvider())==null){getLogger().severe("VoidFlame-Core storage unavailable.");getServer().getPluginManager().disablePlugin(this);return;}
        logs=new LogService(this);
        getServer().getServicesManager().register(LogService.class,logs,this,ServicePriority.Normal);
        getServer().getServicesManager().register(AuditLogService.class, logs, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this,this);
        long retain = Math.max(1L, getConfig().getLong("settings.retain-days", 90L));
        long intervalTicks = Math.max(20L, getConfig().getLong("settings.retention-check-interval-ticks", 20L * 3600L));
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> prune(retain), intervalTicks, intervalTicks);
        PluginCommand c=getCommand("vflogs"); if(c!=null){c.setExecutor(this::command);c.setTabCompleter(this::tab);}
        getLogger().info("VoidFlame-Logs enabled.");
    }

    private static String safe(String s){return s==null?"":s.replace("\\","/").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    private static String json(String s){return "\"" + safe(s) + "\"";}
    private static String format(Map<String,Object> row){return "["+row.get("timestamp")+"] "+row.get("actor")+" "+row.get("action")+" -> "+row.get("target")+" | "+row.get("metadata_json");}

    @EventHandler public void join(PlayerJoinEvent e){if(getConfig().getBoolean("events.player",true))logs.log(e.getPlayer().getUniqueId().toString(),"JOIN",e.getPlayer().getName(),"firstJoin="+e.getPlayer().hasPlayedBefore());}
    @EventHandler public void quit(PlayerQuitEvent e){if(getConfig().getBoolean("events.player",true))logs.log(e.getPlayer().getUniqueId().toString(),"QUIT",e.getPlayer().getName(),"");}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){if(getConfig().getBoolean("events.admin",true))logs.log(e.getPlayer().getUniqueId().toString(),"COMMAND",e.getPlayer().getName(),e.getMessage());}
    @EventHandler public void kick(PlayerKickEvent e){if(getConfig().getBoolean("events.player",true))logs.log("SYSTEM","KICK",e.getPlayer().getName(),"reason="+e.getReason());}
    @EventHandler public void death(PlayerDeathEvent e){if(getConfig().getBoolean("events.player",true))logs.log(e.getEntity().getUniqueId().toString(),"DEATH",e.getEntity().getName(),e.getDeathMessage()==null?"":e.getDeathMessage());}

    private boolean command(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("voidflame.logs.view")){sender.sendMessage("§cNo permission.");return true;}
        int page=1;
        String action="";
        String actor="";
        String target="";
        String metadata="";
        if(args.length>0 && args[0].equalsIgnoreCase("gui") && sender instanceof Player player){openGui(player,1,"");return true;} if(args.length>0) try{page=Math.max(1,Integer.parseInt(args[0]));}catch(NumberFormatException ignored){action=args[0];}
        if(args.length>1) action=args[1];
        if(args.length>2) actor=args[2];
        if(args.length>3) target=args[3];
        if(args.length>4) metadata=args[4];
        int limit=Math.max(1, Math.min(getConfig().getInt("settings.max-page-size",100), getConfig().getInt("settings.search-page-size",25)));
        int offset=(page-1)*limit;
        StringBuilder sql=new StringBuilder("SELECT actor,action,target,timestamp,metadata_json FROM audit_logs WHERE 1=1");
        List<Object> params=new ArrayList<>();
        if(!action.isBlank()){sql.append(" AND action LIKE ?");params.add("%"+action+"%");}
        if(!actor.isBlank()){sql.append(" AND actor LIKE ?");params.add("%"+actor+"%");}
        if(!target.isBlank() && getConfig().getBoolean("filters.target-enabled", true)){sql.append(" AND target LIKE ?");params.add("%"+target+"%");}
        if(!metadata.isBlank() && getConfig().getBoolean("filters.metadata-enabled", true)){sql.append(" AND metadata_json LIKE ?");params.add("%"+metadata+"%");}
        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit); params.add(offset);
        final int requestedPage = page;
        final String requestedAction = action;
        final String requestedActor = actor;
        final String requestedTarget = target;
        final String requestedMetadata = metadata;
        storage.database().query(sql.toString(), params.toArray())
            .thenAccept(rows -> Bukkit.getScheduler().runTask(this,()->{
                sender.sendMessage("§8§m----------------");
                sender.sendMessage("§bVoidFlame Logs §7Page "+requestedPage+" §8| §faction="+(requestedAction.isBlank()?"*":requestedAction)+" §8| §factor="+(requestedActor.isBlank()?"*":requestedActor)+" §8| §ftarget="+(requestedTarget.isBlank()?"*":requestedTarget)+" §8| §fmeta="+(requestedMetadata.isBlank()?"*":requestedMetadata));
                if(rows.isEmpty()) sender.sendMessage("§7No logs matched.");
                for(var row:rows) sender.sendMessage("§7• §f"+format(row));
                sender.sendMessage("§8§m----------------");
            })).exceptionally(err->{ Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§cCould not read logs.")); return null; });
        return true;
    }
    private void openGui(Player player,int page,String action){
        Inventory inv=Bukkit.createInventory(null,54,"§8VoidFlame §5• §dLogs §7"+page);
        ItemStack filler=new ItemStack(org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fm=filler.getItemMeta(); if(fm!=null){fm.setDisplayName(" ");filler.setItemMeta(fm);}
        for(int i=0;i<54;i++)inv.setItem(i,filler.clone());
        button(inv,4,org.bukkit.Material.BOOK,"§5§lAUDIT LOG CENTER","§7Persistent logs from VoidFlame-Core","§8Filter and inspect server activity");
        button(inv,45,org.bukkit.Material.ARROW,"§e§lPREVIOUS","§7Open previous page");
        button(inv,49,org.bukkit.Material.COMPARATOR,"§b§lFILTER","§7Current action: "+(action.isBlank()?"All":action),"§8Use /vflogs <page> <action> for exact filtering");
        button(inv,53,org.bukkit.Material.BARRIER,"§c§lCLOSE");
        int limit=45,offset=Math.max(0,page-1)*limit;
        String sql="SELECT actor,action,target,timestamp,metadata_json FROM audit_logs WHERE 1=1"+(action.isBlank()?"":" AND action LIKE ?")+" ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        List<Object> params=new ArrayList<>(); if(!action.isBlank())params.add("%"+action+"%");params.add(limit);params.add(offset);
        storage.database().query(sql,params.toArray()).thenAccept(rows->Bukkit.getScheduler().runTask(this,()->{
            int slot=9;
            for(var row:rows){if(slot>=45)break; button(inv,slot++,org.bukkit.Material.PAPER,"§f"+row.get("action"),"§7Actor: §f"+row.get("actor"),"§7Target: §f"+row.get("target"),"§8"+row.get("timestamp")); }
            player.openInventory(inv);
        })).exceptionally(e->{player.sendMessage("§cCould not read logs.");return null;});
    }
    private void button(org.bukkit.inventory.Inventory inv,int slot,org.bukkit.Material mat,String name,String... lore){
        if(slot>=inv.getSize())return; org.bukkit.inventory.ItemStack it=new org.bukkit.inventory.ItemStack(mat); org.bukkit.inventory.meta.ItemMeta m=it.getItemMeta();
        if(m!=null){m.setDisplayName(name);m.setLore(Arrays.asList(lore));it.setItemMeta(m);} inv.setItem(slot,it);
    }
    @EventHandler public void logsGuiClick(org.bukkit.event.inventory.InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().startsWith("§8VoidFlame §5• §dLogs"))return;
        e.setCancelled(true); int slot=e.getRawSlot(); if(slot==45){openGui(p,1,"");} else if(slot==53)p.closeInventory();
    }

    private void prune(long retainDays) {
        long cutoff = System.currentTimeMillis() - retainDays * 24L * 60L * 60L * 1000L;
        storage.database().execute("DELETE FROM audit_logs WHERE timestamp < ?", cutoff)
                .exceptionally(error -> { getLogger().warning("Log retention cleanup failed: " + error.getMessage()); return null; });
    }

    private List<String> tab(CommandSender s,Command c,String a,String[] args){
        if(args.length==1)return List.of("1","2","3","10","25","50");
        if(args.length==2)return List.of("DUEL_FINISH","PARTY_MATCH_FINISH","KIT_APPLY","ARENA_RESET_SUCCESS","ARENA_RESET_FAILURE","SECURITY_VIOLATION");
        return List.of();
    }
    @Override public void onDisable(){
        if(logs!=null)getServer().getServicesManager().unregister(LogService.class,logs);
        if(logs!=null)getServer().getServicesManager().unregister(AuditLogService.class,logs);
    }
}