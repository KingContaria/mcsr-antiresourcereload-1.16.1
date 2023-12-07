package me.wurgo.antiresourcereload.mixin;

import me.wurgo.antiresourcereload.AntiResourceReload;
import net.minecraft.resource.ServerResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ExecutionException;

@Mixin(ServerResourceManager.class)
public abstract class ServerResourceManagerMixin {
    @Inject(
            method = "close",
            at = @At("HEAD"),
            cancellable = true
    )
    private void keepCachedResourcesOpen(CallbackInfo ci) throws ExecutionException, InterruptedException {
        // noinspection ConstantConditions - I think mixin is confusing for intellij here
        if (AntiResourceReload.cache != null && (Object) this == AntiResourceReload.cache.get()) {
            ci.cancel();
        }
    }
}
