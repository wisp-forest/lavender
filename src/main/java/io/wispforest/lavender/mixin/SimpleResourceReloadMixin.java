package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.pond.LavenderLifecycledResourceManagerExtension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SimpleResourceReload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleResourceReload.class)
public class SimpleResourceReloadMixin {

    @Inject(method = "start(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Lnet/minecraft/resource/SimpleResourceReload$Factory;Ljava/util/concurrent/CompletableFuture;)V", at = @At("HEAD"))
    private void loadLavenderBooks(Executor prepareExecutor, Executor applyExecutor, ResourceManager manager, List<ResourceReloader> reloaders, @Coerce Object factory, CompletableFuture<?> initialStage, CallbackInfo ci) {
        if (!(manager instanceof LavenderLifecycledResourceManagerExtension extension) || extension.lavender$resourceType() != ResourceType.CLIENT_RESOURCES) return;
        if (MinecraftClient.getInstance().world == null) return;

        BookLoader.reload(manager);
    }

}
