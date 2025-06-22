package com.example.mixin;

import com.example.UltimateCrop;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

package com.example.mixin;

import com.example.UltimateCrop;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(LecternBlockEntity.class)
public class LecternBlockEntityMixin extends BlockEntity {
    public LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(at = @At("HEAD"), method = "setBook(Lnet/minecraft/item/ItemStack;)V")
    private void injectSetBook(ItemStack book, CallbackInfo info) {
        locateClaimingVillager();
        @Nullable final WrittenBookContentComponent writtenBookContentComponent = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (writtenBookContentComponent != null) {
            String content = writtenBookContentComponent.getPages(false).getFirst().getLiteralString();

            if (!content.isBlank()) {
                String[] splitContent = content.split("\n");
                String targetBookName = splitContent[0];
                System.out.println("not null");
            }
        }
    }

    private void locateClaimingVillager() {
        this.world.getEntitiesByClass(VillagerEntity.class, new Box(this.pos).expand(64), villagerEntity -> {
            isVillagerLecternOwner(villagerEntity);
            return false;
        });
    }

    private void isVillagerLecternOwner(VillagerEntity villagerEntity) {
        Optional<GlobalPos> claimedLecternOfTheVillager = villagerEntity.getBrain().getOptionalMemory(MemoryModuleType.JOB_SITE);
        if (claimedLecternOfTheVillager.isPresent()) {
            System.out.println(claimedLecternOfTheVillager.get().pos().compareTo(this.pos) == 0);
        }
    }
}
