package com.example.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(LecternBlockEntity.class)
public class LecternBlockEntityMixin extends BlockEntity {
    public LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(at = @At("HEAD"), method = "setBook(Lnet/minecraft/item/ItemStack;)V")
    private void injectSetBook(ItemStack book, CallbackInfo info) {
        VillagerEntity villagerEntity = getVillagerWhoClaimedJobSite();
        String targetBook = getTargetBook(book);

        if (villagerEntity != null) {
            villagerEntity.getOffers().forEach(tradeOffer -> {
                        ItemEnchantmentsComponent storedEnchant = tradeOffer.getSellItem().getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, null);
                        if (storedEnchant != null) {
                            storedEnchant.getEnchantments().forEach(enchantmentRegistryEntry ->
                                    {

                                        enchantmentRegistryEntry.getKey().ifPresent(enchantmentRegistryKey ->
                                                {
                                                    //TODO
                                                    // - MAKE A WAY TO GET THE ENCHANTMENT TOOLTIP ;3
                                                    System.out.println(Enchantment.getName(enchantmentRegistryEntry, storedEnchant.getLevel(enchantmentRegistryEntry)));
                                                }
                                        );
                                    }
                            );
                            ;
                        }
//
                    }
            );
        }
    }

    @Nullable
    @Unique
    private VillagerEntity getVillagerWhoClaimedJobSite() {
        if (this.world != null) {
            List<VillagerEntity> villagerEntityList = this.world.getEntitiesByClass(VillagerEntity.class, new Box(this.pos).expand(64), this::isVillagerLecternOwner);
//            return ;
            if (!villagerEntityList.isEmpty()) {
                return villagerEntityList.getFirst();
            }
        }
        return null;
    }

    @Unique
    private boolean isVillagerLecternOwner(VillagerEntity villagerEntity) {
        Optional<GlobalPos> claimedLecternOfTheVillager = villagerEntity.getBrain().getOptionalMemory(MemoryModuleType.JOB_SITE);
        if (claimedLecternOfTheVillager != null && claimedLecternOfTheVillager.isPresent()) {
            return claimedLecternOfTheVillager.get().pos().compareTo(this.pos) == 0;
        }
        return false;
    }

    @Unique
    String getTargetBook(ItemStack book) {
        final WrittenBookContentComponent writtenBookContentComponent = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (writtenBookContentComponent != null) {
            String content = writtenBookContentComponent.getPages(false).getFirst().getLiteralString();

            if (content != null && !content.isBlank()) {
                String[] splitContent = content.split("\n");
                return splitContent[0];
            }
        }
        return "";
    }
}
