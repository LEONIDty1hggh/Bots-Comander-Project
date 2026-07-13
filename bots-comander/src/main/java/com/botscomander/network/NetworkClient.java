package com.botscomander.network;

import net.minidev.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static com.botscomander.BotsComander.bot;
import com.botscomander.BotsComander;

public class NetworkClient {
    private final String host;
    private final int port;
    private final String botId;


    public NetworkClient(String host, int port, String botId) {
        this.host = host;
        this.port = port;
        this.botId = botId;
    }

    public void startListening() {
        new Thread(() -> {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("[" + botId + "] Успешно подключился к Python-серверу!");
                String username_reg = bot.getBotUsername();

                out.println("AUTH:" + botId + ":" + username_reg);

                String response;
                while ((response = in.readLine()) != null) {
                    System.out.println("[" + botId + "] Получено: " + response);
                    try {
                        com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(response).getAsJsonObject();

                        String prompt = data.get("promt").getAsString();       // "msg" или "command"
                        String type = data.get("type").getAsString();         // "all" or "single"
                        String message = data.get("message").getAsString();   // Сам текст

                        String username = null;
                        if (data.has("username") && !data.get("username").isJsonNull()) {
                            username = data.get("username").getAsString();
                        }
                        if (prompt.equals("changenik")) {
                            if (changeusername(message)) {
                               out.println("CHANGENIK:" + botId + ":" + message);
                            }
                        }
                        handleServerMessage(prompt, message);

                    } catch (Exception e) {
                        System.out.println("Ошибка парсинга JSON (Gson): " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                System.out.println("[" + botId + "] Ошибка соединения: " + e.getMessage());
            }
        }).start();
    }

    private void handleServerMessage(String promt, String message) {
        if (promt.equals("msg")) {
            System.out.println("Запрос с сервера на отправку сообщения " + message);
            bot.setLastCommand("msg", message);
            bot.sendMessage(message);
        }
        else if (promt.equals("command")) {
            System.out.println("Запрос с сервера на отправку команды " + message);
            bot.setcommandsendtimes(0);
            BotsComander.timerforreset = 0;
            bot.setLastCommand("command", message);
            bot.sendCommand(message);
        }
        else if (promt.equals("connect")) {
            System.out.println("Запрос с сервера на подключение к серверу " + message);
            bot.setLastCommand("connect", message);
            bot.connectToServer(message);
        }
        else if (promt.equals("takeallfromah")) {
            System.out.println("Запрос с сервера на снятия всего с аукциона" + message);
            bot.setLastCommand("takeallfromah", message);
            bot.setisNeedToTakeAllFromAh(true);
        }
        else if (promt.equals("refreshah")) {
            System.out.println("Запрос с сервера на перевыставление предметов на аукционе" + message);
            bot.setLastCommand("refreshah", message);
            bot.setisNeedToRefreshAh(true);
        }
        else if (promt.equals("dropall")) {
            System.out.println("Запрос с сервера на выкидываение всех пердметов" + message);
            bot.setLastCommand("dropall", message);
            bot.triggerDropAll();
        }
    }

    private boolean changeusername (String message) {
        System.out.println("Запрос с сервера на смену ника " + message);
        try {
            bot.setLastCommand("changenik", message);
            bot.changeBotNick(message);
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка при смене ника: " + e.getMessage());
            return false;
        }
    }
}