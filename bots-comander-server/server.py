import socket
import asyncio
import json
import os
import threading

HOST = "localhost"
PORT = 5000
botlist = {}
DB_FILE = "bots_database.json"

# Фикс конекта от нейронки
def handle_bot(conn, addr):
    print(f"Бот подключился с адреса: {addr}")
    current_bot_id = None  # Храним ID текущего бота в потоке для обработки обрыва связи

    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break

            message = data.decode('utf-8').strip()
            print(f"Получено от бота: {message}")

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
                    print(f"Бот ({bot_id}) ({username}) успешно добавлен в список!")
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
                        print(f"Бот {botlist[bot_id]['username']} ({bot_id}) успешно отключен.")
                        save_botlist()
                    else:
                        print(f"Попытка отключения неизвестного bot_id: {bot_id}")
                except Exception as e:
                    print(f"Ошибка при обработке отключения: {e}")

            elif message.startswith("CHANGENIK:"):
                parts = message.split(":")
                bot_id = parts[1]
                username = parts[2]
                if bot_id in botlist:
                    botlist[bot_id]["username"] = username
                    print(f"Ник бота ({bot_id}) успешно изменён на ({username}) ")
                    save_botlist()

    except Exception as e:
        print(f"Ошибка соединения с ботом {addr}: {e}")
    finally:

        if current_bot_id and current_bot_id in botlist:
            if botlist[current_bot_id]["conn"] == conn:
                botlist[current_bot_id]["status"] = "offline"
                botlist[current_bot_id]["conn"] = None
                print(f"Связь с ботом ({current_bot_id}) потеряна.")
                save_botlist()
        conn.close()


def start_server():
    load_botlist()

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen()
    print("Сервер запущен и слушает порт...")

    # Главный цикл теперь постоянно ждет новых подключений
    while True:
        try:
            conn, addr = server_socket.accept()
            # Для каждого нового бота создаем и запускаем свой поток
            bot_thread = threading.Thread(target=handle_bot, args=(conn, addr), daemon=True)
            bot_thread.start()
        except KeyboardInterrupt:
            print("\nСервер остановлен.")
            break
        except Exception as e:
            print(f"Ошибка при приеме подключения: {e}")


def start_console():
    while True:
        promt = input("Admin : ").strip()

        if not promt:
            continue

        # Тип промта
        if promt.startswith("msg "):
            promt_type = "msg"
        elif promt.startswith("command "):
            promt_type = "command"
        elif promt.startswith("changenik "):
            promt_type = "changenik"
        elif promt.startswith("connect "):
            promt_type = "connect"
        elif promt.startswith("takeallfromah "):
            promt_type = "takeallfromah"
        elif promt.startswith("refreshah "):
            promt_type = "refreshah"
        elif promt.startswith("dropall "):
            promt_type = "dropall"
        else:

            if promt.split()[0] not in ["msg", "command", "changenik", "connect", "takeallfromah", "refreshah", "dropall"]:
                print("error: неизвестная команда. Доступны: msg, command, changenik, connect, takeallfromah, refreshah, dropall")
                continue

        # Проверки для разных промтов
        if ":" not in promt :
            print(f"error: отсутствует двоеточие ':' перед текстом для {promt_type}. Если вы используете 'takeallfromah, refreshah, dropall' просто поставьте двоеточие и дальше ничего не пишите")
            continue

        if promt_type == "command" and "/" in promt:
            print("error: Введите команду без '/'")
            continue


        command_part, clean_message = promt.split(":", 1)
        clean_message = clean_message.strip()

        cmd_args = command_part.split()


        if len(cmd_args) < 2:
            print("error: укажите флаг (-all или -single -username)")
            continue

        flag = cmd_args[1]

        if flag == "-all":
            msg_type = "all"
            username = None
        elif flag == "-single":
            msg_type = "single"
            if len(cmd_args) < 3:
                print("error: no username для флага -single")
                continue
            username = cmd_args[2].removeprefix("-")
        else:
            print(f"error: неизвестный флаг {flag}")
            continue

        data = {
            "promt": promt_type,
            "type": msg_type,
            "username": username,
            "message": clean_message
        }

        json_data = json.dumps(data, ensure_ascii=False)
        if msg_type == "all":
            for info in botlist.values():
                if (info["status"]) == "online" and info["conn"] is not None:
                    conn = info["conn"]
                    packet = json_data + "\n"
                    conn.sendall(packet.encode("utf-8"))
        elif msg_type == "single":
            for info in botlist.values():
                if (info["status"]) == "online" and info["conn"] is not None and info["username"] == username:
                    conn = info["conn"]
                    packet = json_data + "\n"
                    conn.sendall(packet.encode("utf-8"))
        print(f"[Успешно спарсено JSON]: {json_data}")


def save_botlist():
    clean_data = {}
    for bot_id, info in botlist.items():
        clean_data[bot_id] = {
            "username": info["username"],
            "status": info["status"],
        }

    with open(DB_FILE, "w", encoding="utf-8") as f:
        json.dump(clean_data, f, ensure_ascii=False, indent=4)
    print("База данных ботов успешно сохранена в файл.")


def load_botlist():
    global botlist
    # Проверяем, существует ли файл и не пустой ли он
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
            print(f"Загружено ботов из файла: {len(botlist)}")
        except Exception as e:
            print(f"Ошибка при загрузке базы данных: {e}")
            botlist = {}
    else:
        print("Файл базы данных пуст или не найден, создаем чистый список.")
        botlist = {}


def shutdown_server():
    print("\n[!] Выключаем сервер. Сбрасываем статусы ботов...")

    for info in botlist.values():
        info["conn"] = None
        info["status"] = "offline"

    save_botlist()
    print("[+] Все боты переведены в offline. База сохранена.")

if __name__ == "__main__":
    try:
        console_thread = threading.Thread(target=start_console, daemon=True)
        console_thread.start()
        print("[+] Админ-консоль успешно запущена!")

        start_server()
    except KeyboardInterrupt:
        shutdown_server()
