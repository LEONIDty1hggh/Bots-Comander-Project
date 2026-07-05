package com.botscomander;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotsComander implements ClientModInitializer {
	public static final String MOD_ID = "bots-comander";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Bot bot = new Bot();
	public static long timerforreset = 0;
	private static long now = System.currentTimeMillis();

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			String rawText = message.getString();
			handleMessage(rawText);
		});

		// Прослушка системных сообщений и оповещений
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String rawText = message.getString();
			handleMessage(rawText);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			now = System.currentTimeMillis();
			if (now - timerforreset > 10000) {
				bot.setcommandsendtimes(0);
				timerforreset = System.currentTimeMillis();
			}
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

					if (!itemDropped && currentSlot >= handler.slots.size()) {
						bot.setNeedToDropAll(false);
						System.out.println("[Bot] Все вещи успешно сброшены!");
					}
				}
				return;
			}

			// ================= БЛОК АУКЦИОНА =================
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
			} else {
				// Передаем текущее время для работы таймаутов защиты сборщика лотов
				bot.takeAllFromAh(System.currentTimeMillis());
			}
		});
	}
	// фикс балика
	private void handleMessage(String message) {

		String clean = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
		String low = clean.toLowerCase();


		// ================= BALANCE =================
		if (low.contains("ваш баланс")) {

			Matcher m = Pattern.compile("\\$(\\d[\\d,]*)")
					.matcher(clean);

			if (m.find()) {

				String balance = m.group(1).replace(",", "");

				bot.setExpectedBalance(balance);
				bot.sendFullbalCommand(balance);
			}

			return;
		}

		// ================= PAY CONFIRM =================
		if (clean.contains("/pay")) {

			Matcher m = Pattern.compile("/pay\\s+(\\S+)\\s+(\\d+)")
					.matcher(clean);

			if (m.find()) {

				String amount = m.group(2);
				String expected = bot.getExpectedBalance();

				if (expected != null && expected.equals(amount)) {


					bot.sendFullbalCommand(amount);
					bot.setExpectedBalance("");
				}
			}
		}
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}