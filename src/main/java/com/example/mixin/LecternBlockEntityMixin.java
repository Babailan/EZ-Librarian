package com.example.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Mixin to extend {@link LecternBlockEntity} behavior.
 * When a written book with enchantment data is placed, it generates a custom trade
 * for the linked villager if conditions are met.
 */
@Mixin(LecternBlockEntity.class)
public class LecternBlockEntityMixin extends BlockEntity {

    public LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Injects into Lectern's setBook to intercept when a book is placed.
     * If a nearby villager owns the lectern and is untraded, it generates an enchanted book trade
     * based on the book's first page content.
     *
     * @param book the ItemStack placed on the lectern
     * @param info callback info for the injection
     */
    @Inject(at = @At("HEAD"), method = "setBook(Lnet/minecraft/item/ItemStack;)V")
    private void injectSetBook(ItemStack book, CallbackInfo info) {
        if (world instanceof ServerWorld) {
            VillagerEntity villagerEntity = getVillagerWhoClaimedJobSite();
            if (villagerEntity == null) {
                return;
            }
            // prevent injection if villager got experience already.
            if (villagerEntity.getExperience() != 0) {
                return;
            }
            TradeOffer targetBook = getTargetBook(book, villagerEntity);
            if (targetBook == null) {
                return;
            }

            TradeOfferList currentTradeOfferList = villagerEntity.getOffers();
            TradeOfferList modifiedTradeOffer = new TradeOfferList();


            boolean foundEnchantedBook = false;
            // this will always be 2 since it's a novice.
            for (int i = 0; i < 2; i++) {
                TradeOffer offer = currentTradeOfferList.get(i);
                ItemStack sellItem = offer.getSellItem();
                boolean isEnchantedBook = Registries.ITEM.getId(sellItem.getItem()).getPath().equals("enchanted_book");

                if (isEnchantedBook && !foundEnchantedBook) {
                    modifiedTradeOffer.add(targetBook);
                    foundEnchantedBook = true;
                } else if (i == 1 && !foundEnchantedBook) {
                    // If this is the last one and no enchanted book was found yet, insert targetBook
                    modifiedTradeOffer.add(targetBook);
                } else {
                    modifiedTradeOffer.add(offer);
                }
            }


            villagerEntity.setOffers(modifiedTradeOffer);
            // Lock villager trade offer so memory module ai thinks it got job already even tho -1 it still matter.
            villagerEntity.setExperience(-1);
            villagerEntity.getWorld().getChunk(villagerEntity.getBlockPos()).markNeedsSaving();
            villagerEntity.playSound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE);

        }
    }

    /**
     * Finds the villager that claimed this lectern as a job site.
     *
     * @return the matching VillagerEntity, or null if not found
     */
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
     * Checks if the given villager owns this lectern as a job site.
     *
     * @param villagerEntity the villager to check
     * @return true if their job site matches this lectern position
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
     * Parses the written book and creates a trade offer for an enchanted book
     * based on the enchantment specified on the first page.
     *
     * @param book           the written book ItemStack
     * @param villagerEntity the target villager
     * @return a TradeOffer with a custom enchanted book, or null if invalid
     */
    @Unique
    private TradeOffer getTargetBook(ItemStack book, VillagerEntity villagerEntity) {
        WrittenBookContentComponent bookContent = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (bookContent == null || bookContent.getPages(false).isEmpty()) {
            return null;
        }

        String firstPage = bookContent.getPages(false).getFirst().getLiteralString();
        if (firstPage == null || firstPage.isBlank()) {
            return null;
        }

        // Parse the enchantment name and level from the first line
        String[] lines = firstPage.split("\n");
        String[] words = lines[0].toLowerCase().trim().split(" ");

        String levelString = words[words.length - 1];
        String enchantmentId = String.join("_", Arrays.copyOfRange(words, 0, Math.max(1, words.length - 1)));
        int level = romanToInt(levelString);
        RegistryKey<Enchantment> enchantmentKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla(enchantmentId));

        if (world == null) {
            return null;
        }

        Optional<RegistryEntry.Reference<Enchantment>> enchantmentEntry = world.getRegistryManager().getOptionalEntry(enchantmentKey);
        if (enchantmentEntry.isEmpty()) {
            return null;
        }

        RegistryEntry.Reference<Enchantment> enchantment = enchantmentEntry.get();
        int safeLevel = Math.min(level, enchantment.value().getMaxLevel());

        ItemEnchantmentsComponent.Builder enchantBuilder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        enchantBuilder.set(enchantment, safeLevel);

        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, enchantBuilder.build());

        TradeOffer generatedTradeOffer = new TradeOffers.EnchantBookFactory(5, EnchantmentTags.TRADEABLE).create(villagerEntity, world.random);
        assert generatedTradeOffer != null;

        //make a random price range(32,64);
        int min = 32;
        int max = 64;
        int randomPrice = min + (int) (Math.random() * (max - min + 1));
        TradedItem emerald = new TradedItem(Items.EMERALD, randomPrice);
        return new TradeOffer(emerald, generatedTradeOffer.getSecondBuyItem(), enchantedBook, generatedTradeOffer.getMaxUses(), generatedTradeOffer.getMerchantExperience(), generatedTradeOffer.getPriceMultiplier());
    }

    /**
     * Converts a Roman numeral string to its corresponding integer value.
     * Supports common enchantment levels: I to V.
     *
     * @param stringLevel the Roman numeral (e.g. "iii", "iv")
     * @return the numeric level, defaulting to 1 if unrecognized
     */
    @Unique
    private int romanToInt(String stringLevel) {
        return switch (stringLevel) {
            case "ii" -> 2;
            case "iii" -> 3;
            case "iv" -> 4;
            case "v" -> 5;
            default -> 1;
        };
    }
}
