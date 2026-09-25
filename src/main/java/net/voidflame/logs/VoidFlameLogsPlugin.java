package net.voidflame.logs;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class VoidFlameLogsPlugin extends JavaPlugin implements Listener {
 private Object storage; private Method put,get; private LogService logs;
 public static final class LogService {
  private final VoidFlameLogsPlugin p; LogService(VoidFlameLogsPlugin p){this.p=p;}
  public CompletableFuture<Void> log(String type,String message){return p.put(System.currentTimeMillis()+"-"+java.util.UUID.randomUUID(),type+"|"+message);}
 }
 @Override public void onEnable(){saveDefaultConfig();if(!connectStorage()){getLogger().severe("VoidFlame-Core storage unavailable.");getServer().getPluginManager().disablePlugin(this);return;}logs=new LogService(this);getServer().getServicesManager().register(LogService.class,logs,this,ServicePriority.Normal);getServer().getPluginManager().registerEvents(this,this);getLogger().info("VoidFlame-Logs enabled.");}
 private boolean connectStorage(){try{Class<?> t=Class.forName("net.voidflame.core.storage.StorageService");RegisteredServiceProvider<?> r=getServer().getServicesManager().getRegistration(t);if(r==null)return false;storage=r.getProvider();put=t.getMethod("put",String.class,String.class,String.class);get=t.getMethod("get",String.class,String.class);return true;}catch(ReflectiveOperationException e){return false;}}
 public CompletableFuture<Void> put(String k,String v){try{return (CompletableFuture<Void>)put.invoke(storage,"logs",k,v);}catch(ReflectiveOperationException e){return CompletableFuture.failedFuture(e);}}
 public CompletableFuture<String> get(String k){try{return (CompletableFuture<String>)get.invoke(storage,"logs",k);}catch(ReflectiveOperationException e){return CompletableFuture.failedFuture(e);}}
 @EventHandler public void join(PlayerJoinEvent e){logs.log("JOIN",e.getPlayer().getUniqueId()+"|"+e.getPlayer().getName());}
 @EventHandler public void quit(PlayerQuitEvent e){logs.log("QUIT",e.getPlayer().getUniqueId()+"|"+e.getPlayer().getName());}
 @EventHandler public void command(PlayerCommandPreprocessEvent e){logs.log("COMMAND",e.getPlayer().getUniqueId()+"|"+e.getMessage());}
 @Override public void onDisable(){getServer().getServicesManager().unregister(LogService.class,this);}
}
