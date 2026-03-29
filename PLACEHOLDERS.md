# PvPRoomsPro — PlaceholderAPI Placeholders

> **Prefijo:** `%pvprooms_<placeholder>%`
> Compatible con PlaceholderAPI (PAPI). Requiere la expansión `PvPRooms` activa.

---

## 📊 ELO / Puntuación

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_elo%` | ELO global del jugador | `1450` |
| `%pvprooms_elo_<kit>%` | ELO del jugador en un kit específico | `%pvprooms_elo_espada%` → `1320` |
| `%pvprooms_points%` | Puntos totales del jugador | `2800` |
| `%pvprooms_points_<kit>%` | Puntos del jugador en un kit específico | `340` |
| `%pvprooms_elo_change%` | Último cambio de ELO (+/-) | `+18` |
| `%pvprooms_peak_elo%` | ELO máximo histórico del jugador | `1870` |

---

## 🏆 Rango / Tier

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_tier%` | Nombre del rango actual del jugador | `Diamante` |
| `%pvprooms_tier_<kit>%` | Rango del jugador en un kit específico | `Oro` |
| `%pvprooms_tier_color%` | Código de color del rango actual | `§b` |
| `%pvprooms_tier_color_<kit>%` | Código de color del rango en un kit | `§e` |
| `%pvprooms_tier_icon%` | Icono/símbolo del rango actual | `✦` |
| `%pvprooms_tier_score%` | Puntuación interna del rango (0–8) | `5` |
| `%pvprooms_tier_formatted%` | Rango con formato completo (color+nombre) | `§bDiamante` |
| `%pvprooms_tier_next%` | Nombre del siguiente rango | `Maestro` |
| `%pvprooms_tier_pts_needed%` | Puntos necesarios para subir de rango | `450` |
| `%pvprooms_tier_progress%` | Progreso al siguiente rango (0–100) | `67` |

### Rangos disponibles (de menor a mayor)
| Rango | Color | Score |
|---|---|---|
| Sin Rango | `§7` | `0` |
| Hierro | `§8` | `1` |
| Bronce | `§6` | `2` |
| Plata | `§f` | `3` |
| Oro | `§e` | `4` |
| Esmeralda | `§a` | `5` |
| Diamante | `§b` | `6` |
| Maestro | `§5` | `7` |
| Leyenda | `§c§l` | `8` |

---

## 🎖 Título

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_title%` | Título actual del jugador | `Leyenda` |
| `%pvprooms_title_color%` | Color del título | `§c` |
| `%pvprooms_title_symbol%` | Símbolo decorativo del título | `✦` |
| `%pvprooms_title_formatted%` | Título con color completo | `§c✦ Leyenda` |

---

## ⚔ Estadísticas de Duelo

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_wins%` | Victorias totales | `142` |
| `%pvprooms_losses%` | Derrotas totales | `98` |
| `%pvprooms_kills%` | Kills totales en duelos | `210` |
| `%pvprooms_deaths%` | Muertes totales en duelos | `105` |
| `%pvprooms_kd%` | Ratio K/D (kills/deaths) | `2.00` |
| `%pvprooms_wl%` | Ratio W/L (wins/losses) | `1.45` |
| `%pvprooms_winrate%` | Porcentaje de victorias (0–100) | `59` |
| `%pvprooms_streak%` | Racha de victorias actual | `7` |
| `%pvprooms_best_streak%` | Mejor racha histórica | `14` |
| `%pvprooms_duels_played%` | Total de duelos jugados | `240` |
| `%pvprooms_wins_<kit>%` | Victorias en un kit específico | `%pvprooms_wins_espada%` → `45` |
| `%pvprooms_losses_<kit>%` | Derrotas en un kit específico | `23` |
| `%pvprooms_kills_<kit>%` | Kills en un kit específico | `68` |
| `%pvprooms_kd_<kit>%` | Ratio K/D en un kit específico | `2.95` |

---

## 🎮 Estado en juego

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_in_queue%` | `true` si el jugador está en cola | `true` |
| `%pvprooms_in_duel%` | `true` si el jugador está en duelo | `false` |
| `%pvprooms_queue_kit%` | Kit en el que está en cola | `espada` |
| `%pvprooms_queue_time%` | Tiempo en cola (segundos) | `38` |
| `%pvprooms_duel_opponent%` | Nombre del rival actual en duelo | `Steve` |
| `%pvprooms_duel_kit%` | Kit del duelo actual | `nodebuff` |
| `%pvprooms_duel_time%` | Tiempo transcurrido del duelo (segundos) | `72` |
| `%pvprooms_duel_score%` | Marcador del duelo actual | `1-0` |

---

## 🏅 Tops — Clasificación global

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_rank%` | Posición en el ranking global | `#4` |
| `%pvprooms_rank_num%` | Posición numérica sin `#` | `4` |
| `%pvprooms_rank_<kit>%` | Posición en el ranking de un kit | `%pvprooms_rank_espada%` → `#7` |
| `%pvprooms_top_name_<N>%` | Nombre del jugador en posición N (1–10) | `%pvprooms_top_name_1%` → `Alex` |
| `%pvprooms_top_elo_<N>%` | ELO del jugador en posición N | `%pvprooms_top_elo_1%` → `2340` |
| `%pvprooms_top_tier_<N>%` | Rango del jugador en posición N | `Leyenda` |
| `%pvprooms_top_wins_<N>%` | Victorias del jugador en posición N | `312` |
| `%pvprooms_top_<kit>_name_<N>%` | Nombre del top N en un kit concreto | `%pvprooms_top_espada_name_1%` |
| `%pvprooms_top_<kit>_elo_<N>%` | ELO del top N en un kit concreto | `1980` |

