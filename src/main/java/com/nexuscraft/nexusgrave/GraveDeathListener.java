package com.nexuscraft.nexusgrave;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The whole plugin's real entry point: instead of vanilla's blind random item-scatter on death,
 * every death seals its drops and XP into a real, physical, persistent grave at the death
 * location. Real vanilla drop/XP behavior is fully suppressed here ({@code getDrops().clear()},
 * {@code setDroppedExp(0)}, {@code setKeepInventory(false)} -- a grave, not vanilla's own optional
 * "keep everything" rule, is the whole point) and replaced with this project's own sealed,
 * lootable-by-anyone grave. The corpse-run debuff/compass is deliberately NOT applied here -- see
 * {@link #onPlayerRespawn} and {@code PlayerRespawnEvent}'s own stub comment for why.
 */
public final class GraveDeathListener implements Listener {

    private final GraveRegistry registry;
    private final GraveStructureService structureService;
    private final CorpseRunService corpseRunService;

    public GraveDeathListener(GraveRegistry registry, GraveStructureService structureService, CorpseRunService corpseRunService) {
        this.registry = registry;
        this.structureService = structureService;
        this.corpseRunService = corpseRunService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location deathLocation = player.getLocation();
        if (deathLocation == null || deathLocation.getWorld() == null) {
            return;
        }

        String plainDeathMessage = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        if (plainDeathMessage == null || plainDeathMessage.isBlank()) {
            plainDeathMessage = player.getName() + " died";
        }

        GraveRecord record = new GraveRecord(
                GraveKey.newKey(),
                player.getUniqueId(),
                player.getName(),
                deathLocation.getWorld().getName(),
                deathLocation.getBlockX(),
                deathLocation.getBlockY(),
                deathLocation.getBlockZ(),
                System.currentTimeMillis(),
                plainDeathMessage);

        record.setCustomEpitaph(registry.pendingEpitaph(player.getUniqueId()));
        record.items().addAll(event.getDrops());
        record.setDroppedExp(event.getDroppedExp());

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);

        registry.register(record);
        structureService.place(record);
    }

    /** Applies the corpse-run debuff/compass right as the player actually respawns -- the moment
     *  it will really stick, unlike applying it during {@link PlayerDeathEvent} itself. */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        corpseRunService.apply(event.getPlayer());
    }
}
