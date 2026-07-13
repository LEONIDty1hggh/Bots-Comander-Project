package com.botscomander;

import com.botscomander.Util.IdGenerator;
import com.botscomander.Util.Timer;
import com.botscomander.mixin.ExampleMixin;
import com.botscomander.network.NetworkClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.HashMap;

// =============================================================
// Класс бота, сюда писать новые функции для выполнения игроком
// =============================================================

public class Bot {
    private String bot_id;
    private String username = "";
    private NetworkClient network;
    private MinecraftClient MC = MinecraftClient.getInstance();
    private String lastcommand = null;
    private HashMap<String, String> last_command = new HashMap<>();
    private int step = 0;
    private int timer = 0;
    private int totalItems = 0;
    private boolean getisNeedToRefreshAh = false;
    private boolean setisNeedToTakeAllFromAh = false;
    private boolean needToDropAll = false;
    private int currentDropSlot = 0;
    private boolean isneedsendcommandanagin = false;
    private boolean afk = false;
    private int commandsendtimes = 0;
    private String expectedBalance = "";
    private Random random = new Random();
    private long takeAllStartTime = 0;
    private float currentMouseXSpeed = 0;
    private float currentMouseYSpeed = 0;
    private Timer AfkTimer = new Timer();
    private boolean walkForward = true;

    public Bot(){
        IdGenerator generator = new IdGenerator();
        this.bot_id = generator.getOrCreateBotId();
        this.network = new NetworkClient("localhost", 5000, this.bot_id);
        this.network.startListening();
    }

    public void sendMessage(String message) {
        if (MC.player != null) {
            MC.player.networkHandler.sendChatMessage(message);
        } else {
            System.out.println("[" + this.bot_id + "] Игрок не найден, сообщение не отправлено.");
        }
    }

    public void sendCommand(String command) {
        if (MC.player != null) {
            if (command.contains("pay")) {
                isneedsendcommandanagin = true;
                if (command.contains("fullbal")) {
                    MC.getNetworkHandler().sendChatCommand("money");
                    lastcommand = command;
                    return;
                }
            }

            MC.getNetworkHandler().sendChatCommand(command);
        } else {
            System.out.println("[" + this.bot_id + "] Игрок не найден, команда не отправлена.");
        }
    }

    public void sendFullbalCommand(String balance) {
        if (lastcommand == null) {
            System.out.println("[" + this.bot_id + "] Команда не найдена, команда не отправлена.");
            return;
        }

        if (MC.player != null) {
            MC.getNetworkHandler().sendChatCommand(lastcommand.replace("fullbal", balance));
        }
    }
    public String getBotUsername() {
        if (MC.player != null) {
            return MC.player.getName().getString();
        } else if (MC.getSession() != null) {
            return MC.getSession().getUsername();
        }
        return "";
    }

    public void changeBotNick(String newNickname) {
        MC.execute(() -> {
            try {
                System.out.println("[" + this.bot_id + "] Начинаю смену ника на: " + newNickname);
                if (MC.world != null) {
                    MC.disconnect(new TitleScreen(), false);
                    System.out.println("[" + this.bot_id + "] Отключились от сервера для смены ника.");
                }
                Session newSession = new Session(
                        newNickname,
                        UUID.randomUUID(),
                        "0",
                        Optional.empty(),
                        Optional.empty(),
                        Session.AccountType.MOJANG
                );
                ((ExampleMixin) MC).setSession(newSession);
                System.out.println("[" + this.bot_id + "] Ник в сессии успешно изменен!");
            } catch (Exception e) {
                System.out.println("Ошибка при смене ника: " + e.getMessage());
            }
        });
    }

    public void connectToServer(String ip) {
        MC.execute(() -> {
            try {
                ServerInfo serverInfo = new ServerInfo("Server", ip + ":25565", ServerInfo.ServerType.OTHER);
                serverInfo.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
                ConnectScreen.connect(
                        new TitleScreen(),
                        MC,
                        ServerAddress.parse(serverInfo.address),
                        serverInfo,
                        false,
                        null
                );
                System.out.println("[" + this.bot_id + "] Подключаюсь к " + ip);
            } catch (Exception e) {
                System.out.println("Ошибка при старте подключения в главном потоке: " + e.getMessage());
            }
        });
    }

