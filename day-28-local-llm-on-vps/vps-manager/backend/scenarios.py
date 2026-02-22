"""
VPS Wizard — определения сценариев и шагов.

Типы шагов:
  info  — информационный блок (рекомендации, инструкции), SSH не нужен
  exec  — выполнить команды на сервере через SSH

Переменные в commands подставляются из context:
  {host}, {admin_user}, {admin_pass}, {domain}, ...

Поля шага:
  skippable     — bool: можно ли пропустить шаг при ошибке
  error_hints   — list: подсказки при ошибке, каждая может содержать fix_commands
"""

# ── Переиспользуемые подсказки об ошибках ─────────────────────────────────────

_APT_LOCK_HINT = {
    "title": "Заблокирован менеджер пакетов (dpkg lock)",
    "hint": (
        "Другой процесс использует apt/dpkg (фоновое обновление, unattended-upgrades). "
        "Нажмите «Применить исправление», дождитесь завершения, затем «Повторить шаг»."
    ),
    "fix_commands": [
        "systemctl stop unattended-upgrades apt-daily.service apt-daily-upgrade.service 2>/dev/null || true",
        "rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock",
        "dpkg --configure -a",
        "apt-get install -f -y",
    ],
}

_DNS_HINT = {
    "title": "DNS-запись ещё не распространилась",
    "hint": (
        "Certbot проверяет, что домен ведёт на IP вашего сервера. "
        "Убедитесь, что A-запись настроена правильно, подождите 5–60 минут и нажмите «Повторить шаг»."
    ),
}

_CERTBOT_RATE_HINT = {
    "title": "Превышен лимит запросов Let's Encrypt",
    "hint": (
        "Let's Encrypt ограничивает число сертификатов для домена (5/неделю). "
        "Добавьте флаг --staging для теста или подождите сброса лимита."
    ),
}

_PROVIDER_FIREWALL_HINT = {
    "title": "Внешний файрволл провайдера",
    "hint": (
        "Большинство VPS-провайдеров (DigitalOcean, Hetzner, Linode, Vultr, TimeWeb и др.) "
        "имеют внешний файрволл в панели управления — он не зависит от UFW на сервере. "
        "Откройте панель управления вашего провайдера и убедитесь, что входящий UDP-трафик "
        "на порт 1194 разрешён. Также проверьте, что провайдер не блокирует UDP вообще."
    ),
}

_FAIL2BAN_SYSTEMD_HINT = {
    "title": "Fail2ban не запускается через systemd",
    "hint": (
        "Сервис может быть замаскирован или конфигурация нестандартная для этого образа. "
        "Попробуйте исправление или пропустите шаг — он не критичен для работы сервера."
    ),
    "fix_commands": [
        "systemctl unmask fail2ban || true",
        "systemctl enable fail2ban",
        "systemctl restart fail2ban || true",
        "fail2ban-client status 2>/dev/null || echo 'fail2ban started in background'",
    ],
}

