package dev.profittracker.client;

import dev.profittracker.BookCombiner;
import dev.profittracker.ChatTitleRule;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Reads book metadata, never guesses tiers from names or lore. */
final class BookItems {
    static boolean isBook(ItemStack stack) { return stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK); }

    static BookCombiner.Book book(ItemStack stack) {
        if (!isBook(stack)) return null;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom != null) {
            var attributes = custom.copyTag().getCompoundOrEmpty("ExtraAttributes");
            if (attributes.contains("enchantments")) {
                var enchantments = attributes.getCompoundOrEmpty("enchantments");
                if (enchantments.size() != 1) return null;
                String name = enchantments.keySet().iterator().next();
                int level = enchantments.getIntOr(name, 0);
                return level > 0 && level <= 10 ? new BookCombiner.Book("skyblock:" + name, level) : null;
            }
        }
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.size() != 1) return null;
        var entry = stored.entrySet().iterator().next();
        var key = entry.getKey().unwrapKey();
        int level = entry.getIntValue();
        return key.isPresent() && level > 0 && level <= 10
                ? new BookCombiner.Book(key.get().identifier().toString(), level) : null;
    }

    static BookCombiner.Cell cell(ItemStack stack) {
        if (stack.isEmpty()) return BookCombiner.Cell.EMPTY;
        var book = book(stack);
        // Idle menu decorations are learned by material, name, and lore, not just by emptiness.
        String marker = "";
        if (book == null) {
            var text = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    .append('\n').append(ChatTitleRule.clean(stack.getHoverName().getString()));
            for (var line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines())
                text.append('\n').append(ChatTitleRule.clean(line.getString()));
            marker = text.toString();
        }
        return new BookCombiner.Cell(book, stack.getCount(), marker);
    }
}
