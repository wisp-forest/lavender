package io.wispforest.lavender.mixin;

import io.wispforest.lavender.pond.LavenderLifecycledResourceManagerExtension;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LifecycledResourceManagerImpl.class)
public class LifecycledResourceManagerMixin implements LavenderLifecycledResourceManagerExtension {

    @Unique
    private ResourceType resourceType;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void captureResourceType(ResourceType type, List<?> packs, CallbackInfo ci) {
        this.resourceType = type;
    }

    @Override
    public ResourceType lavender$resourceType() {
        return this.resourceType;
    }
}
