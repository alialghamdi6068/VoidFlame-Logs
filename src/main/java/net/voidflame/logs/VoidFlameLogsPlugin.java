package net.voidflame.logs;

import net.voidflame.core.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    public static final class LogService {
        private final VoidFlameLogsPlugin plugin;
        private LogService(VoidFlameLogsPlugin plugin){this.plugin=plugin;}
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
        getServer().getPluginManager().registerEvents(this,this);
        PluginCommand c=getCommand("vflogs"); if(c!=null){c.setExecutor(this::command);c.setTabCompleter(this::tab);}
        getLogger().info("VoidFlame-Logs enabled.");
    }

    private static String safe(String s){return s==null?"":s.replace("\\","/").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}\n    private static String json(String s){return "\"" + safe(s) + "\"";}\n    private static String format(Map<String,Object> row){return "["+row.get("timestamp")+"] "+row.get("actor")+" "+row.get("action")+" -> "+row.get("target")+" | "+row.get("metadata_json");}

    @EventHandler public void join(PlayerJoinEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"JOIN",e.getPlayer().getName(),"firstJoin="+e.getPlayer().hasPlayedBefore());}
    @EventHandler public void quit(PlayerQuitEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"QUIT",e.getPlayer().getName(),"");}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"COMMAND",e.getPlayer().getName(),e.getMessage());}
    @EventHandler public void kick(PlayerKickEvent e){logs.log("SYSTEM","KICK",e.getPlayer().getName(),"reason="+e.getReason());}
    @EventHandler public void death(PlayerDeathEvent e){logs.log(e.getEntity().getUniqueId().toString(),"DEATH",e.getEntity().getName(),e.getDeathMessage()==null?"":e.getDeathMessage());}

    private boolean command(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("voidflame.logs.view")){sender.sendMessage("§cNo permission.");return true;}
        int limit=10;
        if(args.length>0) try{limit=Math.max(1,Math.min(50,Integer.parseInt(args[0])));}catch(NumberFormatException ignored){}
        storage.database().query("SELECT actor,action,target,timestamp,metadata_json FROM audit_logs ORDER BY timestamp DESC LIMIT ?", limit)
            .thenAccept(rows -> Bukkit.getScheduler().runTask(this,()->{
                sender.sendMessage("§8§m----------------");
                sender.sendMessage("§bVoidFlame Logs §7("+rows.size()+")");
                for(var row:rows) sender.sendMessage("§7• §f"+format(row));
                sender.sendMessage("§8§m----------------");
            })).exceptionally(err->{sender.sendMessage("§cCould not read logs.");return null;});
        return true;
    }
    private List<String> tab(CommandSender s,Command c,String a,String[] args){return args.length==1?List.of("10","25","50"):List.of();}
    @Override public void onDisable(){if(logs!=null)getServer().getServicesManager().unregister(LogService.class,logs);}
}