package com.bluup.hexwright.inits

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.common.aspects.AspectMappingLoader
import com.bluup.hexwright.server.block.HexwrightBlocks
import com.bluup.hexwright.server.hexicon.HexiconUIFactory
import com.bluup.hexwright.server.item.HexwrightItems
import com.bluup.hexwright.server.menu.HexwrightMenus
import com.bluup.hexwright.server.pentabox.PentaboxUIFactory
import com.bluup.hexwright.server.pocketcaster.PocketCasterUIFactory
import com.bluup.hexwright.server.coalescence.CoalescenceRecipes
import com.bluup.hexwright.server.hexpatterns.HexwrightActions
import com.bluup.hexwright.server.talisman.TalismanActions
import com.bluup.hexwright.server.talisman.TalismanEvents
import com.bluup.hexwright.server.progression.HexwrightCriteria
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities
import com.bluup.hexwright.server.staff_assembly.StaffCoreBeamHandler
import com.lowdragmc.lowdraglib.gui.factory.UIFactory
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.core.cauldron.CauldronInteraction
import net.minecraft.server.packs.PackType

object HexwrightServer : ModInitializer {
    override fun onInitialize() {
        com.bluup.hexwright.server.sound.HexwrightSoundEvents.register()
        com.bluup.hexwright.server.effect.HexwrightEffects.register()
        HexwrightCriteria.register()
        TalismanEvents.register()
        com.bluup.hexwright.server.pentabox.PentaboxEvents.register()
        CoalescenceRecipes.register()
        com.bluup.hexwright.server.hexpatterns.StackIota.register()
        com.bluup.hexwright.server.network.ResonanceIota.register()
        com.bluup.hexwright.server.region.RegionIota.register()
        com.bluup.hexwright.server.remnant.RemnantIota.register()
        com.bluup.hexwright.server.remnant.RemnantsIota.register()
        HexwrightActions.register()
        com.bluup.hexwright.server.remnant.RemnantActions.register()
        com.bluup.hexwright.server.remnant.RemnantBuffs.register()
        com.bluup.hexwright.server.armour.MantleFlight.register()
        com.bluup.hexwright.server.region.RegionActions.register()
        com.bluup.hexwright.server.region.RegionThothBenchmark.register()
        com.bluup.hexwright.server.hexpatterns.CantorActions.register()
        com.bluup.hexwright.server.hexpatterns.HeldSealActions.register()
        com.bluup.hexwright.server.hexpatterns.VaultActions.register()
        com.bluup.hexwright.server.hexpatterns.VaultRelocationActions.register()
        com.bluup.hexwright.server.hexpatterns.ResonantAnchorActions.register()
        com.bluup.hexwright.server.hexpatterns.ResonantRingActions.register()
        com.bluup.hexwright.server.hexpatterns.HarmonicActions.register()
        com.bluup.hexwright.server.hexpatterns.HarmonicEmitterActions.register()
        com.bluup.hexwright.server.hexpatterns.HarmonicTransducerActions.register()
        com.bluup.hexwright.server.harmonic.HarmonicEvents.register()
        com.bluup.hexwright.server.hexpatterns.WardingBoxActions.register()
        com.bluup.hexwright.server.hexpatterns.MediaGrantActions.register()
        com.bluup.hexwright.server.media.MediaGrants.register()
        com.bluup.hexwright.server.hexicon.HexiconData.register()
        com.bluup.hexwright.server.hexpatterns.WielderActions.register()
        com.bluup.hexwright.server.armour.ArmourPowerActions.register()
        com.bluup.hexwright.server.powerorb.PowerOrbActions.register()
        com.bluup.hexwright.server.powerorb.GolemOrbPower.register()
        com.bluup.hexwright.server.armour.PassageStep.register()
        TalismanActions.register()
        com.bluup.hexwright.server.portal.PortalActions.register()
        com.bluup.hexwright.server.portal.PortalManager.register()
        com.bluup.hexwright.server.hexpatterns.VoidTearActions.register()
        com.bluup.hexwright.server.portal.VoidTearManager.register()
        com.bluup.hexwright.server.hexpatterns.DustActions.register()
        com.bluup.hexwright.server.dust.DustManifestations.register()
        com.bluup.hexwright.server.vault.VaultManager.register()
        com.bluup.hexwright.server.vehicle.FlightActions.register()
        com.bluup.hexwright.server.bindstone.BindstoneWard.register()
        com.bluup.hexwright.server.boss.WardedChests.register()
        com.bluup.hexwright.server.worldgen.AreaWard.register()
        com.bluup.hexwright.server.worldgen.ruinedportal.RuinedPortalManager.register()
        com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultWard.register()
        com.bluup.hexwright.server.staff_assembly.StaffCastFlare.register()
        com.bluup.hexwright.server.weapon.SlamWindUp.register()
        com.bluup.hexwright.server.weapon.SoulHarvest.register()
        com.bluup.hexwright.compat.accessories.AccessoriesCompat.register()
        Hexwright.LOGGER.info("Hexwright server initializing.")
        HexwrightNetworking.registerServer()
        HexwrightItems.register()
        com.bluup.hexwright.server.armour.HexwrightArmour.register()
        HexwrightBlocks.register()
        com.bluup.hexwright.server.fluid.HexidFluids.register()
        com.bluup.hexwright.server.fluid.HexidTankStorage.register()
        CauldronInteraction.WATER[HexwrightItems.SEALED_SATCHEL] = CauldronInteraction.DYED_ITEM
        CauldronInteraction.WATER[HexwrightItems.MANTLE_OF_ASCENSION] = CauldronInteraction.DYED_ITEM
        CauldronInteraction.WATER[HexwrightItems.HAT_OF_ASCENSION] = CauldronInteraction.DYED_ITEM
        CauldronInteraction.WATER[HexwrightItems.GOLEM_POWER_ORB] = CauldronInteraction.DYED_ITEM
        com.bluup.hexwright.server.worldgen.HexwrightWorldgen.register()
        com.bluup.hexwright.server.progression.TabletLoot.register()
        com.bluup.hexwright.server.progression.RecipeTablets.validate(
            com.bluup.hexwright.server.block.WorktableRecipes.RECIPES.mapTo(HashSet()) { it.nameKey }
        )
        com.bluup.hexwright.server.journal.Investigations.validate()
        com.bluup.hexwright.server.journal.LoreEntries.validate()
        com.bluup.hexwright.server.journal.Artifacts.validate()
        com.bluup.hexwright.server.journal.InvestigationProgress.register()
        com.bluup.hexwright.server.journal.StarterJournal.register()
        com.bluup.hexwright.server.journal.JournalDebugCommand.register()
        com.bluup.hexwright.server.command.DebugCommand.register()
        HexwrightEntities.register()
        com.bluup.hexwright.server.boss.HexwrightBossEntities.register()
        com.bluup.hexwright.server.boss.HexwrightBossEntities.registerAttributes()
        com.bluup.hexwright.server.mob.HexwrightMobEntities.register()
        com.bluup.hexwright.server.mob.HexwrightMobEntities.registerAttributes()
        com.bluup.hexwright.server.powerorb.PowerOrbEntities.register()
        HexwrightMenus.register()
        com.bluup.hexwright.server.talisman.HexwrightRecipes.register()
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> StaffCoreBeamHandler.clearPlayer(handler.player) }
        UIFactory.register(PentaboxUIFactory.INSTANCE)
        UIFactory.register(HexiconUIFactory.INSTANCE)
        UIFactory.register(PocketCasterUIFactory.INSTANCE)
        UIFactory.register(com.bluup.hexwright.server.journal.JournalUIFactory.INSTANCE)
        UIFactory.register(com.bluup.hexwright.server.reliquary.SatchelUIFactory.INSTANCE)

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(AspectMappingLoader())
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register { player, _ ->
            HexwrightNetworking.sendAspectSync(player)
            HexwrightNetworking.sendMasterySync(player)
            HexwrightNetworking.sendRecipeUnlockSync(player)
            HexwrightNetworking.sendInvestigationSync(player)
            HexwrightNetworking.sendArtifactSync(player)
            HexwrightNetworking.sendResonanceNames(player)
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            com.bluup.hexwright.common.aspects.RecipeAspects.rebuild(server)
            com.bluup.hexwright.server.hexpatterns.PerWorldPatterns.prime(server)
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) {
                com.bluup.hexwright.common.aspects.RecipeAspects.rebuild(server)
                server.playerList.players.forEach { HexwrightNetworking.sendAspectSync(it) }
            }
        }
    }
}