    public void refreshAh(MinecraftClient client) {
        if (this.step == 0) {
            if (client.currentScreen instanceof HandledScreen<?> screen) {
                String title = screen.getTitle().getString().toLowerCase();
                if (title.contains("хранилище")){
                    this.step = 2;
                    this.timer = 20;
                }
            } else {
                client.player.networkHandler.sendChatCommand("ah");
                this.step = 1;
                this.timer = 20;
            }
        }
        else if (client.currentScreen instanceof GenericContainerScreen screen) {
            if (this.step == 1) {
                clickSlot(client, screen, 46);
                this.step = 2;
                this.timer = 20;
            } else if (this.step == 2) {
                clickSlot(client, screen, 52);
                this.step = 3;
                this.timer = 20;
            } else if (step == 3) {
                MC.player.closeHandledScreen();
                this.step = 0;
                this.timer = 0;
                this.totalItems = 0;
                this.getisNeedToRefreshAh = false;
            }
        }
    }

    public void takeAllFromAh(long now) {
        if (MC.player == null) {
            System.out.println("[" + this.bot_id + "] Ошибка, зайдите на сервер");
            return;
        }

        // Защита от зависания: если процесс затянулся больше 30 сек — сбрасываем
        if (this.step > 0 && takeAllStartTime > 0 && (now - takeAllStartTime) > 30000) {
            resetTakeAll("TIMEOUT 30s");
            return;
        }

        switch (this.step) {
            case 0 -> {
                takeAllStartTime = now;
                MC.getNetworkHandler().sendChatCommand("ah");
                this.timer = 20;
                this.step = 1;
            }
            case 1 -> {
                if (MC.currentScreen instanceof HandledScreen<?> screen) {
                    String title = screen.getTitle().getString().toLowerCase();
                    if (title.contains("аукцион") || title.contains("auction")) {
                        clickSlot(MC, screen, 46); // Клик по хранилищу
                        this.step = 2;
                        this.timer = 15;
                    } else {
                        MC.player.closeHandledScreen();
                        this.timer = 10;
                    }
                } else if ((now - takeAllStartTime) > 5000) {
                    MC.getNetworkHandler().sendChatCommand("ah");
                    this.timer = 20;
                } else {
                    this.timer = 5;
                }
            }
            case 2 -> {
                if (!(MC.currentScreen instanceof HandledScreen<?> screen)) {
                    resetTakeAll("Экран пропал на шаге 2");
                    return;
                }
                this.totalItems = countStorageItems(screen.getScreenHandler());
                if (this.totalItems <= 0) {
                    finishTakeAll();
                } else {
                    this.step = 3;
                    this.timer = 5;
                }
            }
            case 3 -> {
                if (!(MC.currentScreen instanceof HandledScreen<?> screen)) {
                    finishTakeAll();
                    return;
                }
                clickSlot(MC, screen, 0); // Забираем предмет из 0 слота хранилища
                this.totalItems--;

                if (this.totalItems <= 0) {
                    this.step = 4;
                    this.timer = 10;
                } else {
                    this.timer = 8; // Небольшая задержка между кликами для стабильности
                }
            }
            case 4 -> finishTakeAll();
            default -> resetTakeAll("Неизвестный шаг " + this.step);
        }
    }

