package com.github.neapovil.latency;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.PacketListenerAPI;
import org.inventivetalent.packetlistener.handler.PacketHandler;
import org.inventivetalent.packetlistener.handler.ReceivedPacket;
import org.inventivetalent.packetlistener.handler.SentPacket;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;

public final class Latency extends JavaPlugin
{
    private static Latency instance;
    private final Map<UUID, Instant> calculating = new HashMap<>();
    private final Map<UUID, Long> latencies = new HashMap<>();

    @Override
    public void onEnable()
    {
        instance = this;

        PacketListenerAPI.addPacketHandler(new PacketHandler(this) {
            @Override
            public void onSend(SentPacket packet)
            {
                if (!packet.getPacketName().equals("PacketPlayOutKeepAlive"))
                {
                    return;
                }

                if (!packet.hasPlayer())
                {
                    return;
                }

                calculating.put(packet.getPlayer().getUniqueId(), Instant.now());
            }

            @Override
            public void onReceive(ReceivedPacket packet)
            {
                if (!packet.getPacketName().equals("PacketPlayInKeepAlive"))
                {
                    return;
                }

                if (!packet.hasPlayer())
                {
                    return;
                }

                final Instant now = Instant.now();
                final Instant before = calculating.remove(packet.getPlayer().getUniqueId());

                latencies.put(packet.getPlayer().getUniqueId(), Duration.between(before, now).toMillis());
            }
        });

        new CommandAPICommand("latency")
                .withPermission("latency.command.self")
                .executesPlayer((player, args) -> {
                    player.sendMessage("Ping: " + latencies.getOrDefault(player.getUniqueId(), 0L) + "ms");
                })
                .register();

        new CommandAPICommand("latency")
                .withArguments(new EntitySelectorArgument("player", EntitySelectorArgument.EntitySelector.ONE_PLAYER).withPermission("latency.command.other"))
                .executes((sender, args) -> {
                    final Player target = (Player) args[0];

                    sender.sendMessage(target.getName() + "'s ping: " + latencies.getOrDefault(target.getUniqueId(), 0L) + "ms");
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

    public Map<UUID, Long> getLatencies()
    {
        return this.latencies;
    }
}
