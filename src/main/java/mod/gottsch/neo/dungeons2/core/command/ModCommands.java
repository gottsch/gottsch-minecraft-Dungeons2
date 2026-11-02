package mod.gottsch.neo.dungeons2.core.command;

import net.neoforged.fml.common.EventBusSubscriber;
import mod.gottsch.neo.dungeons2.Dungeons;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = Dungeons.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onServerStarting(RegisterCommandsEvent event) {
        SpawnDungeonCommand.register(event.getDispatcher());
        LocateDungeonCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
