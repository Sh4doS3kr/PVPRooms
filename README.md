# PvPRoomsPro

> Professional 1v1 PvP duels plugin for **Paper 1.21.x**  
> ELO ranking · GUI kits · Auto arena instancing · Spectator mode · Scoreboard

---

## Table of Contents

1. [Features](#features)
2. [Project Structure](#project-structure)
3. [How to Compile](#how-to-compile)
4. [How to Install](#how-to-install)
5. [First-Time Setup](#first-time-setup)
   - [Creating Kits (Admin)](#creating-kits-admin)
   - [Creating Arenas (Admin)](#creating-arenas-admin)
6. [How Players Use the Plugin](#how-players-use-the-plugin)
7. [All Commands](#all-commands)
8. [Permissions](#permissions)
9. [Configuration Reference](#configuration-reference)
10. [Data Files](#data-files)
11. [Arena Template System](#arena-template-system)
12. [ELO System](#elo-system)
13. [Architecture Overview](#architecture-overview)

---

## Features

| Feature | Description |
|---|---|
| **1v1 Duels** | Automated matchmaking for 1v1 fights |
| **GUI Kit Selection** | Chest GUI to choose kit and join queue |
| **Auto Arena Instancing** | Clones world folders per match, deletes after |
| **ELO Ranking** | K-factor ELO formula with leaderboard |
| **Scoreboard** | Live sideboard showing kit, ELO, opponent, time |
| **Spectator Mode** | Watch any active duel, invisible to players |
| **Kit Management** | Admins create/edit/delete kits from their inventory |
| **Cooldowns** | Queue and spectate cooldowns |
| **Safe Cleanup** | Worlds unloaded and deleted asynchronously |
| **Multiple Simultaneous Matches** | Fully concurrent, no shared state |

---

## Project Structure

```
PvPArenas/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/pvprooms/
        │   ├── PvPRoomsPro.java              ← Main plugin class
        │   ├── commands/
        │   │   ├── KitCommand.java           ← /kit
        │   │   ├── ArenaCommand.java         ← /arena
        │   │   ├── QueueCommand.java         ← /queue  (opens GUI)
        │   │   ├── SpectateCommand.java      ← /spectate <player>
        │   │   ├── LeaveCommand.java         ← /pvpleave
        │   │   ├── StatsCommand.java         ← /stats [player]
        │   │   └── TopCommand.java           ← /top
        │   ├── gui/
        │   │   └── KitGUI.java               ← Kit selection chest GUI
        │   ├── listeners/
        │   │   ├── CombatListener.java       ← PvP damage rules
        │   │   ├── InventoryListener.java    ← GUI click handling
        │   │   └── PlayerListener.java       ← Death, quit, drop events
        │   ├── managers/
        │   │   ├── ArenaInstanceManager.java ← World copy/delete
        │   │   ├── ArenaManager.java         ← Arena template CRUD
        │   │   ├── DuelManager.java          ← Live duel lifecycle
        │   │   ├── EloManager.java           ← ELO calculations
        │   │   ├── KitManager.java           ← Kit CRUD + apply
        │   │   ├── QueueManager.java         ← Per-kit queues + matchmaking
        │   │   └── ScoreboardManager.java    ← Sidebar scoreboards
        │   └── model/
        │       ├── ArenaTemplate.java        ← Arena data model
        │       ├── Duel.java                 ← Active duel state
        │       └── Kit.java                  ← Kit data model
        └── resources/
            ├── plugin.yml
            └── config.yml
```

---

## How to Compile

### Requirements

- **Java 21 JDK** (matches Paper 1.21.x)
- **Maven 3.8+**
- Internet access (to download Paper API from papermc repo)

### Steps

```bash
# Clone or place the project folder
cd PvPArenas

# Compile and package
mvn clean package

# Output JAR will be at:
# target/PvPRoomsPro-1.0.0.jar
```

---

## How to Install

1. Build the JAR as shown above.
2. Copy `target/PvPRoomsPro-1.0.0.jar` into your Paper server's `plugins/` folder.
3. Start or restart the server.
4. The plugin will generate:
   ```
   plugins/PvPRoomsPro/
   ├── config.yml
   ├── kits.yml      (empty)
   ├── arenas.yml    (empty)
   ├── elo.yml       (empty)
   └── maps/         (arena template worlds go here)
   ```

---

## First-Time Setup

### Creating Kits (Admin)

A kit is saved from the **admin's current inventory** at the moment of creation.

**Step-by-step:**

1. Fill your hotbar and inventory with the desired items (sword, armor, potions, etc.).
2. Put on your armor.
3. Run:
   ```
   /kit create nodebuff
   ```
4. The kit `nodebuff` is now saved to `kits.yml`.

**To update a kit** (wears the new inventory):
```
/kit edit nodebuff
```

**To delete a kit:**
```
/kit delete nodebuff
```

**To list all kits:**
```
/kit list
```

> Requires permission `pvprooms.kit` (default: op)

---

### Creating Arenas (Admin)

An arena template links a world folder with two spawn points.

**Step-by-step:**

#### 1. Place the world folder

Copy your arena world folder into:
```
plugins/PvPRoomsPro/maps/<arenaName>/
```

Example:
```
plugins/PvPRoomsPro/maps/arena1/
    level.dat
    region/
    ...
```

> The world must be a valid Minecraft world folder (with `level.dat`).

#### 2. Register the arena

```
/arena create arena1
```

#### 3. Set spawn points

Stand at the spawn position for Player 1 and run:
```
/arena setspawn1 arena1
```

Walk to the spawn position for Player 2 and run:
```
/arena setspawn2 arena1
```

#### 4. Verify

```
/arena info arena1
```

The output should show `Configured: Yes`.

**To delete an arena:**
```
/arena delete arena1
```

> Requires permission `pvprooms.arena` (default: op)

---

## How Players Use the Plugin

### Joining a Duel

1. Run `/queue`
2. A GUI opens showing all available kits.
3. Click the desired kit.
4. You join that kit's queue automatically.
5. When an opponent is found:
   - A temporary arena world is created.
   - Both players are teleported to spawn points.
   - Kits are applied.
   - A countdown starts (`5` seconds by default).
   - **FIGHT!**

### During the Duel

- A sidebar scoreboard shows: kit, your ELO, opponent name, opponent ELO, elapsed time.
- The fight ends when one player **dies**.
- On death, items are not dropped and inventory is not lost.
- ELO is updated for both players.
- Both players are teleported back to the lobby.
- The arena world is automatically deleted.

### Leaving Queue / Forfeiting

```
/pvpleave
```

- If in queue → leaves queue.
- If in a duel → forfeits (opponent wins and gains ELO).
- If spectating → stops spectating.

### Spectating

```
/spectate <PlayerName>
```

- The target player must be in an active FIGHTING duel.
- You are made invisible to both players (SPECTATOR gamemode).
- You can fly around and watch.
- Use `/pvpleave` to stop spectating.

### Checking Stats

```
/stats
/stats <player>
```

Shows ELO and current rank.

### Leaderboard

```
/top
```

Shows the top 10 players by ELO.

---

## All Commands

| Command | Description | Permission |
|---|---|---|
| `/queue` | Open kit GUI and join queue | `pvprooms.queue` |
| `/pvpleave` | Leave queue, forfeit duel, or stop spectating | `pvprooms.leave` |
| `/spectate <player>` | Spectate an active duel | `pvprooms.spectate` |
| `/stats [player]` | View ELO and rank | `pvprooms.stats` |
| `/top` | View ELO leaderboard top 10 | `pvprooms.top` |
| `/kit create <name>` | Create kit from inventory | `pvprooms.kit` |
| `/kit edit <name>` | Update kit from inventory | `pvprooms.kit` |
| `/kit delete <name>` | Delete a kit | `pvprooms.kit` |
| `/kit list` | List all kits | `pvprooms.kit` |
| `/arena create <name>` | Register an arena template | `pvprooms.arena` |
| `/arena setspawn1 <name>` | Set spawn 1 at your location | `pvprooms.arena` |
| `/arena setspawn2 <name>` | Set spawn 2 at your location | `pvprooms.arena` |
| `/arena delete <name>` | Delete an arena template | `pvprooms.arena` |
| `/arena list` | List all arena templates | `pvprooms.arena` |
| `/arena info <name>` | Show arena details | `pvprooms.arena` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `pvprooms.queue` | Join the PvP queue | `true` |
| `pvprooms.leave` | Leave queue or duel | `true` |
| `pvprooms.spectate` | Spectate duels | `true` |
| `pvprooms.stats` | View ELO stats | `true` |
| `pvprooms.top` | View leaderboard | `true` |
| `pvprooms.kit` | Manage kits (admin) | `op` |
| `pvprooms.arena` | Manage arenas (admin) | `op` |
| `pvprooms.admin` | All admin permissions | `op` |

---

## Configuration Reference

`plugins/PvPRoomsPro/config.yml`

```yaml
general:
  prefix: "&8[&cPvP&4Rooms&8] &r"
  lobby-world: "world"          # World name for lobby spawn
  lobby-spawn:
    x: 0.5
    y: 64.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0

duels:
  countdown: 5                  # Seconds before fight starts
  max-duration: 300             # Max duel length (0 = unlimited)
  keep-inventory: true          # Players keep items on death
  drop-items-on-death: false

queue:
  check-interval: 40            # Ticks between matchmaking checks (20t = 1s)

elo:
  starting-elo: 1000
  use-scaling: true             # Use K-factor ELO formula

arenas:
  templates-folder: "maps"
  instance-prefix: "pvp_match_"

scoreboard:
  enabled: true
  title: "&c&lPvPRooms"
  update-interval: 20

cooldowns:
  queue: 3                      # Seconds between queue joins
  spectate: 2
```

---

## Data Files

| File | Contents |
|---|---|
| `kits.yml` | All kit definitions (items, armor, offhand) |
| `arenas.yml` | All arena templates (world, spawn1, spawn2) |
| `elo.yml` | All player ELO scores and display names |
| `config.yml` | All plugin settings |

---

## Arena Template System

When a match starts:

```
plugins/PvPRoomsPro/maps/arena1/   ← Template (never modified)
        ↓ copied to
<serverRoot>/pvp_match_1a2b3c4d/   ← Temporary instance world
```

1. The `maps/arena1/` folder is **recursively copied** to a new world folder.
2. `session.lock` is deleted from the copy so Bukkit can load it.
3. A `WorldCreator` with a void chunk generator loads the world (prevents new chunk generation).
4. `AutoSave` is disabled on the instance world.
5. On match end → world is **unloaded** → folder is **deleted asynchronously**.

> ⚠️ The template folder inside `maps/` is **never touched** during a match.  
> You can have unlimited simultaneous matches using the same template.

---

## ELO System

Uses the standard **ELO formula with K-factor 32**:

```
expectedScore = 1 / (1 + 10^((opponentElo - playerElo) / 400))
eloChange     = K * (actualScore - expectedScore)
```

- Win = `actualScore = 1`
- Loss = `actualScore = 0`
- Minimum gain/loss per match: **5 ELO**
- Starting ELO: **1000** (configurable)

---

## Architecture Overview

```
PvPRoomsPro (main)
    │
    ├── KitManager          CRUD for kits, kits.yml persistence
    ├── ArenaManager        CRUD for templates, arenas.yml persistence
    ├── ArenaInstanceManager  World copy/load/unload/delete
    ├── EloManager          ELO calc + elo.yml persistence
    ├── ScoreboardManager   Per-player sidebar scoreboards
    ├── DuelManager         Duel lifecycle (start → countdown → fight → end)
    │       └── uses all managers above
    ├── QueueManager        Per-kit FIFO queues + matchmaking timer
    │       └── calls DuelManager.startDuel()
    └── KitGUI              Builds & opens kit selection GUI
            └── InventoryListener handles clicks → QueueManager
```

### Duel lifecycle flow

```
/queue → KitGUI → InventoryListener
    → QueueManager.addToQueue()
        → [matchmaking tick]
        → DuelManager.startDuel()
            → ArenaManager.getRandomArena()
            → ArenaInstanceManager.createInstance()   (world clone)
            → Players teleported + kits applied
            → Countdown (5s)
            → FIGHTING state
                ↓ CombatListener enforces PvP rules
                ↓ PlayerListener handles death
            → endDuel(winner)
                → EloManager.processResult()
                → ScoreboardManager.clearScoreboard()
                → Players teleported to lobby
                → ArenaInstanceManager.destroyInstance()  (async delete)
```