COMMON_STEPS = [
    {
        "id": "info_what_is_vps",
        "name": "Что такое VPS?",
        "description": "Объяснение VPS и отличие от других видов хостинга",
        "type": "info",
        "info_text": (
            "## Что такое VPS?\n\n"
            "**VPS (Virtual Private Server)** — виртуальный выделенный сервер. "
            "Это ваша «виртуальная» машина, которая работает на физическом сервере "
            "в дата-центре провайдера. Вы получаете **полный root-доступ**, свою ОС "
            "и гарантированные ресурсы (CPU, RAM, диск).\n\n"
            "## Виды хостинга\n\n"
            "- **Shared-хостинг** (~100 ₽/мес) — тысячи сайтов на одном сервере, "
            "ресурсы не гарантированы, нет root-доступа. Только для простых сайтов.\n"
            "- **VPS** (~300–800 ₽/мес) — ваш виртуальный сервер. Гарантированные RAM/CPU, "
            "root-доступ, любая ОС. **Оптимально для большинства задач.**\n"
            "- **Выделенный сервер** (от 5 000 ₽/мес) — физическая машина целиком. "
            "Нужен при очень высоких нагрузках.\n"
            "- **Облако (AWS, GCP, Azure)** — ресурсы масштабируются автоматически. "
            "Сложнее в настройке, дороже при постоянной нагрузке.\n\n"
            "## Что можно делать на VPS?\n\n"
            "- Личный VPN (WireGuard, OpenVPN) — обходить блокировки, защищать трафик\n"
            "- Сайты и веб-приложения с доменом и SSL-сертификатом\n"
            "- Telegram/Discord боты, парсеры, скрипты автоматизации\n"
            "- Почтовый сервер, базы данных, CI/CD pipeline\n"
            "- Игровые серверы, резервное хранилище, медиасервер\n\n"
            "## Типичные характеристики базового VPS\n\n"
            "- 1 vCPU, 1–2 GB RAM, 20–40 GB SSD NVMe\n"
            "- Стоимость: €3–8/месяц (~300–800 ₽)\n"
            "- ОС: Ubuntu 22.04 LTS (рекомендуется)\n"
            "- Подключение: SSH (порт 22) из любой точки мира"
        ),
    },
    {
        "id": "info_vps_tips",
        "name": "Где купить VPS и как заказать",
        "description": "Проверенные провайдеры с ценами и пошаговый гайд",
        "type": "info",
        "info_text": (
            "## Международные провайдеры\n\n"
            "- **[Hetzner](https://hetzner.com)** — от €3.8/мес, Германия/Финляндия/США. "
            "Лучшее соотношение цена/качество в Европе, есть ARM-серверы\n"
            "- **[DigitalOcean](https://digitalocean.com)** — от $6/мес, 15 регионов, "
            "простой интерфейс, отличная документация\n"
            "- **[Vultr](https://vultr.com)** — от $6/мес, 32 региона по всему миру\n"
            "- **[Linode / Akamai](https://linode.com)** — от $5/мес, надёжный с 2003 года\n"
            "- **[OVHcloud](https://ovhcloud.com)** — от €3.5/мес, французская компания, "
            "серверы в Европе и Канаде\n"
            "- **[Contabo](https://contabo.com)** — от €4.99/мес, много RAM/диска за те же деньги\n\n"
            "## Российские провайдеры\n\n"
            "- **[Selectel](https://selectel.ru)** — от 300 ₽/мес, Москва/СПб, "
            "высокое качество и SLA\n"
            "- **[TimeWeb Cloud](https://timeweb.cloud)** — от 149 ₽/мес, "
            "удобная панель, хорошо для новичков\n"
            "- **[REG.RU](https://reg.ru)** — от 149 ₽/мес, "
            "крупнейший российский регистратор\n"
            "- **[Beget](https://beget.com)** — от 299 ₽/мес, "
            "популярен для PHP/WordPress-проектов\n"
            "- **[Aeza](https://aeza.net)** — от 99 ₽/мес, "
            "серверы в разных странах, анонимная оплата\n"
            "- **[Cloud.ru (SberCloud)](https://cloud.ru)** — от 500 ₽/мес, "
            "корпоративного класса, высокая надёжность\n\n"
            "## Как заказать VPS: пошагово\n\n"
            "1. Зарегистрируйтесь у провайдера (email + верификация)\n"
            "2. Создайте сервер: выберите регион и конфигурацию (1 vCPU / 1 GB RAM для старта)\n"
            "3. Выберите ОС: **Ubuntu 22.04 LTS** (рекомендуется)\n"
            "4. Установите пароль root или загрузите свой SSH-ключ\n"
            "5. Дождитесь запуска (обычно 30–60 секунд)\n"
            "6. Скопируйте IP-адрес и введите данные в следующем шаге мастера\n\n"
            "## Минимальные требования\n\n"
            "- ОС: **Ubuntu 22.04 LTS** или Debian 12\n"
            "- RAM: 512 MB для VPN, 1 GB для сайта или приложения\n"
            "- Диск: 10 GB SSD\n"
            "- SSH-доступ по паролю (root) или SSH-ключу"
        ),
    },
    {
        "id": "sys_update",
        "name": "Обновление пакетов",
        "description": "apt update && apt upgrade — обновляем систему",
        "type": "exec",
        "error_hints": [_APT_LOCK_HINT],
        "commands": [
            "export DEBIAN_FRONTEND=noninteractive",
            "apt-get update -y",
            "apt-get upgrade -y",
            "apt-get install -y curl wget sudo ufw",
        ],
    },
    {
        "id": "create_user",
        "name": "Создание sudo-пользователя",
        "description": "Новый пользователь вместо root для повседневной работы",
        "type": "exec",
        "inputs": [
            {
                "key": "admin_user",
                "label": "Имя пользователя",
                "type": "text",
                "placeholder": "admin",
                "default": "admin",
            },
            {
                "key": "admin_pass",
                "label": "Пароль",
                "type": "password",
                "placeholder": "Сложный пароль (мин. 12 символов)",
            },
        ],
        "commands": [
            "id -u {admin_user} 2>/dev/null && echo 'User exists, skipping' || adduser --gecos '' --disabled-password {admin_user}",
            "echo '{admin_user}:{admin_pass}' | chpasswd",
            "usermod -aG sudo {admin_user}",
            "echo '{admin_user} ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/{admin_user}",
            "chmod 0440 /etc/sudoers.d/{admin_user}",
            "echo 'User created: ' && id {admin_user}",
        ],
    },
    {
        "id": "setup_ufw",
        "name": "Файрволл UFW",
        "description": "Базовые правила: запрещаем всё входящее, открываем SSH",
        "type": "exec",
        "skippable": True,
        "commands": [
            "ufw --force reset",
            "ufw default deny incoming",
            "ufw default allow outgoing",
            "ufw allow 22/tcp comment 'SSH'",
            "ufw --force enable",
            "ufw status verbose",
        ],
    },
    {
        "id": "open_ports",
        "name": "Открытие портов в UFW",
        "description": "Выберите порты для входящих соединений (SSH уже открыт)",
        "type": "exec",
        "skippable": True,
        "inputs": [
            {
                "key": "port_web",
                "label": "HTTP (80) + HTTPS (443) — для веб-приложений",
                "type": "select",
                "options": ["нет", "да"],
                "default": "нет",
            },
            {
                "key": "port_openvpn",
                "label": "OpenVPN UDP 1194 — для VPN-сервера",
                "type": "select",
                "options": ["нет", "да"],
                "default": "нет",
            },
            {
                "key": "port_custom",
                "label": "Дополнительные порты через запятую (например: 8080/tcp, 11434/tcp)",
                "type": "text",
                "placeholder": "8080/tcp, 11434/tcp",
                "default": "",
                "optional": True,
            },
        ],
        "commands": [
            """if [ '{port_web}' = 'да' ]; then
  ufw allow 80/tcp comment 'HTTP'
  ufw allow 443/tcp comment 'HTTPS'
  echo '→ Порты 80/TCP и 443/TCP (HTTP/HTTPS) открыты'
else
  echo '→ HTTP/HTTPS — пропущено'
fi""",
            """if [ '{port_openvpn}' = 'да' ]; then
  ufw allow 1194/udp comment 'OpenVPN'
  echo '→ Порт 1194/UDP (OpenVPN) открыт'
else
  echo '→ OpenVPN — пропущено'
fi""",
            r"""if [ -n '{port_custom}' ]; then
  echo '{port_custom}' | tr ',' '\n' | while IFS= read -r p; do
    p=$(echo "$p" | xargs)
    [ -n "$p" ] && ufw allow $p comment 'Custom' && echo "→ Порт $p открыт"
  done
fi""",
            "ufw status verbose",
        ],
    },
    {
        "id": "setup_fail2ban",
        "name": "Fail2ban",
        "description": "Автоматическая блокировка IP при попытках брутфорса",
        "type": "exec",
        "skippable": True,
        "error_hints": [_APT_LOCK_HINT, _FAIL2BAN_SYSTEMD_HINT],
        "commands": [
            "apt-get install -y fail2ban",
            "systemctl enable fail2ban",
            "systemctl restart fail2ban",
            "fail2ban-client status",
        ],
    },
    {
        "id": "info_ssh_keys",
        "name": "SSH-ключи (рекомендуется)",
        "description": "Инструкция по настройке входа без пароля",
        "type": "info",
        "info_text": (
            "После завершения настройки рекомендуем отключить вход по паролю.\n\n"
            "**На вашем компьютере:**\n"
            "```\n"
            "# Генерация ключа (если ещё нет)\n"
            "ssh-keygen -t ed25519 -C \"your@email.com\"\n\n"
            "# Копирование на сервер\n"
            "ssh-copy-id {admin_user}@{host}\n"
            "```\n\n"
            "Затем в файле `/etc/ssh/sshd_config` установите:\n"
            "```\n"
            "PasswordAuthentication no\n"
            "PermitRootLogin no\n"
            "```\n"
            "И перезапустите: `systemctl restart sshd`"
        ),
    },
]

