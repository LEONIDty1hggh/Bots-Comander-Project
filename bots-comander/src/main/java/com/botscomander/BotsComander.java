package com.botscomander;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class BotsComander implements ClientModInitializer {
	public static final String MOD_ID = "bots-comander";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Bot bot = new Bot();

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			handleMessage(message.getString());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.interactionManager == null) return;

			// ================= БЛОК ДЛЯ DROP ALL =================
			if (bot.isNeedToDropAll()) {
				var handler = client.player.currentScreenHandler;
				if (handler != null) {
					int currentSlot = bot.getCurrentDropSlot();
					boolean itemDropped = false;

					while (currentSlot < handler.slots.size()) {
						var slot = handler.getSlot(currentSlot);

						if (slot != null && slot.inventory instanceof net.minecraft.entity.player.PlayerInventory && slot.hasStack()) {
							client.interactionManager.clickSlot(
									handler.syncId,
									currentSlot,
									1,
									net.minecraft.screen.slot.SlotActionType.THROW,
									client.player
							);
							itemDropped = true;
							currentSlot++;
							bot.setCurrentDropSlot(currentSlot);
							break;
						}
						currentSlot++;
					}

					// Если дошли до конца и ничего не дропнули — выключаем режим
					if (!itemDropped && currentSlot >= handler.slots.size()) {
						bot.setNeedToDropAll(false);
						System.out.println("[Bot] Все вещи успешно сброшены!");
					}
				}
				return;
			}
			Integer timer = bot.gettimer();
			boolean isNeedToRefreshah = bot.getisNeedToRefreshAh();
			boolean isNeedToTakeAllFromAh = bot.getisNeedToTakeAllFromAh();
			if (!isNeedToRefreshah && !isNeedToTakeAllFromAh) return;
			if (timer > 0) {
				timer--;
				bot.setTimer(timer);
				return;
			}

			if (isNeedToRefreshah) {
				bot.refreshAh(client);
			}else {
				bot.takeAllFromAh();
			}
		});
	}

	private void handleMessage(String message) {
		String low_message = message.toLowerCase();
		if (low_message.contains("ваш бал")) {
			try {
				String[] partsAfterColon = low_message.split(":", 2);

				if (partsAfterColon.length > 1) {
					String[] dollarParts = partsAfterColon[1].split("\\$");

					if (dollarParts.length > 1) {
						String balance = dollarParts[1].trim();

						System.out.println("[BOT] Баланс успешно распарсен: " + balance);
						bot.sendFullbalCommand(balance);
					}
				}
			} catch (Exception e) {
				System.out.println("Ошибка при парсинге баланса: " + e.getMessage());
			}
		}
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