    private static int countStorageItems(@NotNull ScreenHandler handler) {
        int total = 0;
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack()) {
                total += slot.getStack().getCount();
            }
        }
        return total;
    }

    private void finishTakeAll() {
        if (MC.player != null) {
            MC.player.closeHandledScreen();
        }
        MC.setScreen(null);
        this.step = 0;
        this.timer = 0;
        this.totalItems = 0;
        this.setisNeedToTakeAllFromAh = false;
        takeAllStartTime = 0;
        System.out.println("[" + this.bot_id + "] Сбор лотов завершён, окно закрыто");
    }

    private void resetTakeAll(String reason) {
        System.out.println("[" + this.bot_id + "] takeAll сброс: " + reason);
        if (MC.player != null) {
            MC.player.closeHandledScreen();
        }
        MC.setScreen(null);
        this.step = 0;
        this.timer = 0;
        this.totalItems = 0;
        this.setisNeedToTakeAllFromAh = false;
        takeAllStartTime = 0;
    }

    private void clickSlot(MinecraftClient client, HandledScreen<?> screen, int slotId) {
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slotId, 0, SlotActionType.PICKUP, client.player
            );
        }
    }
    // Анти афк
    public void inAfk () {
        if (this.last_command.get("type") == null) {
            this.afk = false;
            this.MC.options.forwardKey.setPressed(false);
            this.MC.options.backKey.setPressed(false);
            return;
        }

        if (!AfkTimer.isTimerOn()) {
            System.out.println("[Bot-Debug] Старт обхода. Задаю вектор мыши и ходьбы.");
            this.currentMouseXSpeed = (random.nextFloat() * 4.0f) - 2.0f;
            this.currentMouseYSpeed = (random.nextFloat() * 2.0f) - 1.0f;
            if (walkForward) {
                this.MC.options.forwardKey.setPressed(true);
            } else {
                this.MC.options.backKey.setPressed(true);
            }
        }

        if (this.MC.player != null) {
            // Прямое воздействие на углы с шагом, имитирующим движение мыши
            this.MC.player.setYaw(this.MC.player.getYaw() + this.currentMouseXSpeed);
            this.MC.player.setPitch(this.MC.player.getPitch() + this.currentMouseYSpeed);
        }

        if(!AfkTimer.startTimer(1500)){
            return;
        }
        System.out.println("[Bot-Debug] Движение окончено. Сброс.");
        this.MC.options.forwardKey.setPressed(false);
        this.MC.options.backKey.setPressed(false);

        walkForward = !walkForward;


        String type = this.last_command.get("type");
        String data = this.last_command.get("data");

        switch (type) {
            case "msg":
                this.sendMessage(data);
                break;
            case "command":
                System.out.println("[Bot-Debug] Повторный ввод команды после движения камеры: " + data);
                this.sendCommand(data);
                break;
            case "connect":
                this.connectToServer(data);
                break;
            case "takeallfromah":
                this.setisNeedToTakeAllFromAh(true);
                break;
            case "refreshah":
                this.setisNeedToRefreshAh(true);
                break;
            case "dropall":
                this.triggerDropAll();
                break;
        }

        this.afk = false;
        this.last_command.clear();
    }

    public boolean isNeedToDropAll() { return this.needToDropAll; }
    public void setNeedToDropAll(boolean needToDropAll) { this.needToDropAll = needToDropAll; }
    public int getCurrentDropSlot() { return this.currentDropSlot; }
    public void setCurrentDropSlot(int currentDropSlot) { this.currentDropSlot = currentDropSlot; }
    public void triggerDropAll() { this.currentDropSlot = 0; this.needToDropAll = true; }
    public Integer gettimer() { return this.timer; }
    public void setTimer(Integer timer) { this.timer = timer; }
    public boolean getisNeedToRefreshAh() { return this.getisNeedToRefreshAh; }
    public boolean getisNeedToTakeAllFromAh() { return this.setisNeedToTakeAllFromAh; }

    public void setisNeedToRefreshAh(boolean getisNeedToRefreshAh) {
        if (this.setisNeedToTakeAllFromAh) {
            System.out.println("Сейчас работает сбор предметов с аукциона ...");
            return;
        }
        this.getisNeedToRefreshAh = getisNeedToRefreshAh;
        this.timer = 0;
        this.step = 0;
        this.totalItems = 0;
    }

    public void setisNeedToTakeAllFromAh(boolean setisNeedToTakeAllFromAh) {
        if (this.getisNeedToRefreshAh) {
            System.out.println("Сейчас работает перезагрузка аукциона ...");
            return;
        }
        this.setisNeedToTakeAllFromAh = setisNeedToTakeAllFromAh;
        this.timer = 0;
        this.step = 0;
        this.totalItems = 0;
    }
    public String getExpectedBalance() {
        return this.expectedBalance;
    }

    public void setExpectedBalance(String expectedBalance) {
        this.expectedBalance = expectedBalance;
    }

    public void setcommandsendtimes(int commandsendtimes) {this.commandsendtimes = commandsendtimes;}

    public void setAFK(boolean afk) {this.afk = afk;}
    public boolean getAFK() {return this.afk;}

    public HashMap<String, String> getLastCommand() { return this.last_command; }
    public void setLastCommand(String type, String data) {
        this.last_command.clear();
        this.last_command.put("type", type);
        this.last_command.put("data", data);
    }
}