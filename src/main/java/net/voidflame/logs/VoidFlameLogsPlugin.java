package net.voidflame.logs;

import net.voidflame.core.storage.StorageService;
import net.voidflame.core.api.AuditLogService;
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
        PluginCommand c=getCommand("vflogs"); if(c!=null){c.setExecutor(this::command);c.setTabCompleter(this::tab);}
        getLogger().info("VoidFlame-Logs enabled.");
    }

    private static String safe(String s){return s==null?"":s.replace("\\","/").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    private static String json(String s){return "\"" + safe(s) + "\"";}
    private static String format(Map<String,Object> row){return "["+row.get("timestamp")+"] "+row.get("actor")+" "+row.get("action")+" -> "+row.get("target")+" | "+row.get("metadata_json");}

    @EventHandler public void join(PlayerJoinEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"JOIN",e.getPlayer().getName(),"firstJoin="+e.getPlayer().hasPlayedBefore());}
    @EventHandler public void quit(PlayerQuitEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"QUIT",e.getPlayer().getName(),"");}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){logs.log(e.getPlayer().getUniqueId().toString(),"COMMAND",e.getPlayer().getName(),e.getMessage());}
    @EventHandler public void kick(PlayerKickEvent e){logs.log("SYSTEM","KICK",e.getPlayer().getName(),"reason="+e.getReason());}
    @EventHandler public void death(PlayerDeathEvent e){logs.log(e.getEntity().getUniqueId().toString(),"DEATH",e.getEntity().getName(),e.getDeathMessage()==null?"":e.getDeathMessage());}

    private boolean command(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("voidflame.logs.view")){sender.sendMessage("§cNo permission.");return true;}
        int page=1;
        String action="";
        String actor="";
        if(args.length>0) try{page=Math.max(1,Integer.parseInt(args[0]));}catch(NumberFormatException ignored){action=args[0];}
        if(args.length>1) action=args[1];
        if(args.length>2) actor=args[2];
        int limit=10;
        int offset=(page-1)*limit;
        StringBuilder sql=new StringBuilder("SELECT actor,action,target,timestamp,metadata_json FROM audit_logs WHERE 1=1");
        List<Object> params=new ArrayList<>();
        if(!action.isBlank()){sql.append(" AND action LIKE ?");params.add("%"+action+"%");}
        if(!actor.isBlank()){sql.append(" AND actor LIKE ?");params.add("%"+actor+"%");}
        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit); params.add(offset);
        final int requestedPage = page;
        final String requestedAction = action;
        final String requestedActor = actor;
        storage.database().query(sql.toString(), params.toArray())
            .thenAccept(rows -> Bukkit.getScheduler().runTask(this,()->{
                sender.sendMessage("§8§m----------------");
                sender.sendMessage("§bVoidFlame Logs §7Page "+requestedPage+" §8| §faction="+(requestedAction.isBlank()?"*":requestedAction)+" §8| §factor="+(requestedActor.isBlank()?"*":requestedActor));
                if(rows.isEmpty()) sender.sendMessage("§7No logs matched.");
                for(var row:rows) sender.sendMessage("§7• §f"+format(row));
                sender.sendMessage("§8§m----------------");
            })).exceptionally(err->{sender.sendMessage("§cCould not read logs.");return null;});
        return true;
    }
    private List<String> tab(CommandSender s,Command c,String a,String[] args){
        if(args.length==1)return List.of("1","2","3","10","25","50");
        return List.of();
    }
    @Override public void onDisable(){
        if(logs!=null)getServer().getServicesManager().unregister(LogService.class,logs);
        if(logs!=null)getServer().getServicesManager().unregister(AuditLogService.class,logs);
    }
}