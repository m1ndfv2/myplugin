package com.example.plugin.routes;

import java.sql.ResultSet;
import java.time.Instant;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.models.NavbarItem;
import dev.osunolimits.utils.osu.PermissionHelper;
import dev.osunolimits.utils.osu.PermissionHelper.Privileges;
import spark.Request;
import spark.Response;

public class SupporterKeysRoute extends Shiina {
    private static final int SUPPORTER_PRIV = Privileges.SUPPORTER.getValue();
    private final NavbarItem navItem;

    public SupporterKeysRoute(NavbarItem navItem) {
        this.navItem = navItem;
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if ("POST".equalsIgnoreCase(req.requestMethod())) {
            handleRedeem(req, shiina);
        }

        shiina.data.put("actNav", navItem.getActNav());
        shiina.data.put("seo", new SEOBuilder("Supporter Keys", App.customization.get("homeDescription").toString()));
        shiina.data.put("canManageSupporterKeys", canManageSupporterKeys(shiina));

        return renderTemplate("modules/plugins/supporter/supporter.html", shiina, res, req);
    }

    private void handleRedeem(Request req, ShiinaRequest shiina) {
        String action = req.queryParams("action");
        if (!"redeem".equalsIgnoreCase(action)) {
            shiina.data.put("statusError", "Unknown action.");
            return;
        }

        if (!shiina.loggedIn || shiina.user == null) {
            shiina.data.put("statusError", "You must be logged in to redeem a key.");
            return;
        }

        String code = req.queryParams("code");
        if (code == null || code.isBlank()) {
            shiina.data.put("statusError", "Key cannot be empty.");
            return;
        }

        code = code.trim().toUpperCase();

        try {
            ResultSet keyRs = shiina.mysql.Query(
                "SELECT `id`, `duration_days`, `used_by` FROM `supporter_keys` WHERE `code` = ? LIMIT 1",
                code
            );

            if (keyRs == null || !keyRs.next()) {
                shiina.data.put("statusError", "Invalid key.");
                return;
            }

            int keyId = keyRs.getInt("id");
            int durationDays = keyRs.getInt("duration_days");
            int usedBy = keyRs.getInt("used_by");

            if (usedBy > 0) {
                shiina.data.put("statusError", "This key has already been used.");
                return;
            }

            ResultSet userRs = shiina.mysql.Query(
                "SELECT `priv`, `donor_end` FROM `users` WHERE `id` = ? LIMIT 1",
                shiina.user.id
            );

            if (userRs == null || !userRs.next()) {
                shiina.data.put("statusError", "User not found.");
                return;
            }

            int now = (int) Instant.now().getEpochSecond();
            int oldPriv = userRs.getInt("priv");
            int oldDonorEnd = userRs.getInt("donor_end");

            int newPriv = oldPriv | SUPPORTER_PRIV;
            int baseTs = Math.max(now, oldDonorEnd);
            int newDonorEnd = baseTs + (durationDays * 86400);

            int updatedUser = shiina.mysql.Exec(
                "UPDATE `users` SET `priv` = ?, `donor_end` = ? WHERE `id` = ?",
                newPriv,
                newDonorEnd,
                shiina.user.id
            );

            if (updatedUser < 0) {
                shiina.data.put("statusError", "Could not update user privileges.");
                return;
            }

            int updatedKey = shiina.mysql.Exec(
                "UPDATE `supporter_keys` SET `used_by` = ?, `used_at` = ? WHERE `id` = ?",
                shiina.user.id,
                now,
                keyId
            );

            if (updatedKey < 0) {
                shiina.data.put("statusError", "Supporter granted, but key state update failed. Check database.");
                return;
            }

            shiina.data.put("statusMessage", "Success! Supporter activated for " + durationDays + " day(s).");
        } catch (Exception ex) {
            shiina.data.put("statusError", "Redemption failed: " + ex.getMessage());
        }
    }

    private boolean canManageSupporterKeys(ShiinaRequest shiina) {
        if (!shiina.loggedIn || shiina.user == null) {
            return false;
        }

        return PermissionHelper.hasPrivileges(shiina.user.priv, Privileges.ADMINISTRATOR)
            || PermissionHelper.hasPrivileges(shiina.user.priv, Privileges.DEVELOPER);
    }
}
