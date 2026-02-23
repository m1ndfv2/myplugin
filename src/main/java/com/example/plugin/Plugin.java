package com.example.plugin;

import com.example.plugin.routes.SupporterKeysAdminRoute;
import com.example.plugin.routes.SupporterKeysRoute;

import ch.qos.logback.classic.Logger;
import dev.osunolimits.main.WebServer;
import dev.osunolimits.plugins.NavbarRegister;
import dev.osunolimits.plugins.ShiinaEventListener;
import dev.osunolimits.plugins.ShiinaPlugin;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.plugins.models.NavbarAdminItem;
import dev.osunolimits.plugins.models.NavbarItem;
import dev.osunolimits.utils.osu.PermissionHelper.Privileges;

public class Plugin extends ShiinaPlugin {
    public static Logger pluginLogger;
    private final ShiinaEventListener listener = new ExampleListener();

    @Override
    protected void onEnable(String pluginName, Logger logger) {
        pluginLogger = logger;
        ShiinaRegistry.registerListener(listener);

        NavbarItem navItem = new NavbarItem("Supporter Keys", "supporter-keys");
        NavbarAdminItem adminNavItem = new NavbarAdminItem(
            "Supporter Keys",
            "supporter-keys-admin",
            "fa-solid fa-key",
            Privileges.ADMINISTRATOR
        );

        NavbarRegister.register(navItem);
        NavbarRegister.registerAdmin(adminNavItem);

        SupporterKeysRoute userRoute = new SupporterKeysRoute(navItem);
        WebServer.get("/supporter-keys", userRoute);
        WebServer.post("/supporter-keys", userRoute);

        SupporterKeysAdminRoute adminRoute = new SupporterKeysAdminRoute(adminNavItem);
        WebServer.get("/supporter-keys/admin", adminRoute);
        WebServer.post("/supporter-keys/admin", adminRoute);
        WebServer.get("/admin/supporter-keys", adminRoute);
        WebServer.post("/admin/supporter-keys", adminRoute);
    }

    @Override
    protected void onDisable(String pluginName, Logger logger) {
        pluginLogger = null;
        ShiinaRegistry.unregisterListener(listener);
    }
}
