import socket
import json
import os
import threading
import customtkinter as ctk

HOST = "localhost"
PORT = 5000
botlist = {}
DB_FILE = "bots_database.json"

# Gui by Gemini(AI)
ctk.set_appearance_mode("System")  # Автовыбор (Тёмная/Светлая) в зависимости от ОС
ctk.set_default_color_theme("blue")  # Цветовая схема

log_widget = None


def log_to_gui(text):
    """Кастомный принт, который дублирует сообщения в окно GUI и в обычную консоль"""
    print(text)
    if log_widget:
        log_widget.after(0, lambda: _write_to_log(text))


def _write_to_log(text):
    log_widget.configure(state="normal")
    log_widget.insert(ctk.END, text + "\n")
    log_widget.see(ctk.END)
    log_widget.configure(state="disabled")


# ==================== ЛОГИКА СЕРВЕРА (БЕЗ ИЗМЕНЕНИЙ) ====================

def handle_bot(conn, addr):
    log_to_gui(f"Бот подключился с адреса: {addr}")
    current_bot_id = None

    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break

            message = data.decode('utf-8').strip()
            log_to_gui(f"Получено от бота: {message}")

            if message.startswith("AUTH:"):
                try:
                    parts = message.split(":")
                    bot_id = parts[1]
                    username = parts[2]
                    current_bot_id = bot_id

                    botlist[bot_id] = {
                        "conn": conn,
                        "status": "online",
                        "username": username
                    }
                    log_to_gui(f"Бот ({bot_id}) ({username}) успешно добавлен в список!")
                    save_botlist()
                except Exception as e:
                    conn.sendall(f"error: {e}\n".encode('utf-8'))

            elif message.startswith("DISCONNECT:"):
                parts = message.split(":")
                try:
                    bot_id = parts[1]
                    if bot_id in botlist:
                        botlist[bot_id]["conn"] = None
                        botlist[bot_id]["status"] = "offline"
                        log_to_gui(f"Бот {botlist[bot_id]['username']} ({bot_id}) успешно отключен.")
                        save_botlist()
                    else:
                        log_to_gui(f"Попытка отключения неизвестного bot_id: {bot_id}")
                except Exception as e:
                    log_to_gui(f"Ошибка при обработке отключения: {e}")

            elif message.startswith("CHANGENIK:"):
                parts = message.split(":")
                bot_id = parts[1]
                username = parts[2]
                if bot_id in botlist:
                    botlist[bot_id]["username"] = username
                    log_to_gui(f"Ник бота ({bot_id}) успешно изменён на ({username}) ")
                    save_botlist()

    except Exception as e:
        log_to_gui(f"Ошибка соединения с ботом {addr}: {e}")
    finally:
        if current_bot_id and current_bot_id in botlist:
            if botlist[current_bot_id]["conn"] == conn:
                botlist[current_bot_id]["status"] = "offline"
                botlist[current_bot_id]["conn"] = None
                log_to_gui(f"Связь с ботом ({current_bot_id}) потеряна.")
                save_botlist()
        conn.close()


def start_server():
    load_botlist()

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen()
    log_to_gui("Сервер запущен и слушает порт...")

    while True:
        try:
            conn, addr = server_socket.accept()
            bot_thread = threading.Thread(target=handle_bot, args=(conn, addr), daemon=True)
            bot_thread.start()
        except Exception as e:
            log_to_gui(f"Ошибка при приеме подключения: {e}")


def save_botlist():
    clean_data = {}
    for bot_id, info in botlist.items():
        clean_data[bot_id] = {
            "username": info["username"],
            "status": info["status"],
        }

    with open(DB_FILE, "w", encoding="utf-8") as f:
        json.dump(clean_data, f, ensure_ascii=False, indent=4)
    log_to_gui("База данных ботов успешно сохранена в файл.")


def load_botlist():
    global botlist
    if os.path.exists(DB_FILE) and os.path.getsize(DB_FILE) > 0:
        try:
            with open(DB_FILE, "r", encoding="utf-8") as f:
                saved_data = json.load(f)

                for bot_id, info in saved_data.items():
                    botlist[bot_id] = {
                        "conn": None,
                        "username": info["username"],
                        "status": "offline"
                    }
            log_to_gui(f"Загружено ботов из файла: {len(botlist)}")
        except Exception as e:
            log_to_gui(f"Ошибка при загрузке базы данных: {e}")
            botlist = {}
    else:
        log_to_gui("Файл базы данных пуст или не найден, создаем чистый список.")
        botlist = {}


def shutdown_server():
    log_to_gui("\n[!] Выключаем сервер. Сбрасываем статусы ботов...")
    for info in botlist.values():
        info["conn"] = None
        info["status"] = "offline"
    save_botlist()
    log_to_gui("[+] Все боты переведены в offline. База сохранена.")


# ==================== ИНТЕРФЕЙС GUI (CUSTOMTKINTER) ====================

