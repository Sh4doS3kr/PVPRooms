# PvPRooms — API Reference

Base URL: `http://TU_IP:27090`

---

## 🔓 Pública (sin autenticación)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/top` | Top 100 jugadores (puntuación total) |
| `GET` | `/api/top/{kit}` | Top 100 para un kit específico |
| `GET` | `/api/kits` | Lista de todos los kits disponibles |
| `GET` | `/api/player/{nombre}` | Stats completos de un jugador |
| `GET` | `/api/stats` | Estado del servidor (online, duelos activos, cola) |

---

## 🤖 Discord Bot

> Header requerido: `X-Api-Key: <valor de web-api.discord-api-key en config.yml>`

### Tiers

| Método | Ruta | Body JSON | Descripción |
|--------|------|-----------|-------------|
| `POST` | `/api/discord/settier` | `{ "player": "Steve", "tier": "LT3", "kit": "sword" }` | Asigna un tier manualmente. Notifica al jugador en juego si está online. |
| `POST` | `/api/discord/notify` | `{ "player": "Steve", "message": "Hola" }` | Envía un mensaje en juego al jugador desde el bot. |
| `GET` | `/api/discord/player/{nombre}` | — | Devuelve ELO, best tier y puntos por kit del jugador. |

**Tiers válidos:** `UNRANKED · LT5 · HT5 · LT4 · HT4 · LT3 · HT3 · LT2 · HT2 · LT1 · HT1`

### Vinculación Discord ↔ Minecraft

Flujo recomendado:
1. El jugador pide vincular en Discord → bot llama `send-link-code`
2. El jugador recibe el código en juego y se lo dice al bot
3. El bot llama `confirm-link` con el código → vínculo confirmado

| Método | Ruta | Body JSON | Descripción |
|--------|------|-----------|-------------|
| `POST` | `/api/discord/send-link-code` | `{ "player": "Steve", "discordId": "123456789", "discordUsername": "steve" }` | Genera código de 6 dígitos y lo manda al jugador en juego. Válido 5 min. Devuelve `{ "code": "...", "online": true/false }` |
| `POST` | `/api/discord/confirm-link` | `{ "code": "123456", "discordId": "123456789" }` | Confirma el vínculo. Notifica al jugador en juego. Devuelve `{ "player": "...", "uuid": "..." }` |
| `GET` | `/api/discord/link-status/{discordId}` | — | Comprueba si un Discord ID ya está vinculado. Devuelve `{ "linked": true/false, "player": "...", "online": ... }` |

---

## 🌐 Web — Usuarios (autenticación por cookie de sesión)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Registro de cuenta |
| `POST` | `/api/auth/login` | Inicio de sesión |
| `POST` | `/api/auth/logout` | Cerrar sesión |
| `GET` | `/api/auth/session` | Comprobar sesión activa |
| `POST` | `/api/tickets/create` | Crear ticket de tier test |
| `GET` | `/api/tickets/list` | Listar tickets del usuario |
| `GET` | `/api/tickets/view` | Ver un ticket concreto |
| `POST` | `/api/tickets/message` | Enviar mensaje en un ticket |
| `POST` | `/api/tickets/claim` | Reclamar ticket (solo testers) |
| `POST` | `/api/tickets/status` | Cambiar estado del ticket |
| `POST` | `/api/tickets/schedule` | Programar sesión de test |
| `POST` | `/api/tickets/result` | Registrar resultado del test |
| `GET` | `/api/slots` | Ver slots de horario disponibles |

---

## 🛡️ Admin (sesión con rol admin)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/admin/users` | Listar usuarios registrados |
| `GET` | `/api/admin/logs` | Ver logs de acciones |
| `POST` | `/api/admin/user` | Acciones sobre un usuario (`delete`, `reset_sessions`, `set_tester`) |
