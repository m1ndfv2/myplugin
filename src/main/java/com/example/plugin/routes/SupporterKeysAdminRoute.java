package com.example.plugin.routes;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.plugin.Plugin;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.models.NavbarAdminItem;
import dev.osunolimits.utils.osu.PermissionHelper;
import dev.osunolimits.utils.osu.PermissionHelper.Privileges;
import spark.Request;
import spark.Response;

public class SupporterKeysAdminRoute extends Shiina {
    private final NavbarAdminItem adminNavItem;

    public SupporterKeysAdminRoute(NavbarAdminItem adminNavItem) {
        this.adminNavItem = adminNavItem;
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!canManageSupporterKeys(shiina)) {
            res.status(403);
            shiina.data.put("statusError", "Only administrators can access this page.");
            return renderTemplate("admin/supporter-keys.html", shiina, res, req);
        }

        if ("POST".equalsIgnoreCase(req.requestMethod())) {
            handleGenerate(req, shiina);
        }

        shiina.data.put("actNav", adminNavItem.getActNav());
        shiina.data.put("seo", new SEOBuilder("Supporter Keys Admin", App.customization.get("homeDescription").toString()));
        shiina.data.put("generatedKeys", loadGeneratedKeys(shiina));

        return renderTemplate("admin/supporter-keys.html", shiina, res, req);
    }

    private void handleGenerate(Request req, ShiinaRequest shiina) {
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

        if (Plugin.pluginLogger != null) {
            Plugin.pluginLogger.info(
                "Starting supporter key generation: amount={}, durationDays={}, requestedBy={}",
                amount,
                durationDays,
                shiina.user != null ? shiina.user.id : 0
            );
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
                if (Plugin.pluginLogger != null) {
                    Plugin.pluginLogger.info(
                        "Generated supporter key: code={}, durationDays={}, createdBy={}",
                        code,
                        durationDays,
                        shiina.user != null ? shiina.user.id : 0
                    );
                }
            }
        }

        if (Plugin.pluginLogger != null) {
            Plugin.pluginLogger.info(
                "Supporter key generation finished: generated={}, requestedAmount={}, durationDays={}, requestedBy={}",
                created.size(),
                amount,
                durationDays,
                shiina.user != null ? shiina.user.id : 0
            );
        }

        shiina.data.put("statusMessage", "Generated " + created.size() + " key(s) for " + durationDays + " day(s).");
        shiina.data.put("newKeys", created);
    }

    private List<Map<String, Object>> loadGeneratedKeys(ShiinaRequest shiina) {
        List<Map<String, Object>> keys = new ArrayList<>();

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

    private boolean canManageSupporterKeys(ShiinaRequest shiina) {
        if (!shiina.loggedIn || shiina.user == null) {
            return false;
        }

        return PermissionHelper.hasPrivileges(shiina.user.priv, Privileges.ADMINISTRATOR)
            || PermissionHelper.hasPrivileges(shiina.user.priv, Privileges.DEVELOPER);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
