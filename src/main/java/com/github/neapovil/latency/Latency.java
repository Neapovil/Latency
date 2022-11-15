package com.github.neapovil.latency;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.electronwill.nightconfig.core.file.FileConfig;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelector;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;

public final class Latency extends JavaPlugin implements Listener
{
    private static Latency instance;
    private final Map<UUID, Long> latencies = new HashMap<>();
    private FileConfig messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable()
    {
        instance = this;

        this.saveResource("messages.json", false);

        this.messages = FileConfig.builder(this.getDataFolder().toPath().resolve("messages.json"))
                .autoreload()
                .autosave()
                .build();

        this.messages.load();

        this.getServer().getPluginManager().registerEvents(this, this);

        new CommandAPICommand("latency")
                .withPermission("latency.command.self")
                .executesPlayer((player, args) -> {
                    final long ms = this.latencies.getOrDefault(player.getUniqueId(), 0L);
                    final String message = this.messages.get("messages.self");
                    final Component component = this.miniMessage.deserialize(message, Placeholder.parsed("ms", "" + ms));

                    player.sendMessage(component);
                })
                .register();

        new CommandAPICommand("latency")
                .withPermission("latency.command.self")
                .withArguments(new EntitySelectorArgument<Player>("player", EntitySelector.ONE_PLAYER).withPermission("latency.command.other"))
                .executes((sender, args) -> {
                    final Player target = (Player) args[0];

                    final String message = this.messages.get("messages.other");
                    final long ms = this.latencies.getOrDefault(target.getUniqueId(), 0L);
                    final Component component = this.miniMessage.deserialize(message, Placeholder.parsed("player_name", target.getName()),
                            Placeholder.parsed("ms", "" + ms));

                    sender.sendMessage(component);
                })
                .register();
    }

    @Override
    public void onDisable()
    {
    }

    public static Latency getInstance()
    {
        return instance;
    }

    public long getPlayerLatency(UUID uuid)
    {
        return this.latencies.getOrDefault(uuid, 0L);
    }

    @EventHandler
    private void playerJoin(PlayerJoinEvent event)
    {
        ((CraftPlayer) event.getPlayer()).getHandle().networkManager.channel.pipeline()
                .addBefore("packet_handler", "latency-plugin", new CustomHandler(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    private void playerQuit(PlayerQuitEvent event)
    {
        this.latencies.remove(event.getPlayer().getUniqueId());
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