---

## 🎒 Kits

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_kit_count%` | Total de kits disponibles | `8` |
| `%pvprooms_kit_list%` | Lista de kits separada por comas | `espada,nodebuff,uhc` |
| `%pvprooms_best_kit%` | Kit con más victorias del jugador | `espada` |
| `%pvprooms_best_kit_elo%` | ELO más alto del jugador entre todos los kits | `1780` |
| `%pvprooms_queue_size_<kit>%` | Jugadores en cola de un kit | `%pvprooms_queue_size_espada%` → `3` |
| `%pvprooms_queue_total%` | Total de jugadores en cola (todos los kits) | `12` |

---

## 🎨 Trims (Armor Trim System)

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_trim_helmet%` | Trim del casco del jugador | `iron:bolt` |
| `%pvprooms_trim_chestplate%` | Trim de la pechera del jugador | `diamond:flow` |
| `%pvprooms_trim_leggings%` | Trim de los pantalones del jugador | `gold:coast` |
| `%pvprooms_trim_boots%` | Trim de las botas del jugador | `emerald:tide` |
| `%pvprooms_trim_helmet_pattern%` | Solo el patrón del casco | `bolt` |
| `%pvprooms_trim_helmet_material%` | Solo el material del casco | `iron` |
| `%pvprooms_trim_count%` | Número de piezas con trim activo | `3` |
| `%pvprooms_crates_opened%` | Total de crates abiertos por el jugador | `47` |
| `%pvprooms_legendary_trims%` | Trims legendarios obtenidos | `5` |

---

## 🌐 Servidor

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_online%` | Jugadores conectados | `34` |
| `%pvprooms_active_duels%` | Duelos activos en este momento | `8` |
| `%pvprooms_region%` | Región del servidor | `EU` |
| `%pvprooms_total_players%` | Total de jugadores registrados | `1240` |
| `%pvprooms_total_duels_played%` | Total de duelos jugados en el servidor | `18450` |

---

## 🌍 Región del jugador

| Placeholder | Descripción | Ejemplo |
|---|---|---|
| `%pvprooms_player_region%` | Región del jugador (detectada por IP) | `EU` |
| `%pvprooms_player_region_flag%` | Bandera emoji de la región | `🇪🇺` |

---

## 💡 Uso en scoreboard (ejemplo CMI / FeatherBoard)

```yaml
lines:
  - '§5§lPvP §dRooms'
  - ''
  - '§7Rango: %pvprooms_tier_formatted%'
  - '§7ELO: §e%pvprooms_elo%'
  - '§7Pos: §f%pvprooms_rank%'
  - ''
  - '§7Victorias: §a%pvprooms_wins%'
  - '§7Derrotas: §c%pvprooms_losses%'
  - '§7K/D: §f%pvprooms_kd%'
  - '§7Racha: §6%pvprooms_streak%'
  - ''
  - '§7En cola: §e%pvprooms_in_queue%'
  - '§7Cola kit: §f%pvprooms_queue_kit%'
```

## 💡 Uso en chat (ejemplo EssentialsX)

```yaml
format: '{DISPLAYNAME} §8[%pvprooms_tier_formatted%§8] §r{MESSAGE}'
```

## 💡 Uso en TAB (ejemplo TAB plugin)

```yaml
header: '§5§lPvP§dRooms §8| §7Región: %pvprooms_region%'
footer: '§7Online: §e%pvprooms_online% §8| §7Duelos activos: §c%pvprooms_active_duels%'
```

---

## ⚙ Implementación (PvPRoomsExpansion)

Para que los placeholders funcionen, añade la clase `PvPRoomsExpansion` (PlaceholderAPI) al plugin
y regístrala en `onEnable()`:

```java
if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
    new PvPRoomsExpansion(this).register();
    getLogger().info("[PAPI] PlaceholderAPI expansion registered.");
}
```

Cada placeholder del tipo `%pvprooms_top_name_1%` utiliza la API `TierManager.getTopPlayers(10)`.

---

*Generado para PvPRoomsPro v1.0.0 — Sistema de Tiers custom + Armor Trims*
