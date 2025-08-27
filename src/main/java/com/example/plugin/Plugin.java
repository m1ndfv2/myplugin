package com.example.plugin;

import com.example.plugin.routes.ExampleRoute;

import ch.qos.logback.classic.Logger;
import dev.osunolimits.main.WebServer;
import dev.osunolimits.plugins.NavbarRegister;
import dev.osunolimits.plugins.ShiinaEventListener;
import dev.osunolimits.plugins.ShiinaPlugin;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.plugins.models.NavbarAdminItem;
import dev.osunolimits.plugins.models.NavbarItem;
import dev.osunolimits.plugins.models.NavbarSettingsItem;
import dev.osunolimits.utils.osu.PermissionHelper.Privileges;

/**
 * Shiina Example plugin
 * You can change this and use it for creating your plugins
 */
public class Plugin extends ShiinaPlugin
{
    public static Logger examplePluginLogger;
    private ShiinaEventListener listener = new ExampleListener();

    @Override
    protected void onEnable(String pluginName, Logger logger) {
        examplePluginLogger = logger;
        ShiinaRegistry.registerListener(listener);

        NavbarItem mainNavbarItem = new NavbarItem("Example", "example");
        NavbarAdminItem adminNavItem = new NavbarAdminItem("Example", "example", "fa-solid fa-image", Privileges.ADMINISTRATOR);
        NavbarSettingsItem settingsNavItem = new NavbarSettingsItem("Example", "example");
        settingsNavItem.setIcon("fa-solid fa-cog");

        NavbarRegister.register(mainNavbarItem);
        NavbarRegister.registerAdmin(adminNavItem);
        NavbarRegister.registerSettings(settingsNavItem);

        WebServer.get("/example", new ExampleRoute(mainNavbarItem));
    }

    @Override
    protected void onDisable(String pluginName, Logger logger) {
        examplePluginLogger = null;
        ShiinaRegistry.unregisterListener(listener);
    }

}
