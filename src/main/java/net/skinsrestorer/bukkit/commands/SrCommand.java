/*
 * #%L
 * SkinsRestorer
 * %%
 * Copyright (C) 2021 SkinsRestorer
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package net.skinsrestorer.bukkit.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.skinsrestorer.bukkit.SkinsRestorer;
import net.skinsrestorer.shared.storage.Config;
import net.skinsrestorer.shared.storage.Locale;
import net.skinsrestorer.shared.utils.ReflectionUtil;
import net.skinsrestorer.shared.utils.ServiceChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

@CommandAlias("sr|skinsrestorer")
@CommandPermission("%sr")
public class SrCommand extends BaseCommand {
    private final SkinsRestorer plugin;

    public SrCommand(SkinsRestorer plugin) {
        this.plugin = plugin;
    }

    @HelpCommand
    @Syntax(" [help]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("reload")
    @CommandPermission("%srReload")
    @Description("%helpSrReload")
    public void onReload(CommandSender sender) {
        Locale.load(SkinsRestorer.getInstance().getConfigPath());
        Config.load(SkinsRestorer.getInstance().getConfigPath(), SkinsRestorer.getInstance().getResource("config.yml"));

        sender.sendMessage(Locale.RELOAD);
    }

    @Subcommand("status")
    @CommandPermission("%srStatus")
    @Description("%helpSrStatus")
    public void onStatus(CommandSender sender) {
        sender.sendMessage("§3----------------------------------------------");
        sender.sendMessage("§7Checking needed services for SR to work properly...");

        Bukkit.getScheduler().runTaskAsynchronously(SkinsRestorer.getInstance(), () -> {
            ServiceChecker checker = new ServiceChecker();
            checker.setMojangAPI(plugin.getMojangAPI());
            checker.checkServices();

            ServiceChecker.ServiceCheckResponse response = checker.getResponse();
            List<String> results = response.getResults();

            for (String result : results) {
                sender.sendMessage(result);
            }
            sender.sendMessage("§7Working UUID API count: §6" + response.getWorkingUUID());
            sender.sendMessage("§7Working Profile API count: §6" + response.getWorkingProfile());
            if (response.getWorkingUUID() >= 1 && response.getWorkingProfile() >= 1)
                sender.sendMessage("§aThe plugin currently is in a working state.");
            else
                sender.sendMessage("§cPlugin currently can't fetch new skins. You might check out our discord at https://discord.me/servers/skinsrestorer");
            sender.sendMessage("§3----------------------------------------------");
            sender.sendMessage("§7SkinsRestorer §6v" + plugin.getVersion());
            sender.sendMessage("§7Server: §6" + plugin.getServer().getVersion());
            sender.sendMessage("§7BungeeMode: §6" + plugin.isBungeeEnabled());
            sender.sendMessage("§7Finished checking services.");
            sender.sendMessage("§3----------------------------------------------");
        });
    }

    @Subcommand("drop|remove")
    @CommandPermission("%srDrop")
    @CommandCompletion("player|skin @players")
    @Description("%helpSrDrop")
    @Syntax(" <player|skin> <target> [target2]")
    public void onDrop(CommandSender sender, PlayerOrSkin e, String[] targets) {
        if (e.name().equalsIgnoreCase("player"))
            for (String targetPlayer : targets)
                plugin.getSkinStorage().removePlayerSkin(targetPlayer);
        else
            for (String targetSkin : targets)
                plugin.getSkinStorage().removeSkinData(targetSkin);

        String targetList = Arrays.toString(targets).substring(1, Arrays.toString(targets).length() - 1);
        sender.sendMessage(Locale.DATA_DROPPED.replace("%playerOrSkin", e.name()).replace("%targets", targetList));
    }


    @Subcommand("props")
    @CommandPermission("%srProps")
    @CommandCompletion("@players")
    @Description("%helpSrProps")
    @Syntax(" <target>")
    public void onProps(CommandSender sender, OnlinePlayer target) {
        try {
            Object ep = ReflectionUtil.invokeMethod(target.getPlayer(), "getHandle");
            Object profile = ReflectionUtil.invokeMethod(ep, "getProfile");
            Object propMap = ReflectionUtil.invokeMethod(profile, "getProperties");

            Collection<?> props = (Collection<?>) ReflectionUtil.invokeMethod(propMap.getClass(), propMap, "get",
                    new Class[]{Object.class}, "textures");

            if (props == null || props.isEmpty()) {
                sender.sendMessage(Locale.NO_SKIN_DATA);
                return;
            }

            for (Object prop : props) {
                String name = (String) ReflectionUtil.invokeMethod(prop, "getName");
                String value = (String) ReflectionUtil.invokeMethod(prop, "getValue");
                String signature = (String) ReflectionUtil.invokeMethod(prop, "getSignature");

                byte[] decoded = Base64.getDecoder().decode(value);

                String decodedString = new String(decoded);
                JsonObject jsonObject = JsonParser.parseString(decodedString).getAsJsonObject();
                String decodedSkin = jsonObject.getAsJsonObject().get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").toString();
                long timestamp = Long.parseLong(jsonObject.getAsJsonObject().get("timestamp").toString());
                String requestDate = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(timestamp));

                ConsoleCommandSender console = Bukkit.getConsoleSender();

                sender.sendMessage("§aRequest time: §e" + requestDate);
                sender.sendMessage("§aProfileId: §e" + jsonObject.getAsJsonObject().get("profileId").toString());
                sender.sendMessage("§aName: §e" + jsonObject.getAsJsonObject().get("profileName").toString());
                sender.sendMessage("§aSkinTexture: §e" + decodedSkin.substring(1, decodedSkin.length() - 1));
                sender.sendMessage("§cMore info in console!");

                //console
                console.sendMessage("\n§aName: §8" + name);
                console.sendMessage("\n§aValue : §8" + value);
                console.sendMessage("\n§aSignature : §8" + signature);
                console.sendMessage("\n§aValue Decoded: §e" + Arrays.toString(decoded));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(Locale.NO_SKIN_DATA);
        }
    }

    public enum PlayerOrSkin {
        PLAYER,
        SKIN,
    }
}
