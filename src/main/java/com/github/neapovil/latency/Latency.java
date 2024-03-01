package com.github.neapovil.latency;

import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;

public final class Latency extends JavaPlugin
{
    private static Latency instance;

    @Override
    public void onEnable()
    {
        instance = this;

        new CommandAPICommand("latency")
                .withPermission("latency.command.self")
                .executesPlayer((player, args) -> {
                    final int ping = ((CraftPlayer) player).getPing();
                    player.sendMessage("Ping: %sms".formatted(ping));
                })
                .register();

        new CommandAPICommand("latency")
                .withPermission("latency.command.other")
                .withArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executes((sender, args) -> {
                    final Player player = (Player) args.get("player");
                    final int ping = ((CraftPlayer) player).getPing();
                    sender.sendMessage("%s's ping: %sms".formatted(player.getName(), ping));
                })
                .register();
    }

    @Override
    public void onDisable()
    {
    }

    public static Latency instance()
    {
        return instance;
    }
}
