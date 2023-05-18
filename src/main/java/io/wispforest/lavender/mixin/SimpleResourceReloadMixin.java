package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.pond.LavenderLifecycledResourceManagerExtension;
import net.minecraft.resource.ResourceManager;
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

    @Inject(method = "<init>", at = @At(value = "INVOKE_ASSIGN", target = "Lcom/google/common/collect/Sets;newHashSet(Ljava/lang/Iterable;)Ljava/util/HashSet;"))
    private void loadLavenderBooks(Executor prepareExecutor, Executor applyExecutor, ResourceManager manager, List reloaders, @Coerce Object factory, CompletableFuture initialStage, CallbackInfo ci) {
        if (!(manager instanceof LavenderLifecycledResourceManagerExtension extension) || extension.lavender$resourceType() != ResourceType.CLIENT_RESOURCES) return;
        BookLoader.reload(manager);
    }

}
