package dev.profittracker.client.mixin;

import dev.profittracker.client.ProfitTrackerClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class EndermiteEventsMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void tu$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) { ProfitTrackerClient.endermiteDamage(packet); }
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void tu$death(ClientboundEntityEventPacket packet, CallbackInfo ci) { ProfitTrackerClient.endermiteEvent(packet); }
}
