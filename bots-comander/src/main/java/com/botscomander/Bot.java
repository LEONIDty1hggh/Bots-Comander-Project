package com.botscomander;
import com.botscomander.Util.IdGenerator;
import com.botscomander.mixin.ExampleMixin;
import com.botscomander.network.NetworkClient;
import net.fabricmc.fabric.mixin.networking.client.accessor.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;

public class Bot {
    private String bot_id;
    private String username = "";
    private NetworkClient network;
    private MinecraftClient MC = MinecraftClient.getInstance();
    private String lastcommand = null;
    private int step = 0;
    private int timer = 0;
    private int totalItems = 0;
    private boolean getisNeedToRefreshAh = false;
    private boolean setisNeedToTakeAllFromAh = false;
    private boolean needToDropAll = false;
    private int currentDropSlot = 0;

    public Bot() {

        IdGenerator generator = new IdGenerator();

        this.bot_id = generator.getOrCreateBotId();

        this.network = new NetworkClient("localhost", 5000, this.bot_id);

        // 3. Запускаем асинхронное чтение сокета
        this.network.startListening();
    }

    public void sendMessage(String message) {
        if (MC.player != null) {
            MC.player.networkHandler.sendChatMessage(message);
        }
        else {
            System.out.println("[" + this.bot_id + "] Игрок не найден, сообщение не отправлено.");
        }
    }

    public void sendCommand(String command) {
        if (MC.player != null) {
            if (command.contains("fullbal")) {
                MC.getNetworkHandler().sendChatCommand("money");
                lastcommand = command;
                return;
            }
            MC.getNetworkHandler().sendChatCommand(command);
        }
        else {
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
                    MC.disconnect(new net.minecraft.client.gui.screen.TitleScreen(), false);
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
        // Шаг 0: Команда
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
                this.timer = 20; // Пауза 2 сек на открытие
            }
        }
        // Шаги в меню
        else if (client.currentScreen instanceof GenericContainerScreen screen) {
            if (this.step == 1) {
                clickSlot(client, screen, 46); // Клик по сундуку
                this.step = 2;
                this.timer = 20;
            } else if (this.step == 2) {
                clickSlot(client, screen, 52); // Клик по часам
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

    public void takeAllFromAh() {
        if (MC.player == null) {
            System.out.println("[" + this.bot_id + "] Ошибка, зайдите на сервер");
            return;
        }
        if (this.step == 0) {
            MC.getNetworkHandler().sendChatCommand("ah");
            this.timer = 20;
            this.step = 1;
        }
        else if (MC.currentScreen instanceof GenericContainerScreen screen) {
            if (this.step == 1) {
                clickSlot(MC, screen, 46); // Клик по сундуку
                this.step = 2;
                this.timer = 20;
            } else if (this.step == 2) {
                var handler = screen.getScreenHandler();
                for (int i = 0; i < handler.slots.size(); i++) {
                    if (i >= 45 && i <= 53) {
                        continue;
                    }
                    var slot = handler.getSlot(i);
                    if (slot != null && slot.hasStack()) {
                        totalItems += slot.getStack().getCount();
                    }
                }
                this.step = 3;
            } else if (step == 3) {
                clickSlot(MC, screen, 0);
                timer = 10;
                if (totalItems == 0) {
                    var handler = screen.getScreenHandler();
                    for (int i = 0; i < handler.slots.size(); i++) {
                        if (i >= 45 && i <= 53) {
                            continue;
                        }
                        var slot = handler.getSlot(i);
                        if (slot != null && slot.hasStack()) {
                            totalItems += slot.getStack().getCount();
                        }
                    }
                    if (totalItems == 0) {
                        this.step = 4;
                    }
                }
            }else if (step == 4) {
                MC.player.closeHandledScreen();
                this.step = 0;
                this.timer = 0;
                this.totalItems = 0;
                this.getisNeedToRefreshAh = false;
            }
        }
    }

    private void clickSlot(MinecraftClient client, GenericContainerScreen screen, int slotId) {
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slotId, 0, SlotActionType.PICKUP, client.player
            );
        }
    }

    public boolean isNeedToDropAll() {
        return this.needToDropAll;
    }

    public void setNeedToDropAll(boolean needToDropAll) {
        this.needToDropAll = needToDropAll;
    }

    public int getCurrentDropSlot() {
        return this.currentDropSlot;
    }

    public void setCurrentDropSlot(int currentDropSlot) {
        this.currentDropSlot = currentDropSlot;
    }

    public void triggerDropAll() {
        this.currentDropSlot = 0;
        this.needToDropAll = true;
    }

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

}
