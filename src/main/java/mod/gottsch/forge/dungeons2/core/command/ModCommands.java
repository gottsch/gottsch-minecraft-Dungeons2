package mod.gottsch.forge.dungeons2.core.command;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onServerStarting(RegisterCommandsEvent event) {
        SpawnDungeonCommand.register(event.getDispatcher());
    }
}
