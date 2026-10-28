package dev.profittracker.client.mixin;

import dev.profittracker.client.ProfitTrackerClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
abstract class LocalChatMixin {
    @Inject(method = {"addClientSystemMessage", "addServerSystemMessage"}, at = @At("HEAD"))
    private void tu$localNotification(Component message, CallbackInfo ci) {
        ProfitTrackerClient.systemGemNotification(message);
    }
}
