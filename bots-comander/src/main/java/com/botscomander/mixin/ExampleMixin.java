package com.botscomander.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session; // ВАЖНО: Проверь этот импорт!
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface ExampleMixin {

	@Accessor("session")
	void setSession(Session session);
}