package com.nexuscraft.nexusgrave;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click resolution for a grave marker: reads its epitaph to whoever clicks it (owner or
 * not -- an epitaph is meant to be found, same as vanilla's own death message would have been),
 * then either consumes the real "rite of passage" item (if the owner is holding one) or opens the
 * real, lootable {@link GraveMenu} (everyone else, or the owner empty-handed). Grave robbing is
 * simply the absence of any ownership check in the loot branch -- there is no separate "is this
 * my grave" gate anywhere in this class beyond the one place it actually matters (granting
 * permanence, which is deliberately owner-only: it's an earned insurance a player buys for their
 * own death, not something a stranger hands out).
 */
public final class GraveInteractListener implements Listener {

    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveDecayService decayService;

    public GraveInteractListener(GraveConfig config, GraveRegistry registry, GraveDecayService decayService) {
        this.config = config;
        this.registry = registry;
        this.decayService = decayService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        GraveRecord record = registry.findByBlock(clicked.getLocation());
        if (record == null) {
            // Might have clicked the PLAYER_HEAD topper instead of the base marker block.
            record = registry.findByBlock(clicked.getRelative(0, -1, 0).getLocation());
        }
        if (record == null || record.isCollapsed()) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        player.sendMessage("§7\"" + record.effectiveEpitaph() + "\"");

        boolean isOwner = record.ownerId().equals(player.getUniqueId());
        ItemStack inHand = event.getItem();
        if (isOwner && !record.isPermanent() && inHand != null && inHand.getType() == config.permanenceRiteItem) {
            consumeOne(player, inHand);
            decayService.grantPermanence(record);
            player.sendMessage("§8[Grave] §fThe rite holds -- this grave will never decay or collapse. "
                    + "§7(It can still be found and looted by anyone before you reclaim it.)");
            return;
        }

        player.openInventory(GraveMenu.build(player, record, config));
    }

    private void consumeOne(Player player, ItemStack inHand) {
        if (inHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
            player.getInventory().setItemInMainHand(inHand);
        }
    }
}
