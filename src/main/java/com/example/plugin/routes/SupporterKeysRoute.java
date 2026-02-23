package com.example.plugin.routes;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.plugins.events.admin.OnAddDonorEvent;
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
            handlePost(req, shiina);
        }

        shiina.data.put("actNav", navItem.getActNav());
        shiina.data.put("seo", new SEOBuilder("Supporter Keys", App.customization.get("homeDescription").toString()));
        shiina.data.put("isAdmin", isAdmin(shiina));
        shiina.data.put("generatedKeys", loadGeneratedKeys(shiina));

        return renderTemplate("modules/plugins/supporter/supporter.html", shiina, res, req);
    }

    private void handlePost(Request req, ShiinaRequest shiina) {
        String action = req.queryParams("action");
        if ("generate".equalsIgnoreCase(action)) {
            handleGenerate(req, shiina);
            return;
        }

        if ("redeem".equalsIgnoreCase(action)) {
            handleRedeem(req, shiina);
            return;
        }

        shiina.data.put("statusError", "Unknown action.");
    }

    private void handleGenerate(Request req, ShiinaRequest shiina) {
        if (!isAdmin(shiina)) {
            shiina.data.put("statusError", "Only administrators can generate keys.");
            return;
        }

        int amount = parseInt(req.queryParams("amount"), 1);
        int durationDays = parseInt(req.queryParams("durationDays"), 30);

        if (amount < 1 || amount > 500) {
            shiina.data.put("statusError", "Amount must be between 1 and 500.");
            return;
        }

        if (durationDays < 1 || durationDays > 3650) {
            shiina.data.put("statusError", "Duration must be between 1 and 3650 days.");
            return;
        }

        List<String> created = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            String code = UUID.randomUUID().toString().replace("-", "").toUpperCase();
            code = code.substring(0, 8) + "-" + code.substring(8, 16) + "-" + code.substring(16, 24);

            int inserted = shiina.mysql.Exec(
                "INSERT INTO supporter_keys (`code`, `duration_days`, `created_by`, `created_at`) VALUES (?, ?, ?, ?)",
                code,
                durationDays,
                shiina.user != null ? shiina.user.id : 0,
                (int) Instant.now().getEpochSecond()
            );

            if (inserted != -1) {
                created.add(code);
            }
        }

        shiina.data.put("statusMessage", "Generated " + created.size() + " key(s) for " + durationDays + " day(s).");
        shiina.data.put("newKeys", created);
    }

    private void handleRedeem(Request req, ShiinaRequest shiina) {
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
                "SELECT `id`, `duration_days`, `used_by`, `used_at` FROM `supporter_keys` WHERE `code` = ? LIMIT 1",
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

            ShiinaRegistry.fireEvent(new OnAddDonorEvent(durationDays + "d", shiina.user.id, 0));
            shiina.data.put("statusMessage", "Success! Supporter activated for " + durationDays + " day(s).");
        } catch (Exception ex) {
            shiina.data.put("statusError", "Redemption failed: " + ex.getMessage());
        }
    }

    private List<Map<String, Object>> loadGeneratedKeys(ShiinaRequest shiina) {
        List<Map<String, Object>> keys = new ArrayList<>();
        if (!isAdmin(shiina)) {
            return keys;
        }

        try {
            ResultSet rs = shiina.mysql.Query(
                "SELECT `code`, `duration_days`, `used_by`, `created_at`, `used_at` FROM `supporter_keys` ORDER BY `id` DESC LIMIT 100"
            );

            if (rs == null) {
                return keys;
            }

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("code", rs.getString("code"));
                row.put("durationDays", rs.getInt("duration_days"));
                row.put("usedBy", rs.getInt("used_by"));
                row.put("createdAt", rs.getInt("created_at"));
                row.put("usedAt", rs.getInt("used_at"));
                keys.add(row);
            }
        } catch (Exception ignored) {
        }

        return keys;
    }

    private boolean isAdmin(ShiinaRequest shiina) {
        return shiina.loggedIn
            && shiina.user != null
            && PermissionHelper.hasPrivileges(shiina.user.priv, Privileges.ADMINISTRATOR);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