OPENVPN_STEPS = [
    {
        "id": "ovpn_install",
        "name": "Установка OpenVPN + Easy-RSA",
        "description": "Устанавливаем OpenVPN и инструменты для управления сертификатами",
        "type": "exec",
        "error_hints": [_APT_LOCK_HINT],
        "commands": [
            "apt-get install -y openvpn easy-rsa iptables",
            "openvpn --version | head -1",
        ],
    },
    {
        "id": "ovpn_pki",
        "name": "Инициализация PKI и создание CA",
        "description": "Создаём инфраструктуру открытых ключей и корневой центр сертификации",
        "type": "exec",
        "commands": [
            """[ -d /etc/openvpn/easy-rsa ] || make-cadir /etc/openvpn/easy-rsa
cd /etc/openvpn/easy-rsa
export EASYRSA_BATCH=yes
export EASYRSA_REQ_CN="VPS-CA"
./easyrsa init-pki
./easyrsa build-ca nopass
echo "CA создан: $(cat pki/ca.crt | openssl x509 -noout -subject)"
""",
        ],
    },
    {
        "id": "ovpn_server_cert",
        "name": "Сертификат сервера + DH + TLS-ключ",
        "description": "Генерация займёт 1–2 минуты из-за параметров Диффи-Хеллмана",
        "type": "exec",
        "commands": [
            """cd /etc/openvpn/easy-rsa
export EASYRSA_BATCH=yes
./easyrsa gen-req server nopass
./easyrsa sign-req server server
./easyrsa gen-dh
openvpn --genkey secret /etc/openvpn/ta.key
echo "Сертификаты и DH готовы"
""",
        ],
    },
    {
        "id": "ovpn_server_config",
        "name": "Конфигурация OpenVPN сервера",
        "description": "Создаём server.conf и копируем нужные файлы",
        "type": "exec",
        "commands": [
            """mkdir -p /etc/openvpn/client
cp /etc/openvpn/easy-rsa/pki/ca.crt /etc/openvpn/
cp /etc/openvpn/easy-rsa/pki/issued/server.crt /etc/openvpn/
cp /etc/openvpn/easy-rsa/pki/private/server.key /etc/openvpn/
cp /etc/openvpn/easy-rsa/pki/dh.pem /etc/openvpn/

cat > /etc/openvpn/server.conf << 'OVPNEOF'
port 1194
proto udp
dev tun
ca /etc/openvpn/ca.crt
cert /etc/openvpn/server.crt
key /etc/openvpn/server.key
dh /etc/openvpn/dh.pem
tls-auth /etc/openvpn/ta.key 0
server 10.9.0.0 255.255.255.0
push "redirect-gateway def1 bypass-dhcp"
push "dhcp-option DNS 1.1.1.1"
push "dhcp-option DNS 8.8.8.8"
keepalive 10 120
data-ciphers AES-256-GCM:AES-128-GCM
cipher AES-256-GCM
user nobody
group nogroup
persist-key
persist-tun
status /var/log/openvpn-status.log
log-append /var/log/openvpn.log
verb 3
OVPNEOF
echo "server.conf создан"
""",
        ],
    },
    {
        "id": "ovpn_ip_forward",
        "name": "IP Forwarding + NAT (persistent)",
        "description": "Включаем маршрутизацию и настраиваем персистентный NAT через UFW",
        "type": "exec",
        "commands": [
            """echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-openvpn.conf
sysctl -p /etc/sysctl.d/99-openvpn.conf

# UFW по умолчанию блокирует FORWARD — разрешаем
sed -i 's/DEFAULT_FORWARD_POLICY="DROP"/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw

# Добавляем NAT-правило в /etc/ufw/before.rules (персистентно, переживает ребут)
IFACE=$(ip route show default | awk '/default/ {print $5}' | head -1)
if ! grep -q 'POSTROUTING.*10.9.0.0' /etc/ufw/before.rules 2>/dev/null; then
  (printf '*nat\\n:POSTROUTING ACCEPT [0:0]\\n-A POSTROUTING -s 10.9.0.0/24 -o %s -j MASQUERADE\\nCOMMIT\\n\\n' "$IFACE"; cat /etc/ufw/before.rules) > /tmp/ufw_before.tmp
  mv /tmp/ufw_before.tmp /etc/ufw/before.rules
fi

ufw reload
echo "IP forwarding включён, NAT настроен на интерфейс $IFACE (persistent)"
""",
        ],
    },
    {
        "id": "ovpn_firewall_start",
        "name": "Файрволл + запуск OpenVPN",
        "description": "Открываем UDP 1194 и запускаем сервис",
        "type": "exec",
        "error_hints": [_PROVIDER_FIREWALL_HINT],
        "commands": [
            "ufw allow 1194/udp comment 'OpenVPN'",
            "ufw status",
            "systemctl enable openvpn@server",
            "systemctl start openvpn@server",
            "sleep 2",
            "systemctl is-active openvpn@server && echo 'OpenVPN запущен!' || (journalctl -u openvpn@server --no-pager -n 20; exit 1)",
        ],
    },
    {
        "id": "ovpn_debug",
        "name": "Диагностика OpenVPN",
        "description": "Проверяем статус сервиса, порт и файрволл — запустите если клиент не подключается",
        "type": "exec",
        "skippable": True,
        "error_hints": [_PROVIDER_FIREWALL_HINT],
        "commands": [
            """echo "=== Статус сервиса OpenVPN ==="
systemctl status openvpn@server --no-pager -l || true""",
            """echo ""
echo "=== Последние логи (30 строк) ==="
journalctl -u openvpn@server --no-pager -n 30 || true""",
            """echo ""
echo "=== Прослушиваемые UDP-порты ==="
ss -ulnp | grep -E '1194|LISTEN' || echo "Порт 1194 не найден в ss — возможно, OpenVPN не запущен"
echo ""
echo "=== Файрволл UFW ==="
ufw status verbose""",
            """echo ""
echo "=== Сетевой интерфейс tun0 ==="
ip a show tun0 2>/dev/null && echo "tun0 активен" || echo "tun0 не найден (клиент ещё не подключён — это нормально)"
echo ""
echo "=== Маршруты (ip route) ==="
ip route show | head -10""",
            r"""echo ""
echo "=== Проверка client1.ovpn ==="
if [ -f /etc/openvpn/client/client1.ovpn ]; then
  SIZE=$(wc -c < /etc/openvpn/client/client1.ovpn)
  REMOTE=$(grep '^remote ' /etc/openvpn/client/client1.ovpn)
  echo "Файл существует: $SIZE байт"
  echo "Строка remote: $REMOTE"
  echo "Секции: $(grep -c '<.*>' /etc/openvpn/client/client1.ovpn || echo 0) тегов"
else
  echo "ОШИБКА: /etc/openvpn/client/client1.ovpn не найден!"
fi""",
        ],
    },
    {
        "id": "ovpn_client_cert",
        "name": "Сертификат клиента",
        "description": "Создаём пару ключей для первого клиента (client1)",
        "type": "exec",
        "commands": [
            """cd /etc/openvpn/easy-rsa
export EASYRSA_BATCH=yes
./easyrsa gen-req client1 nopass
./easyrsa sign-req client client1
echo "Клиентский сертификат подписан"
""",
        ],
    },
    {
        "id": "ovpn_client_ovpn",
        "name": "Сборка client1.ovpn",
        "description": "Собираем inline-конфиг с встроенными сертификатами",
        "type": "exec",
        "commands": [
            # Используем heredoc без кавычек, чтобы bash раскрыл переменные $CA и т.д.
            # {host} подставляется Python-ом до отправки на сервер
            r"""CA=$(cat /etc/openvpn/ca.crt)
CERT=$(openssl x509 -in /etc/openvpn/easy-rsa/pki/issued/client1.crt)
KEY=$(cat /etc/openvpn/easy-rsa/pki/private/client1.key)
TA=$(cat /etc/openvpn/ta.key)

cat > /etc/openvpn/client/client1.ovpn << OVPN
client
dev tun
proto udp
remote """ + "{host}" + r""" 1194
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
data-ciphers AES-256-GCM:AES-128-GCM
cipher AES-256-GCM
verb 3
key-direction 1
<ca>
$CA
</ca>
<cert>
$CERT
</cert>
<key>
$KEY
</key>
<tls-auth>
$TA
</tls-auth>
OVPN

chmod 600 /etc/openvpn/client/client1.ovpn
echo "=== client1.ovpn готов ($(wc -c < /etc/openvpn/client/client1.ovpn) байт) ==="
""",
        ],
    },
    {
        "id": "ovpn_download",
        "name": "Скачать client1.ovpn",
        "description": "Загрузите файл конфигурации на ваш компьютер",
        "type": "download",
        "download_file": "/etc/openvpn/client/client1.ovpn",
        "download_name": "client1.ovpn",
    },
    {
        "id": "ovpn_done",
        "name": "OpenVPN готов!",
        "description": "Инструкции по подключению",
        "type": "info",
        "info_text": (
            "**Ваш VPN-сервер OpenVPN запущен!**\n\n"
            "**Скачайте клиент:**\n"
            "- Windows: openvpn.net/community-downloads\n"
            "- macOS: Tunnelblick (tunnelblick.net)\n"
            "- iOS / Android: OpenVPN Connect из App Store / Play Store\n"
            "- Linux: `apt install openvpn`\n\n"
            "**Подключение:**\n"
            "1. Скачайте файл `client1.ovpn` (кнопка выше)\n"
            "2. Импортируйте его в клиент через «Файл» → «Импорт профиля»\n"
            "3. Нажмите «Подключить»\n\n"
            "**Добавить ещё клиента** (на сервере):\n"
            "```bash\n"
            "cd /etc/openvpn/easy-rsa\n"
            "export EASYRSA_BATCH=yes\n"
            "./easyrsa gen-req client2 nopass\n"
            "./easyrsa sign-req client client2\n"
            "# Затем повторить сборку .ovpn-файла\n"
            "```"
        ),
    },
]

