package net.amunak.bukkit.flywithfood;

/**
 * Copyright 2013 Jiří Barouš
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Amunak
 */
public final class FlyWithFoodCommandExecutor implements CommandExecutor {

    protected static FlyWithFood plugin;

    public FlyWithFoodCommandExecutor(FlyWithFood p) {
        plugin = p;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player pl = sender instanceof Player ? plugin.getServer().getPlayer(sender.getName()) : null;
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].toLowerCase();
        }

        //Handle switching fly on and off
        if (args.length == 1 && plugin.flyControlCommands.contains(args[0])) {
            if (sender instanceof Player) {
                if (!sender.hasPermission("fly.fly")) {
                    sender.sendMessage(ChatColor.RED + "You have no permission to do this.");
                    return true;
                }
                if (args[0].equalsIgnoreCase("toggle")) {
                    plugin.setFlightPreference(pl);
                } else if (args[0].equalsIgnoreCase("on")) {
                    plugin.setFlightPreference(pl, true);
                } else if (args[0].equalsIgnoreCase("off")) {
                    plugin.setFlightPreference(pl, false);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Consoles cannot fly.");
            }
        } else if (args.length == 2 && plugin.flyControlCommands.contains(args[0])) {
            Player otherPlayer = plugin.getServer().getPlayer(args[1]);
            if (sender.hasPermission("fly.other")) {
                if (otherPlayer == null) {
                    sender.sendMessage(ChatColor.YELLOW + "Player not found.");
                } else {
                    if (args[0].equalsIgnoreCase("on")) {
                        plugin.setFlightPreference(otherPlayer, true);
                        sender.sendMessage(otherPlayer.getDisplayName() + ChatColor.YELLOW + " now has the ability to fly.");
                    } else if (args[0].equalsIgnoreCase("off")) {
                        plugin.setFlightPreference(otherPlayer, false);
                        sender.sendMessage(otherPlayer.getDisplayName() + ChatColor.YELLOW + " can no longer fly.");
                    }
                }
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "You have no permission to do this.");
                return true;
            }
        }

        //Command configuration
        if (args.length > 0 && args[0].equals("config")) {
            if (sender.hasPermission("fly.configure")) {
                if (args[1].equals("reload")) {
                    sender.sendMessage(ChatColor.YELLOW + "Reloading plugin configuration");
                    plugin.reloadConfig();
                    return true;
                }
            } else {
                sender.sendMessage(ChatColor.RED + "You have no permission to do this.");
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Invalid configuration command.");
            return true;
        }

        //Display help
        if ((args.length == 0) || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            sender.sendMessage("Use " + ChatColor.GOLD + "/help " + plugin.pdfFile.getName());
            return true;
        }

        return false;

    }
}
