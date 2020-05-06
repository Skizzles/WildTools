package com.bgsoftware.wildtools.objects.tools;

import com.bgsoftware.wildtools.SellWandLogger;
import com.bgsoftware.wildtools.utils.Executor;
import com.bgsoftware.wildtools.utils.NumberUtils;
import com.bgsoftware.wildtools.utils.container.SellInfo;
import com.bgsoftware.wildtools.utils.items.ToolTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.event.player.PlayerInteractEvent;
import com.bgsoftware.wildtools.Locale;
import com.bgsoftware.wildtools.api.events.SellWandUseEvent;
import com.bgsoftware.wildtools.api.objects.tools.SellTool;
import com.bgsoftware.wildtools.api.objects.ToolMode;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public final class WSellTool extends WTool implements SellTool {

    public WSellTool(Material type, String name){
        super(type, name, ToolMode.SELL);
    }

    @Override
    public boolean onBlockInteract(PlayerInteractEvent e) {
        if(!plugin.getProviders().isVaultEnabled()){
            e.getPlayer().sendMessage(ChatColor.RED + "You tried to use a sell-wand, but the server doesn't have Vault installed. " +
                    "Please contact the server administrators if you believe that this is an error.");
            return false;
        }

        if(!plugin.getProviders().canInteract(e.getPlayer(), e.getClickedBlock(), this))
            return false;

        BlockState blockState = e.getClickedBlock().getState();

        if(!plugin.getProviders().isContainer(blockState)){
            Locale.INVALID_CONTAINER_SELL_WAND.send(e.getPlayer());
            return false;
        }

        Chest chest = blockState instanceof Chest ? (Chest) blockState : null;
        UUID taskId = ToolTaskManager.generateTaskId(e.getItem(), e.getPlayer());

        Executor.async(() -> {
            synchronized (getToolMutex(e.getClickedBlock())) {
                try {
                    SellInfo sellInfo = plugin.getProviders().sellContainer(blockState, e.getPlayer());

                    Map<Integer, SoldItem> toSell = sellInfo.getSoldItems();
                    double totalEarnings = sellInfo.getTotalEarnings();
                    double multiplier = getMultiplier();

                    String message = toSell.isEmpty() ? Locale.NO_SELL_ITEMS.getMessage() : Locale.SOLD_CHEST.getMessage();

                    SellWandUseEvent sellWandUseEvent = new SellWandUseEvent(e.getPlayer(), chest, totalEarnings, multiplier, message);
                    Bukkit.getPluginManager().callEvent(sellWandUseEvent);

                    if (sellWandUseEvent.isCancelled())
                        return;

                    multiplier = sellWandUseEvent.getMultiplier();
                    totalEarnings = sellWandUseEvent.getPrice() * multiplier;

                    plugin.getProviders().depositPlayer(e.getPlayer(), totalEarnings);

                    plugin.getProviders().removeContainer(blockState, sellInfo);

                    //noinspection all
                    message = sellWandUseEvent.getMessage().replace("{0}", NumberUtils.format(totalEarnings))
                            .replace("{1}", multiplier != 1 && Locale.MULTIPLIER.getMessage() != null ? Locale.MULTIPLIER.getMessage(multiplier) : "");

                    if (!toSell.isEmpty()) {
                        reduceDurablility(e.getPlayer(), 1, taskId);
                    } else {
                        ToolTaskManager.removeTask(taskId);
                    }

                    for(SoldItem soldItem : toSell.values()){
                        SellWandLogger.log(e.getPlayer().getName() + " sold x" + soldItem.item.getAmount() + " " +
                                soldItem.item.getType() + " for $" + soldItem.price + " (Multiplier: " + multiplier + ")");
                    }

                    if (!message.isEmpty())
                        e.getPlayer().sendMessage(message);
                } finally {
                    removeToolMutex(e.getClickedBlock());
                }
            }
        });

        return true;
    }

    public static final class SoldItem{

        private final ItemStack item;
        private final double price;

        public SoldItem(ItemStack itemStack, double price){
            this.item = itemStack;
            this.price = price;
        }

        public ItemStack getItem() {
            return item;
        }

        public double getPrice() {
            return price;
        }
    }

}