PYTHON_WEBAPP_STEPS = [
    {
        "id": "webapp_python",
        "name": "Установка Python 3",
        "description": "Python, venv и создание директории приложения",
        "type": "exec",
        "error_hints": [_APT_LOCK_HINT],
        "inputs": [
            {
                "key": "app_dir",
                "label": "Путь к приложению на сервере",
                "type": "text",
                "placeholder": "/opt/app",
                "default": "/opt/app",
            },
            {
                "key": "app_service_name",
                "label": "Имя сервиса (только латиница, без пробелов)",
                "type": "text",
                "placeholder": "webapp",
                "default": "webapp",
            },
        ],
        "commands": [
            "apt-get install -y python3 python3-venv python3-pip",
            "python3 --version",
            "mkdir -p {app_dir}",
            "chown -R {admin_user}:{admin_user} {app_dir}",
            "echo 'Python установлен, директория готова: {app_dir}'",
        ],
    },
    {
        "id": "webapp_nginx",
        "name": "Nginx reverse proxy",
        "description": "Nginx принимает запросы и передаёт в Python-приложение",
        "type": "exec",
        "error_hints": [_APT_LOCK_HINT],
        "inputs": [
            {
                "key": "domain",
                "label": "Доменное имя",
                "type": "text",
                "placeholder": "app.example.com",
            },
            {
                "key": "app_port",
                "label": "Порт Python-приложения",
                "type": "text",
                "placeholder": "8000",
                "default": "8000",
            },
        ],
        "commands": [
            "apt-get install -y nginx",
            "systemctl enable nginx",
            """cat > /etc/nginx/sites-available/{app_service_name} << 'NGINXEOF'
server {
    listen 80;
    server_name {domain};
    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:{app_port};
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_cache_bypass $http_upgrade;
        proxy_buffering off;
        proxy_read_timeout 300s;
    }
}
NGINXEOF""",
            "ln -sf /etc/nginx/sites-available/{app_service_name} /etc/nginx/sites-enabled/{app_service_name}",
            "rm -f /etc/nginx/sites-enabled/default",
            "nginx -t && systemctl start nginx",
            "echo 'Nginx настроен'",
        ],
    },
    {
        "id": "webapp_ports",
        "name": "Открываем HTTP/HTTPS порты",
        "description": "Разрешаем 80 и 443 в файрволле",
        "type": "exec",
        "commands": [
            "ufw allow 80/tcp comment 'HTTP'",
            "ufw allow 443/tcp comment 'HTTPS'",
            "ufw status",
        ],
    },
    {
        "id": "webapp_ssl",
        "name": "SSL-сертификат (Let's Encrypt)",
        "description": "Бесплатный HTTPS через Certbot",
        "type": "exec",
        "skippable": True,
        "error_hints": [_DNS_HINT, _CERTBOT_RATE_HINT, _APT_LOCK_HINT],
        "inputs": [
            {
                "key": "admin_email",
                "label": "Email для уведомлений от Let's Encrypt",
                "type": "text",
                "placeholder": "you@example.com",
            },
        ],
        "commands": [
            "apt-get install -y certbot python3-certbot-nginx",
            "certbot --nginx -d {domain} --non-interactive --agree-tos --email {admin_email} --redirect",
            "echo 'SSL настроен. Автообновление включено.'",
        ],
    },
    {
        "id": "webapp_systemd",
        "name": "Systemd-сервис",
        "description": "Автозапуск и мониторинг приложения через systemd",
        "type": "exec",
        "commands": [
            """cat > /etc/systemd/system/{app_service_name}.service << 'SVCEOF'
[Unit]
Description=Python Web App ({app_service_name})
After=network.target

[Service]
Type=simple
User={admin_user}
WorkingDirectory={app_dir}
ExecStart={app_dir}/.venv/bin/python main.py
Restart=on-failure
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
SVCEOF""",
            "systemctl daemon-reload",
            "systemctl enable {app_service_name}",
            "echo 'Systemd-сервис зарегистрирован: {app_service_name}'",
        ],
    },
    {
        "id": "webapp_upload_info",
        "name": "Загрузка файлов приложения",
        "description": "Инструкция по копированию кода на сервер",
        "type": "info",
        "info_text": (
            "Инфраструктура готова. Загрузите файлы вашего приложения на сервер:\n\n"
            "```bash\n"
            "# Загрузить код приложения\n"
            "rsync -avz ./myapp/ {admin_user}@{host}:{app_dir}/\n"
            "```\n\n"
            "Создайте виртуальное окружение и установите зависимости:\n"
            "```bash\n"
            "ssh {admin_user}@{host}\n"
            "cd {app_dir}\n"
            "python3 -m venv .venv\n"
            ".venv/bin/pip install -r requirements.txt\n"
            "```\n\n"
            "**Отредактируйте ExecStart** в `/etc/systemd/system/{app_service_name}.service` "
            "под команду запуска вашего приложения (uvicorn, gunicorn, python main.py и т.д.), "
            "затем выполните `sudo systemctl daemon-reload`.\n\n"
            "**Запустите сервис:**\n"
            "```bash\n"
            "sudo systemctl start {app_service_name}\n"
            "sudo systemctl status {app_service_name}\n"
            "```"
        ),
    },
    {
        "id": "webapp_done",
        "name": "VPS готов!",
        "description": "Итоговые инструкции по управлению сервисом",
        "type": "info",
        "info_text": (
            "**VPS подготовлен для Python-приложения!**\n\n"
            "После загрузки файлов и запуска сервиса приложение будет доступно по адресу:\n"
            "**https://{domain}**\n\n"
            "**Управление сервисом:**\n"
            "```bash\n"
            "sudo systemctl status {app_service_name}   # статус\n"
            "sudo systemctl restart {app_service_name}  # перезапуск\n"
            "journalctl -u {app_service_name} -f        # логи в реальном времени\n"
            "```\n\n"
            "**Обновить приложение:**\n"
            "```bash\n"
            "rsync -avz ./myapp/ {admin_user}@{host}:{app_dir}/\n"
            "ssh {admin_user}@{host} 'sudo systemctl restart {app_service_name}'\n"
            "```"
        ),
    },
]


