package dev.orbitalmace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;

public class OrbitalMaceMod implements ModInitializer {
    public static final String MOD_ID = "orbital_mace";
    public static Item ORBITAL_MACE;

    @Override
    public void onInitialize() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MOD_ID, "orbital_mace"));

        Item.Properties props = new Item.Properties()
                .setId(key)
                .rarity(Rarity.EPIC)
                .durability(750)
                .repairable(Items.BREEZE_ROD)
                .attributes(MaceItem.createAttributes())
                .component(DataComponents.TOOL, MaceItem.createToolProperties());

        ORBITAL_MACE = Registry.register(BuiltInRegistries.ITEM, key, new OrbitalMaceItem(props));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> entries.accept(ORBITAL_MACE));

        // Jeder Treffer mit der Orbital Mace (normaler Hit oder Smash) loest den Strike aus
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) return;
            if (!(entity.level() instanceof ServerLevel level)) return;
            if (!(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MACE_SMASH))) return;
            if (!player.getMainHandItem().is(ORBITAL_MACE)) return;
            OrbitalStrike.trigger(level, player, entity.position());
        });

        ServerTickEvents.END_SERVER_TICK.register(OrbitalStrike::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                OrbitalStrike.onDisconnect(server, handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(OrbitalStrike::clearAll);
    }
}
