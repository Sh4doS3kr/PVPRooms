# 🎮 NPCs y Hologramas — PvPRoomsPro

Sistema completo de NPCs interactivos y hologramas para tu servidor.
Todos los elementos se colocan **en tu posición actual** al ejecutar el comando.

---

## 📋 Índice

1. [Comandos de NPCs](#-comandos-de-npcs)
2. [Comandos de Hologramas](#-comandos-de-hologramas)
3. [Comandos de Administración](#-comandos-de-administración)
4. [Ejemplos de Uso](#-ejemplos-de-uso)

---

## 🧑 Comandos de NPCs

### Crear NPCs

| Comando | Descripción |
|---------|-------------|
| `/npc create queue` | NPC que abre el menú de cola ranked |
| `/npc create queue <kit>` | NPC que abre cola directa para un kit específico |
| `/npc create instant` | NPC para partida instantánea (primer oponente disponible) |
| `/npc create instant <kit>` | Partida instantánea con kit específico |
| `/npc create unranked` | NPC que abre menú de partidas no ranked |
| `/npc create ffa` | NPC para entrar a FFA (Free For All) |
| `/npc create stats` | NPC que muestra tus estadísticas al hacer clic |
| `/npc create leaderboard` | NPC que abre menú de rankings |
| `/npc create kits` | NPC que abre selector de kits |
| `/npc create shop` | NPC que abre tienda de cosméticos/trims |
| `/npc create info` | NPC que muestra información del servidor |
| `/npc create events` | NPC que muestra eventos activos |
| `/npc create tournaments` | NPC para torneos |
| `/npc create practice` | NPC para modo práctica (sin ELO) |
| `/npc create spectate` | NPC para ver partidas en curso |
| `/npc create party` | NPC para crear/gestionar parties |
| `/npc create 1v1` | NPC para duelos 1v1 rápidos |
| `/npc create 2v2` | NPC para duelos 2v2 |
| `/npc create bo3` | NPC para Best of 3 |
| `/npc create bo5` | NPC para Best of 5 |

### Personalizar NPCs

| Comando | Descripción |
|---------|-------------|
| `/npc skin <id> <nombre>` | Cambiar skin del NPC |
| `/npc name <id> <nombre...>` | Cambiar nombre del NPC (soporta colores &) |
| `/npc move <id>` | Mover NPC a tu posición actual |
| `/npc look <id>` | NPC mira hacia donde estás mirando |
| `/npc lookplayer <id> <true/false>` | NPC sigue con la mirada a jugadores cercanos |

---

## 📊 Comandos de Hologramas

### Hologramas de Leaderboard

| Comando | Descripción |
|---------|-------------|
| `/holo create top` | Top 10 general (puntos totales) |
| `/holo create top <kit>` | Top 10 de un kit específico |
| `/holo create top elo` | Top 10 por ELO |
| `/holo create top wins` | Top 10 por victorias |
| `/holo create top streak` | Top 10 por racha actual |
| `/holo create top kdr` | Top 10 por K/D ratio |

### Hologramas de Estadísticas en Vivo

| Comando | Descripción |
|---------|-------------|
| `/holo create stats online` | Jugadores online en tiempo real |
| `/holo create stats duels` | Duelos activos ahora |
| `/holo create stats queue` | Jugadores en cola |
| `/holo create stats today` | Estadísticas del día |
| `/holo create stats week` | Estadísticas de la semana |

### Hologramas de Información

| Comando | Descripción |
|---------|-------------|
| `/holo create info welcome` | Mensaje de bienvenida |
| `/holo create info rules` | Reglas del servidor |
| `/holo create info ranks` | Explicación de rangos/tiers |
| `/holo create info kits` | Lista de kits disponibles |
| `/holo create info commands` | Comandos útiles para jugadores |
| `/holo create info rewards` | Recompensas por subir de tier |
| `/holo create info elo` | Explicación del sistema ELO |
| `/holo create info seasons` | Info sobre temporadas |

### Hologramas de Eventos

| Comando | Descripción |
|---------|-------------|
| `/holo create event next` | Próximo evento programado |
| `/holo create event active` | Evento activo ahora |
| `/holo create event winners` | Ganadores del último evento |

### Hologramas Personalizados

| Comando | Descripción |
|---------|-------------|
| `/holo create custom <líneas...>` | Holograma con texto personalizado |
| `/holo addline <id> <texto>` | Añadir línea al holograma |
| `/holo setline <id> <num> <texto>` | Editar línea específica |
| `/holo delline <id> <num>` | Eliminar línea |
| `/holo move <id>` | Mover holograma a tu posición |

---

## ⚙ Comandos de Administración

### Gestión General

| Comando | Descripción |
|---------|-------------|
| `/admin undo` | **Deshace la última acción** (NPC o holograma creado) |
| `/admin delete npc <id>` | Eliminar NPC por ID |
| `/admin delete holo <id>` | Eliminar holograma por ID |
| `/admin delete nearest npc` | Eliminar NPC más cercano a ti |
| `/admin delete nearest holo` | Eliminar holograma más cercano |
| `/admin list npcs` | Listar todos los NPCs |
| `/admin list holos` | Listar todos los hologramas |
| `/admin tp npc <id>` | Teletransportarte a un NPC |
| `/admin tp holo <id>` | Teletransportarte a un holograma |
| `/admin reload npcs` | Recargar configuración de NPCs |
| `/admin reload holos` | Recargar configuración de hologramas |

### Acciones en Lote

| Comando | Descripción |
|---------|-------------|
| `/admin delete all npcs` | Eliminar TODOS los NPCs (confirmación requerida) |
| `/admin delete all holos` | Eliminar TODOS los hologramas |
| `/admin hide all` | Ocultar todos temporalmente |
| `/admin show all` | Mostrar todos de nuevo |

---

## 🎯 Ejemplos de Uso

### Configuración de Lobby Básico

```bash
# 1. Ir al centro del lobby y crear NPC de cola principal
/npc create queue
/npc name 1 &5&lRANKED &7Queue
/npc skin 1 Technoblade

# 2. Moverse a otra posición para NPC de partida rápida
/npc create instant
/npc name 2 &e&lINSTANT &7Match
/npc skin 2 Dream

# 3. Crear holograma de top players
/holo create top
# El holograma aparece en tu posición actual

# 4. Crear holograma de estadísticas en vivo
/holo create stats online

# 5. Si te equivocas, deshacer:
/admin undo
```

### Setup Completo de Arena

```bash
# NPCs de kits específicos
/npc create queue sword
/npc name 3 &c&lSWORD &7PvP

/npc create queue axe  
/npc name 4 &6&lAXE &7Combat

/npc create queue crystal
/npc name 5 &d&lCRYSTAL &7PvP

# NPC de información
/npc create info
/npc name 6 &b&lINFO

# Hologramas informativos
/holo create info welcome
/holo create info ranks
/holo create top elo
```

### Eliminar Elementos

```bash
# Ver lista de IDs
/admin list npcs
/admin list holos

# Eliminar por ID
/admin delete npc 5
/admin delete holo 3

# O eliminar el más cercano
/admin delete nearest npc

# Deshacer último (si acabas de crearlo)
/admin undo
```

---

## 🔧 Configuración Avanzada

### Archivo: `plugins/PvPRoomsPro/npcs.yml`

```yaml
npcs:
  1:
    type: QUEUE
    kit: null  # null = menú general
    location:
      world: "lobby"
      x: 100.5
      y: 64.0
      z: 200.5
      yaw: 90.0
      pitch: 0.0
    skin: "Technoblade"
    name: "&5&lRANKED &7Queue"
    look_at_player: true
```

### Archivo: `plugins/PvPRoomsPro/holograms.yml`

```yaml
holograms:
  1:
    type: TOP
    subtype: "general"  # general, elo, wins, kit:sword
    location:
      world: "lobby"
      x: 105.5
      y: 66.0
      z: 200.5
    refresh_seconds: 30
    lines: 10  # número de jugadores a mostrar
```

---

## 📝 Notas

- **IDs automáticos**: Cada NPC/holograma recibe un ID único automáticamente
- **Persistencia**: Todo se guarda en archivos YAML y persiste entre reinicios
- **Actualización**: Los hologramas de leaderboard se actualizan cada 30 segundos
- **Permisos**: Todos los comandos requieren `pvprooms.admin`
- **Colores**: Usa `&` para códigos de color en nombres (ej: `&c&lRED`)

---

## 🎨 Tipos de NPC Disponibles

| Tipo | Acción al Hacer Clic |
|------|---------------------|
| `queue` | Abre menú de cola o cola directa si tiene kit |
| `instant` | Te pone en cola y te empareja con el primero disponible |
| `unranked` | Partidas sin afectar ELO |
| `ffa` | Entra a arena Free For All |
| `stats` | Muestra tus estadísticas en chat |
| `leaderboard` | Abre menú GUI de rankings |
| `kits` | Abre selector de kits |
| `shop` | Abre tienda de cosméticos |
| `info` | Muestra información en chat |
| `events` | Muestra eventos activos/próximos |
| `tournaments` | Inscripción a torneos |
| `practice` | Modo práctica (sin ranking) |
| `spectate` | Menú para ver partidas en curso |
| `party` | Crear/gestionar party |
| `1v1` | Duelo 1v1 rápido |
| `2v2` | Duelos 2v2 |
| `bo3` | Best of 3 |
| `bo5` | Best of 5 |

---

*Generado para PvPRoomsPro v1.0.0*
