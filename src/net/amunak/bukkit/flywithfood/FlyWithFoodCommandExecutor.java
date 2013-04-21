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
            ChatColor m = ChatColor.YELLOW;
            ChatColor c = ChatColor.GREEN;
            ChatColor p = ChatColor.AQUA;
            String s = "  ";
            sender.sendMessage(ChatColor.GOLD + this.plugin.pdfFile.getFullName());
            sender.sendMessage(s + m + "You can use " + c + "/fly" + m + ", " + c + "/fwf" + m + " or " + c + "/FlyWithFood" + m + " as base command");
            sender.sendMessage(s + "Available commands:");
            if (sender.hasPermission("fly.fly")) {
                sender.sendMessage(s + s + c + "/fly " + p + "<on|off|toggle>" + m + " - turn fly on, off or toggle it for yourself");
            }
            if (sender.hasPermission("fly.other")) {
                sender.sendMessage(s + s + c + "/fly " + p + "<on|off> (PLAYER)" + m + " - turn fly on or off for " + p + "player");
            }
            if (sender.hasPermission("fly.config")) {
                sender.sendMessage(s + s + c + "/fly config reload - reload plugin's configuration");
                sender.sendMessage(s + "Permission nodes:");
                sender.sendMessage(s + s + c + "fly.*" + m + " all permissions except " + c + "fly.force");
                sender.sendMessage(s + s + c + "fly.fly" + m + " allows " + c + "/fly " + p + "<on|off|toggle>");
                sender.sendMessage(s + s + c + "fly.other" + m + " allows " + c + "/fly " + p + "<on|off|toggle>" + m + " and " + c + "/fly " + p + "<on|off> (PLAYER)");
                sender.sendMessage(s + s + c + "fly.nohunger" + m + " player bypasses hunger drain and checks");
                sender.sendMessage(s + s + c + "fly.eatanything" + m + " player bypasses eating limit and checks");
                sender.sendMessage(s + s + c + "fly.force" + m + " forces player the ability to fly (bypasses the need of fly.fly but other checks apply)");
                sender.sendMessage(s + s + c + "fly.configure" + m + " allows " + c + "/fly config");
            }

            return true;
        }

        return false;

    }
}
