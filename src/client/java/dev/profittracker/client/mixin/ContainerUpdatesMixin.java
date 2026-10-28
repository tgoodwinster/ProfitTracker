package dev.profittracker.client.mixin;

import dev.profittracker.client.BookCombinerController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ContainerUpdatesMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void tu$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        BookCombinerController.serverSlot(packet);
    }
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void tu$contents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        BookCombinerController.serverContents(packet);
    }
    @Inject(method = "handleSetPlayerInventory", at = @At("TAIL"))
    private void tu$inventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        BookCombinerController.serverInventory(packet.slot(), packet.contents());
    }
    @Inject(method = "handleSetCursorItem", at = @At("TAIL"))
    private void tu$cursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        BookCombinerController.serverCursor(packet.contents());
    }
}
