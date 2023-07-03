package org.Combustiblelemon.CovenantRemnants;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class CovenantRemnantsPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();

        // Test that the .jar is loaded and working, using the most obnoxious way possible.
        boolean isMagicLibEnabled = Global.getSettings().getModManager().isModEnabled("MagicLib");
        if(!isMagicLibEnabled){
            throw new RuntimeException("This mod required MagicLib to work.");
        }
    }

    @Override
    public void onNewGame() {
        super.onNewGame();

        // The code below requires that Nexerelin is added as a library (not a dependency, it's only needed to compile the mod).
//        boolean isNexerelinEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");

//        if (!isNexerelinEnabled || SectorManager.getManager().isCorvusMode()) {
//                    new MySectorGen().generate(Global.getSector());
            // Add code that creates a new star system (will only run if Nexerelin's Random (corvus) mode is disabled).
//        }
    }
}
