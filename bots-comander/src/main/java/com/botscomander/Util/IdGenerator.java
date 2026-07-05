package com.botscomander.Util;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

public class IdGenerator {
    private static final String ID_FILE_NAME = "config/bot_id.txt";

    public static String getOrCreateBotId() {
        File file = new File(ID_FILE_NAME);

        // 1. Проверяем, существует ли уже сохраненный ID
        if (file.exists()) {
            try {
                // Читаем ID из файла, убирая лишние пробелы
                String savedId = new String(Files.readAllBytes(file.toPath())).trim();
                if (!savedId.isEmpty()) {
                    return savedId; // Возвращаем старый ID
                }
            } catch (IOException e) {
                System.out.println("Ошибка чтения bot_id: " + e.getMessage());
            }
        }

        // 2. Если файла нет или он пустой, генерируем НОВЫЙ уникальный ID
        // UUID.randomUUID() создает строку типа "123e4567-e89b-12d3-a456-426614174000"
        String newId = UUID.randomUUID().toString();

        // 3. Сохраняем этот новый ID в файл, чтобы при следующем запуске прочитать его
        try {
            // Создаем папки, если их нет (например, если папки config еще не существует)
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
}
