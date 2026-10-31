package com.forma.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every placeable block variant (incl. modded), searchable by name or id. Built lazily on first search. */
public final class BlockCatalog {
    private static List<ItemStack> all;
    private static final List<String> keys = new ArrayList<>();
    private static String lastQuery;
    private static List<ItemStack> lastResult = new ArrayList<>();

    private BlockCatalog() {}

    private static void build() {
        all = new ArrayList<>();
        for (Item item : Item.REGISTRY) {
            if (!(item instanceof ItemBlock)) continue;
            NonNullList<ItemStack> variants = NonNullList.create();
            try {
                item.getSubItems(CreativeTabs.SEARCH, variants);
                if (variants.isEmpty()) variants.add(new ItemStack(item));
                for (ItemStack s : variants) {
                    String key = (s.getDisplayName() + " " + item.getRegistryName()).toLowerCase(Locale.ROOT);
                    all.add(s);
                    keys.add(key);
                }
            } catch (RuntimeException ignored) {
                // A broken modded item shouldn't break the palette.
            }
        }
    }

    public static List<ItemStack> search(String query, int limit) {
        query = query.trim().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) return lastResult;
        if (all == null) build();
        List<ItemStack> out = new ArrayList<>();
        if (!query.isEmpty()) {
            for (int i = 0; i < all.size() && out.size() < limit; i++) {
                if (keys.get(i).contains(query)) out.add(all.get(i));
            }
        }
        lastQuery = query;
        lastResult = out;
        return out;
    }
}
