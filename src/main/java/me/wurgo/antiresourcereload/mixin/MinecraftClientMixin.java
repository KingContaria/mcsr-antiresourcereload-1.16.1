package me.wurgo.antiresourcereload.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.wurgo.antiresourcereload.AntiResourceReload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.EntityTypeTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    private Profiler profiler;

    @WrapOperation(
            method = "createIntegratedResourceManager",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ServerResourceManager;reload(Ljava/util/List;Lnet/minecraft/server/command/CommandManager$RegistrationEnvironment;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<ServerResourceManager> cachedReload(List<ResourcePack> dataPacks, CommandManager.RegistrationEnvironment registrationEnvironment, int i, Executor executor, Executor executor2, Operation<CompletableFuture<ServerResourceManager>> original, @Local ResourcePackManager<ResourcePackProfile> resourcePackManager) throws ExecutionException, InterruptedException {
        boolean usingDataPacks = !resourcePackManager.getEnabledProfiles().stream().map(ResourcePackProfile::getName).collect(Collectors.toList()).equals(DataPackSettings.SAFE_MODE.getEnabled());
        if (usingDataPacks) {
            AntiResourceReload.log("Using data-packs, reloading.");
        } else if (AntiResourceReload.cache == null) {
            AntiResourceReload.log("Cached resources unavailable, reloading & caching.");
        } else {
            AntiResourceReload.log("Using cached server resources.");
            // only reload recipes on the main thread
            // this is for compat with seedqueue creating servers in the background
            if (MinecraftClient.getInstance().isOnThread() && AntiResourceReload.hasSeenRecipes) {
                ServerResourceManager manager = AntiResourceReload.cache.get();
                ((RecipeManagerAccess) manager.getRecipeManager()).invokeApply(AntiResourceReload.recipes, manager.getResourceManager(), this.profiler);
                AntiResourceReload.hasSeenRecipes = false;
            }
            return AntiResourceReload.cache;
        }

        CompletableFuture<ServerResourceManager> reloaded = original.call(dataPacks, registrationEnvironment, i, executor, executor2);
        
        if (!usingDataPacks) {
            AntiResourceReload.cache = reloaded;
        }
        return reloaded;
    }

    @WrapWithCondition(
            method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ServerResourceManager;loadRegistryTags()V"
            )
    )
    private boolean skipReloadingRegistryTags(ServerResourceManager manager) {
        return manager.getRegistryTagManager().blocks() != BlockTags.getContainer() ||
                manager.getRegistryTagManager().items() != ItemTags.getContainer() ||
                manager.getRegistryTagManager().fluids() != FluidTags.getContainer() ||
                manager.getRegistryTagManager().entityTypes() != EntityTypeTags.getContainer();
    }
}