def send_command_from_gui():
    promt_type = cmd_combo.get()
    flag = flag_var.get()
    username = username_entry.get().strip()
    clean_message = message_entry.get().strip()

    if flag == "-single" and not username:
        log_to_gui("error: no username для флага -single")
        return

    if promt_type == "command" and "/" in clean_message:
        log_to_gui("error: Введите команду без '/'")
        return

    msg_type = "all" if flag == "-all" else "single"
    if flag != "-all":
        username = username.removeprefix("-")
    else:
        username = None

    data = {
        "promt": promt_type,
        "type": msg_type,
        "username": username,
        "message": clean_message
    }

    json_data = json.dumps(data, ensure_ascii=False)

    if msg_type == "all":
        for info in botlist.values():
            if info["status"] == "online" and info["conn"] is not None:
                conn = info["conn"]
                packet = json_data + "\n"
                conn.sendall(packet.encode("utf-8"))
    elif msg_type == "single":
        for info in botlist.values():
            if info["status"] == "online" and info["conn"] is not None and info["username"] == username:
                conn = info["conn"]
                packet = json_data + "\n"
                conn.sendall(packet.encode("utf-8"))

    log_to_gui(f"[Успешно спарсено JSON]: {json_data}")
    message_entry.delete(0, ctk.END)


def toggle_username_entry():
    if flag_var.get() == "-all":
        username_entry.configure(state="disabled", placeholder_text="Выбрано: Всем")
        username_entry.delete(0, ctk.END)
    else:
        username_entry.configure(state="normal", placeholder_text="Введи ник бота")


def on_closing():
    shutdown_server()
    root.destroy()


# Настройка главного окна CustomTkinter
root = ctk.CTk()
root.title("Bots Commander Server GUI")
root.geometry("750x600")
root.protocol("WM_DELETE_WINDOW", on_closing)

# Панель управления командами
control_frame = ctk.CTkFrame(root, corner_radius=10)
control_frame.pack(fill="x", padx=15, pady=10)

# Строка 1: Выбор команды и флага
ctk.CTkLabel(control_frame, text="Команда:", font=("Arial", 13, "bold")).grid(row=0, column=0, sticky="w", padx=15,
                                                                              pady=10)
cmd_combo = ctk.CTkComboBox(control_frame,
                            values=["msg", "command", "changenik", "connect", "takeallfromah", "refreshah", "dropall"],
                            width=140)
cmd_combo.set("msg")
cmd_combo.grid(row=0, column=1, sticky="w", padx=5, pady=10)

ctk.CTkLabel(control_frame, text="Кому:", font=("Arial", 13, "bold")).grid(row=0, column=2, sticky="w", padx=20,
                                                                           pady=10)
flag_var = ctk.StringVar(value="-all")

rb_all = ctk.CTkRadioButton(control_frame, text="Всем (-all)", variable=flag_var, value="-all",
                            command=toggle_username_entry)
rb_single = ctk.CTkRadioButton(control_frame, text="Определенному (-single)", variable=flag_var, value="-single",
                               command=toggle_username_entry)
rb_all.grid(row=0, column=3, sticky="w", padx=5, pady=10)
rb_single.grid(row=0, column=4, sticky="w", padx=5, pady=10)

# Строка 2: Ввод Никнейма и сообщения
ctk.CTkLabel(control_frame, text="Ник бота:", font=("Arial", 13, "bold")).grid(row=1, column=0, sticky="w", padx=15,
                                                                               pady=10)
username_entry = ctk.CTkEntry(control_frame, width=140, placeholder_text="Выбрано: Всем", state="disabled")
username_entry.grid(row=1, column=1, sticky="w", padx=5, pady=10)

ctk.CTkLabel(control_frame, text="Данные:", font=("Arial", 13, "bold")).grid(row=1, column=2, sticky="w", padx=20,
                                                                             pady=10)
message_entry = ctk.CTkEntry(control_frame, width=280, placeholder_text="Введи текст сообщения или аргументы...")
message_entry.grid(row=1, column=3, columnspan=2, sticky="we", padx=5, pady=10)

# Строка 3: Кнопка отправки
send_btn = ctk.CTkButton(control_frame, text="ОТПРАВИТЬ КОМАНДУ", font=("Arial", 13, "bold"), fg_color="#2ecc71",
                         hover_color="#27ae60", text_color="white", command=send_command_from_gui)
send_btn.grid(row=2, column=0, columnspan=5, sticky="we", padx=15, pady=10)

# Панель логов
log_frame = ctk.CTkFrame(root, corner_radius=10)
log_frame.pack(fill="both", expand=True, padx=15, pady=5)

ctk.CTkLabel(log_frame, text="Логи сервера в реальном времени:", font=("Arial", 13, "bold")).pack(anchor="w", padx=15,
                                                                                                  pady=5)

log_widget = ctk.CTkTextbox(log_frame, font=("Consolas", 12), activate_scrollbars=True)
log_widget.pack(fill="both", expand=True, padx=15, pady=10)
log_widget.configure(state="disabled")

# Старт
log_to_gui("[+] Современная админ-панель успешно запущена!")
server_thread = threading.Thread(target=start_server, daemon=True)
server_thread.start()

root.mainloop()