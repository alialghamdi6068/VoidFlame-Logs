package net.voidflame.logs;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import net.voidflame.core.storage.StorageService;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.CompletableFuture;

public final class VoidFlameLogsPlugin extends JavaPlugin implements Listener {
 private StorageService storage; private LogService logs;
 public static final class LogService {
  private final VoidFlameLogsPlugin p; LogService(VoidFlameLogsPlugin p){this.p=p;}
  public CompletableFuture<Void> log(String type,String message){return p.put(System.currentTimeMillis()+"-"+java.util.UUID.randomUUID(),type+"|"+message);}
 }
 @Override public void onEnable(){saveDefaultConfig();if(!connectStorage()){getLogger().severe("VoidFlame-Core storage unavailable.");getServer().getPluginManager().disablePlugin(this);return;}logs=new LogService(this);getServer().getServicesManager().register(LogService.class,logs,this,ServicePriority.Normal);getServer().getPluginManager().registerEvents(this,this);getLogger().info("VoidFlame-Logs enabled.");}
 private boolean connectStorage(){ var r=getServer().getServicesManager().getRegistration(StorageService.class); if(r==null)return false; storage=r.getProvider(); return storage!=null; }
 public CompletableFuture<Void> put(String k,String v){ return storage.put("logs",k,v); }
 public CompletableFuture<String> get(String k){ return storage.get("logs",k); }
 @EventHandler public void join(PlayerJoinEvent e){logs.log("JOIN",e.getPlayer().getUniqueId()+"|"+e.getPlayer().getName());}
 @EventHandler public void quit(PlayerQuitEvent e){logs.log("QUIT",e.getPlayer().getUniqueId()+"|"+e.getPlayer().getName());}
 @EventHandler public void command(PlayerCommandPreprocessEvent e){logs.log("COMMAND",e.getPlayer().getUniqueId()+"|"+e.getMessage());}
 @Override public void onDisable(){getServer().getServicesManager().unregister(LogService.class,this);}
}
