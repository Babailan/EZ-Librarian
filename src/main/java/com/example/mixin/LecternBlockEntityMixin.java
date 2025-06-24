package com.example.mixin;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(LecternBlockEntity.class)
public class LecternBlockEntityMixin extends BlockEntity {

    public LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(at = @At("HEAD"), method = "setBook(Lnet/minecraft/item/ItemStack;)V")
    private void injectSetBook(ItemStack book, CallbackInfo info) {
        VillagerEntity villagerEntity = getVillagerWhoClaimedJobSite();
        ItemStack targetBook = getTargetBook(book);
        if (villagerEntity == null || targetBook == null) {
            return;
        }


        TradeOfferList currentTradeOfferList = villagerEntity.getOffers();
        TradeOfferList modifiedTradeOffer = new TradeOfferList();
        for (int i = 0; i < currentTradeOfferList.size(); i++) {
            TradeOffer tradeOffer = currentTradeOfferList.get(i);
            TradeOffer newOffer = new TradeOffer(tradeOffer.getFirstBuyItem(), tradeOffer.getSecondBuyItem(), targetBook, 10, tradeOffer.getMerchantExperience(), tradeOffer.getPriceMultiplier());
            modifiedTradeOffer.add(newOffer);
        }
        villagerEntity.setOffers(modifiedTradeOffer);
    }

    @Nullable
    @Unique
    private VillagerEntity getVillagerWhoClaimedJobSite() {
        if (this.world != null) {
            List<VillagerEntity> villagerEntityList = this.world.getEntitiesByClass(VillagerEntity.class, new Box(this.pos).expand(64), this::isVillagerLecternOwner);
            if (!villagerEntityList.isEmpty()) {
                return villagerEntityList.getFirst();
            }
        }
        return null;
    }

    /**
     * @apiNote check whether if the villager is the one who claimed the lectern.
     */
    @Unique
    private boolean isVillagerLecternOwner(VillagerEntity villagerEntity) {
        Optional<GlobalPos> claimedLecternOfTheVillager = villagerEntity.getBrain().getOptionalMemory(MemoryModuleType.JOB_SITE);
        if (claimedLecternOfTheVillager != null && claimedLecternOfTheVillager.isPresent()) {
            return claimedLecternOfTheVillager.get().pos().compareTo(this.pos) == 0;
        }
        return false;
    }


    /**
     * @param book The ItemStack of the book that was put.
     * @return The item stack that can be use to make a new Offer in the TradeOffer of the villager.
     */
    @Unique
    private ItemStack getTargetBook(ItemStack book) {
        final WrittenBookContentComponent bookContent = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        AtomicReference<Enchantment> targetEnchantment = new AtomicReference<>(null);

        if (bookContent != null) {
            String pageContent = bookContent.getPages(false).getFirst().getLiteralString();

            if (pageContent != null && !pageContent.isBlank()) {
                String[] lines = pageContent.split("\n");
                // Just the first lines. Other lines won't matter.
                String[] words = lines[0].toUpperCase().trim().split(" ");
                int targerLevel = romanToInt(words[words.length - 1]);
                String enchantmentName = String.join("_", Arrays.copyOfRange(words, 0, words.length - 1));
                try {
                    Field enchantmentField = Enchantments.class.getField(enchantmentName);

                    @SuppressWarnings("unchecked") // I supress this because im going to check if it's the instance of RegistryKey<Enchantment> later so it won't matter.
                    RegistryKey<Enchantment> enchantment = (RegistryKey<Enchantment>) enchantmentField.get(null); // static field access
                    if (enchantment instanceof RegistryKey<Enchantment>) {
                        if (world != null) {
                            world.getRegistryManager().getOptionalEntry(enchantment).ifPresent(enchantmentReference -> {
                                targetEnchantment.set(enchantmentReference.value());
                            });
                        }
                    }
                    if (targetEnchantment.get() == null) {
                        return null;
                    }

                    ItemEnchantmentsComponent.Builder enchantmentsBuilder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                    enchantmentsBuilder.add(RegistryEntry.of(targetEnchantment.get()), targerLevel); // You can replace `1` with the parsed level

                    ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
                    enchantedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, enchantmentsBuilder.build());
                    return enchantedBook;
                } catch (NoSuchFieldException e) {
                    System.out.println("No such enchantment found.");
                } catch (IllegalAccessException e) {
                    System.out.println("Illegal access to enchantment field.");
                }
            }
        }
        return null;
    }


    @Unique
    private int romanToInt(String stringLevel) {
        return switch (stringLevel) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            default -> 5;
        };
    }

}

