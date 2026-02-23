package com.example.plugin.routes;

import java.sql.ResultSet;
import java.time.Instant;

import com.example.plugin.Plugin;
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

        return renderUserTemplate(shiina, res, req);
    }

    private Object renderUserTemplate(ShiinaRequest shiina, Response res, Request req) throws Exception {
        try {
            return renderTemplate("modules/supporter/supporter.html", shiina, res, req);
        } catch (Exception primaryError) {
            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.warn("Primary user template missing, trying legacy path: {}", primaryError.getMessage());
            }

            return renderTemplate("modules/plugins/supporter/supporter.html", shiina, res, req);
        }
    }

    private void handleRedeem(Request req, ShiinaRequest shiina) {
        String action = req.queryParams("action");
        if (!"redeem".equalsIgnoreCase(action)) {
            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.warn("Supporter redeem rejected: unknown action='{}' userId={}", action, shiina.user != null ? shiina.user.id : 0);
            }
            shiina.data.put("statusError", "Unknown action.");
            return;
        }

        if (!shiina.loggedIn || shiina.user == null) {
            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.warn("Supporter redeem rejected: anonymous user attempted activation.");
            }
            shiina.data.put("statusError", "You must be logged in to redeem a key.");
            return;
        }

        String code = req.queryParams("code");
        if (code == null || code.isBlank()) {
            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.warn("Supporter redeem rejected: empty key userId={}", shiina.user.id);
            }
            shiina.data.put("statusError", "Key cannot be empty.");
            return;
        }

        code = code.trim().toUpperCase();
        if (Plugin.pluginLogger != null) {
            Plugin.pluginLogger.info("Supporter redeem started: userId={} code={}", shiina.user.id, code);
        }

        try {
            ResultSet keyRs = shiina.mysql.Query(
                "SELECT `id`, `duration_days` FROM `supporter_keys` WHERE `code` = ? LIMIT 1",
                code
            );

            if (keyRs == null || !keyRs.next()) {
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.warn("Supporter redeem failed: invalid key userId={} code={}", shiina.user.id, code);
                }
                shiina.data.put("statusError", "Invalid key.");
                return;
            }

            int keyId = keyRs.getInt("id");
            int durationDays = keyRs.getInt("duration_days");
            int now = (int) Instant.now().getEpochSecond();

            int claimResult = shiina.mysql.Exec(
                "UPDATE `supporter_keys` SET `used_by` = ?, `used_at` = NOW() WHERE `id` = ? AND (`used_by` = 0 OR `used_by` IS NULL)",
                shiina.user.id,
                keyId
            );

            if (claimResult < 0) {
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.error("Supporter redeem failed: key claim query error userId={} code={}", shiina.user.id, code);
                }
                shiina.data.put("statusError", "Could not activate supporter key.");
                return;
            }

            ResultSet claimRs = shiina.mysql.Query(
                "SELECT `used_by` FROM `supporter_keys` WHERE `id` = ? LIMIT 1",
                keyId
            );

            if (claimRs == null || !claimRs.next()) {
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.warn("Supporter redeem failed: key disappeared after claim userId={} code={}", shiina.user.id, code);
                }
                shiina.data.put("statusError", "Invalid key.");
                return;
            }

            int usedBy = claimRs.getInt("used_by");
            if (usedBy != shiina.user.id) {
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.warn("Supporter redeem failed: key already used userId={} code={} usedBy={}", shiina.user.id, code, usedBy);
                }
                shiina.data.put("statusError", "This key has already been used.");
                return;
            }

            int updateUserResult = shiina.mysql.Exec(
                "UPDATE `users` SET `priv` = (`priv` | ?), `donor_end` = (GREATEST(`donor_end`, ?) + (? * 86400)) WHERE `id` = ?",
                SUPPORTER_PRIV,
                now,
                durationDays,
                shiina.user.id
            );

            if (updateUserResult < 0) {
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.error("Supporter redeem failed: user update error userId={} code={}", shiina.user.id, code);
                }
                shiina.data.put("statusError", "Could not update user privileges.");
                return;
            }

            if (App.jedisPool != null) {
                String addPrivPayload = String.format("{\"id\":%d,\"privs\":[\"supporter\"]}", shiina.user.id);
                App.jedisPool.publish("addpriv", addPrivPayload);
            }

            ResultSet userAfterRs = shiina.mysql.Query(
                "SELECT `donor_end` FROM `users` WHERE `id` = ? LIMIT 1",
                shiina.user.id
            );
            int newDonorEnd = userAfterRs != null && userAfterRs.next() ? userAfterRs.getInt("donor_end") : now;

            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.info(
                    "Supporter redeem success: userId={} code={} durationDays={} redeemedAt={} newDonorEnd={}",
                    shiina.user.id,
                    code,
                    durationDays,
                    now,
                    newDonorEnd
                );
            }
            shiina.data.put("statusMessage", "Success! Supporter activated for " + durationDays + " day(s).");
        } catch (Exception ex) {
            if (Plugin.pluginLogger != null) {
                Plugin.pluginLogger.error("Supporter redeem exception: userId={} code={} error={}", shiina.user.id, code, ex.getMessage());
            }
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
