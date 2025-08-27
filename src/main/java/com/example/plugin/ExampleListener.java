package com.example.plugin;

import dev.osunolimits.plugins.ShiinaEventListener;
import dev.osunolimits.plugins.events.actions.OnRegisterEvent;

public class ExampleListener extends ShiinaEventListener {
    @Override
    public void onRegisterEvent(OnRegisterEvent event) {
        Plugin.examplePluginLogger.info("User Registered: " + event.getName());
    }

    // There are more events check it out on https://osu-nolimits.github.io/wiki/plugins/
}
