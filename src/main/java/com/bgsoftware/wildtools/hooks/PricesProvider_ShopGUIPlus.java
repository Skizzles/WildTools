package com.bgsoftware.wildtools.hooks;

import com.bgsoftware.wildtools.WildToolsPlugin;
import com.bgsoftware.wildtools.api.hooks.PricesProvider;
import com.bgsoftware.wildtools.utils.Pair;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopItem;
import net.brcdev.shopgui.shop.ShopManager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class PricesProvider_ShopGUIPlus implements PricesProvider {

    // Added cache for shop items for better performance
    private final Map<WrappedItemStack, Pair<ShopItem, Shop>> cachedShopItems = new HashMap<>();
    private final ShopGuiPlugin plugin;

    public PricesProvider_ShopGUIPlus(){
        WildToolsPlugin.log(" - Using ShopGUIPlus as PricesProvider.");
        plugin = ShopGuiPlugin.getInstance();
    }

    @Override
    public double getPrice(Player player, ItemStack itemStack) {
        double price = 0;

        WrappedItemStack wrappedItemStack = new WrappedItemStack(itemStack);
        Pair<ShopItem, Shop> shopPair = cachedShopItems.computeIfAbsent(wrappedItemStack, i -> {
            Map<String, Shop> shops = plugin.getShopManager().shops;
            for (Shop shop : shops.values()) {
                for (ShopItem _shopItem : shop.getShopItems()) {
                    if (_shopItem.getType() == ShopManager.ItemType.ITEM &&
                            areSimilar(_shopItem.getItem(), itemStack, _shopItem.isCompareMeta()))
                        return new Pair<>(_shopItem, shop);
                }
            }

            return null;
        });

        if(shopPair != null){
            if(player == null) {
                price = Math.max(price, shopPair.getX().getSellPriceForAmount(itemStack.getAmount()));
            }
            else{
                price = Math.max(price, shopPair.getX().getSellPriceForAmount(player, itemStack.getAmount()));
            }
        }

        return price;
    }

    private static boolean areSimilar(ItemStack is1, ItemStack is2, boolean compareMetadata){
        return compareMetadata ? is1.isSimilar(is2) : is2 != null && is1 != null && is1.getType() == is2.getType() &&
                is1.getDurability() == is2.getDurability();
    }

    private static final class WrappedItemStack{

        private final ItemStack value;

        WrappedItemStack(ItemStack value){
            this.value = value.clone();
            this.value.setAmount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WrappedItemStack that = (WrappedItemStack) o;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

}
