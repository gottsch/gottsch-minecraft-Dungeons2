package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two datapack registries postProcess reads are present and carry the shipped content. */
class TestRegistriesSmokeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theWeatheringProcessorListLoads() {
        RegistryAccess access = TestRegistries.get();
        var registry = access.registryOrThrow(Registries.PROCESSOR_LIST);
        assertFalse(registry.keySet().isEmpty(), "no processor lists loaded");
        assertTrue(registry.keySet().stream().anyMatch(id -> id.getPath().contains("classic_weathering")),
                "classic_weathering missing; got " + registry.keySet());
    }

    @Test
    void theClassicMotifResolvesThroughTheRealHelper() {
        MotifConfig config = MotifConfigHelper.get(TestRegistries.get(), "classic");

        assertFalse(config == MotifConfig.DEFAULT, "classic fell back to DEFAULT");
        assertEquals(MotifConfigs.load("classic"), config,
                "registry-resolved classic differs from the direct file read");
    }
}