LOCAL_LLM_STEPS = [
    {
        "id": "info_ollama_requirements",
        "name": "Требования к серверу",
        "description": "Минимальные характеристики VPS для запуска LLM",
        "type": "info",
        "info_text": (
            "## Ollama + три модели на VPS\n\n"
            "**Ollama** — инструмент для запуска LLM-моделей локально. "
            "Поддерживает десятки открытых моделей (Qwen, Llama, Mistral, Gemma и др.).\n\n"
            "Мастер установит **три модели**: основную (качество), "
            "быструю (скорость) и сверхбыструю (максимальная скорость). "
            "В JuriLytics каждый пользователь сможет выбрать нужную.\n\n"
            "## Минимальные требования\n\n"
            "- **RAM**: минимум **8 GB** (рекомендуется 16 GB)\n"
            "- **Диск**: минимум **12 GB** свободного места\n"
            "- **CPU**: любой современный x86-64 (GPU не требуется)\n\n"
            "## Рекомендуемые наборы моделей\n\n"
            "| RAM на VPS | Основная | Быстрая | Сверхбыстрая |\n"
            "|------------|---------|---------|-------------|\n"
            "| 8 GB | `qwen2.5:7b` (~4.7 GB) | `qwen2.5:3b` (~2 GB) | `qwen2.5:1.5b` (~1 GB) |\n"
            "| 16 GB | `qwen2.5:14b` (~9 GB) | `qwen2.5:7b` (~4.7 GB) | `qwen2.5:3b` (~2 GB) |\n"
            "| 4 GB | `qwen2.5:3b` (~2 GB) | `qwen2.5:1.5b` (~1 GB) | `qwen2.5:1.5b` (~1 GB) |\n\n"
            "> Все три модели хранятся на диске (~7.7 GB суммарно для набора 8 GB), "
            "но в RAM одновременно держится только та, с которой работает запрос."
        ),
    },
    {
        "id": "ollama_install",
        "name": "Установка Ollama",
        "description": "Официальный скрипт: создаёт пользователя, systemd-сервис и запускает Ollama",
        "type": "exec",
        "error_hints": [_APT_LOCK_HINT],
        "commands": [
            "apt-get install -y curl",
            "curl -fsSL https://ollama.com/install.sh | sh",
            "sleep 3",
            "systemctl is-active ollama && echo 'Ollama сервис запущен!' "
            "|| (journalctl -u ollama -n 20 --no-pager; exit 1)",
            "ollama --version",
        ],
    },
    {
        "id": "ollama_pull",
        "name": "Загрузка моделей",
        "description": "Скачать три модели: основную, быструю и сверхбыструю. Займёт несколько минут.",
        "type": "exec",
        "inputs": [
            {
                "key": "ollama_model",
                "label": "Основная модель (качество)",
                "type": "text",
                "placeholder": "qwen2.5:7b",
                "default": "qwen2.5:7b",
            },
            {
                "key": "ollama_model2",
                "label": "Быстрая модель (скорость)",
                "type": "text",
                "placeholder": "qwen2.5:3b",
                "default": "qwen2.5:3b",
            },
            {
                "key": "ollama_model3",
                "label": "Сверхбыстрая модель (максимальная скорость)",
                "type": "text",
                "placeholder": "qwen2.5:1.5b",
                "default": "qwen2.5:1.5b",
            },
        ],
        "commands": [
            "echo '=== Загружаем основную модель: {ollama_model} ==='",
            "ollama pull {ollama_model}",
            """if [ -n '{ollama_model2}' ] && [ '{ollama_model2}' != '{ollama_model}' ]; then
  echo ''
  echo '=== Загружаем быструю модель: {ollama_model2} ==='
  ollama pull {ollama_model2}
else
  echo '(вторая модель не указана или совпадает с первой — пропускаем)'
fi""",
            """if [ -n '{ollama_model3}' ] && [ '{ollama_model3}' != '{ollama_model}' ] && [ '{ollama_model3}' != '{ollama_model2}' ]; then
  echo ''
  echo '=== Загружаем сверхбыструю модель: {ollama_model3} ==='
  ollama pull {ollama_model3}
else
  echo '(третья модель не указана или совпадает с одной из предыдущих — пропускаем)'
fi""",
            "echo ''",
            "echo 'Загруженные модели:'",
            "ollama list",
        ],
    },
    {
        "id": "ollama_test",
        "name": "Тест модели",
        "description": "Быстрая проверка: задать простой вопрос модели через REST API",
        "type": "exec",
        "skippable": True,
        "commands": [
            r"""echo 'Отправляем тестовый запрос к {ollama_model}...'
curl -s http://localhost:11434/api/generate \
  -H 'Content-Type: application/json' \
  -d '{"model":"{ollama_model}","prompt":"2+2=? Ответь только числом.","stream":false}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('Ответ модели:', d.get('response','').strip())"
echo 'Тест прошёл успешно!'""",
        ],
    },
    {
        "id": "ollama_remote_access",
        "name": "Внешний доступ (опционально)",
        "description": "Открыть порт 11434, если web-приложение работает на другом сервере",
        "type": "exec",
        "skippable": True,
        "inputs": [
            {
                "key": "ollama_remote",
                "label": "Разрешить внешний доступ к Ollama API (порт 11434)?",
                "type": "select",
                "options": ["нет (только localhost)", "да (открыть порт)"],
                "default": "нет (только localhost)",
            }
        ],
        "commands": [
            """if [ '{ollama_remote}' = 'да (открыть порт)' ]; then
  mkdir -p /etc/systemd/system/ollama.service.d
  printf '[Service]\\nEnvironment="OLLAMA_HOST=0.0.0.0:11434"\\n' \
    > /etc/systemd/system/ollama.service.d/override.conf
  systemctl daemon-reload
  systemctl restart ollama
  sleep 2
  ufw allow 11434/tcp comment 'Ollama API'
  ufw status
  echo '→ Ollama слушает на 0.0.0.0:11434, порт 11434 открыт в UFW'
else
  echo '→ Ollama работает только на localhost:11434 (безопаснее)'
fi""",
        ],
    },
    {
        "id": "ollama_done",
        "name": "Ollama готова!",
        "description": "Инструкции по использованию и подключению к JuriLytics",
        "type": "info",
        "info_text": (
            "**Ollama установлена, модели загружены!**\n\n"
            "**Управление сервисом:**\n"
            "```bash\n"
            "systemctl status ollama       # статус\n"
            "systemctl restart ollama      # перезапуск\n"
            "journalctl -u ollama -f       # логи в реальном времени\n"
            "```\n\n"
            "**Работа с моделями:**\n"
            "```bash\n"
            "ollama list                   # список загруженных моделей\n"
            "ollama pull <имя>             # загрузить модель\n"
            "ollama run <имя>              # интерактивный чат\n"
            "ollama rm <имя>               # удалить модель\n"
            "```\n\n"
            "**REST API (OpenAI-совместимый, порт 11434):**\n"
            "```bash\n"
            "# Основная модель (качество)\n"
            "curl http://localhost:11434/v1/chat/completions \\\\\n"
            "  -H 'Content-Type: application/json' \\\\\n"
            "  -d '{\"model\": \"{ollama_model}\", \"messages\": "
            "[{\"role\": \"user\", \"content\": \"Привет!\"}]}'\n"
            "```\n\n"
            "**Подключение JuriLytics:**\n"
            "Если web-app на том же сервере — укажите в `.env`:\n"
            "```\n"
            "OLLAMA_BASE_URL=http://localhost:11434\n"
            "OLLAMA_MODEL={ollama_model}\n"
            "```\n"
            "Если на другом сервере — замените `localhost` на IP этого VPS "
            "(и убедитесь, что порт 11434 открыт).\n\n"
            "**В панели администратора JuriLytics** откройте «Настройки Ollama» "
            "и укажите нужные модели (`{ollama_model}`, `{ollama_model2}`, `{ollama_model3}`). "
            "Каждый пользователь может выбирать модель самостоятельно в выпадающем списке."
        ),
    },
]


SCENARIOS = {
    "local_llm": {
        "name": "Локальная LLM",
        "subtitle": "Ollama + три модели",
        "description": "Установка Ollama и загрузка трёх моделей (основная + быстрая + сверхбыстрая) для JuriLytics без облачных API",
        "icon": "cpu",
        "steps": COMMON_STEPS + LOCAL_LLM_STEPS,
    },
    "openvpn": {
        "name": "VPN-сервер",
        "subtitle": "OpenVPN",
        "description": "Классический надёжный VPN с .ovpn-файлом для всех платформ",
        "icon": "shield",
        "steps": COMMON_STEPS + OPENVPN_STEPS,
    },
    "python_webapp": {
        "name": "Python веб-приложение",
        "subtitle": "Python + Nginx + SSL",
        "description": "Подготовка VPS для развёртывания Python-приложения (FastAPI, Flask, Django) с Nginx и HTTPS",
        "icon": "server",
        "steps": COMMON_STEPS + PYTHON_WEBAPP_STEPS,
    },
}
