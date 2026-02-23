package com.example.plugin;

import dev.osunolimits.plugins.ShiinaEventListener;
import dev.osunolimits.plugins.events.actions.OnRegisterEvent;
import dev.osunolimits.plugins.events.admin.OnAddDonorEvent;

public class ExampleListener extends ShiinaEventListener {
    @Override
    public void onRegisterEvent(OnRegisterEvent event) {
        Plugin.pluginLogger.info("User registered: {}", event.getName());
    }

    @Override
    public void onAddDonorEvent(OnAddDonorEvent event) {
        Plugin.pluginLogger.info(
            "Supporter granted: userId={}, duration={}, adminId={}",
            event.getUserId(),
            event.getDuration(),
            event.getAdminId()
        );
    }
}
