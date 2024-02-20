package com.github.neapovil.latency;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;

public final class Latency extends JavaPlugin implements Listener
{
    private static Latency instance;
    private final Map<UUID, Long> latencies = new HashMap<>();

    @Override
    public void onEnable()
    {
        instance = this;

        this.getServer().getPluginManager().registerEvents(this, this);

        new CommandAPICommand("latency")
                .withPermission("latency.command.self")
                .executesPlayer((player, args) -> {
                    final long ping = this.latencies.getOrDefault(player.getUniqueId(), 0L);
                    player.sendMessage(Component.text("Ping: %sms".formatted(ping)));
                })
                .register();

        new CommandAPICommand("latency")
                .withPermission("latency.command.other")
                .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executes((sender, args) -> {
                    final Player player = (Player) args.get(0);
                    final long ping = this.latencies.getOrDefault(player.getUniqueId(), 0L);
                    sender.sendMessage(Component.text("%s's ping: %sms".formatted(player.getName(), ping)));
                })
                .register();
    }
    
    @Override
    public void onDisable()
    {
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event)
    {
        ((CraftPlayer) event.getPlayer()).getHandle().connection.connection.channel.pipeline()
                .addBefore("packet_handler", "latency-plugin", new CustomHandler(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        this.latencies.remove(event.getPlayer().getUniqueId());
    }

    public static Latency instance()
    {
        return instance;
    }

    class CustomHandler extends ChannelDuplexHandler
    {
        private final UUID uuid;
        private Instant before;

        public CustomHandler(UUID uuid)
        {
            this.uuid = uuid;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            if (msg instanceof ServerboundKeepAlivePacket)
            {
                latencies.put(this.uuid, Duration.between(this.before, Instant.now()).toMillis());
            }

            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
        {
            if (msg instanceof ClientboundKeepAlivePacket)
            {
                this.before = Instant.now();
            }

            super.write(ctx, msg, promise);
        }
    }
}
