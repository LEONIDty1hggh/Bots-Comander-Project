package com.botscomander.Util;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.botscomander.BotsComander.bot;

public class IdGenerator {
    private static MinecraftClient MC = MinecraftClient.getInstance();
    private static final String BOT_NAME = getBotUsername();
    private static final String ID_FILE_NAME = "config/bot_id_" + BOT_NAME + ".txt";



    public static String getOrCreateBotId() {
        File file = new File(ID_FILE_NAME);

        if (file.exists()) {
            try {
                String savedId = new String(Files.readAllBytes(file.toPath())).trim();
                if (!savedId.isEmpty()) {
                    return savedId;
                }
            } catch (IOException e) {
                System.out.println("Ошибка чтения bot_id: " + e.getMessage());
            }
        }

        String newId = UUID.randomUUID().toString();

        try {
            file.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(newId);
            }
            System.out.println("Сгенерирован и сохранен новый bot_id: " + newId);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения bot_id: " + e.getMessage());
        }

        return newId;
    }

    private static String getBotUsername() {
        if (MC.player != null) {
            return MC.player.getName().getString();
        } else if (MC.getSession() != null) {
            return MC.getSession().getUsername();
        }
        return "";
    }
}
