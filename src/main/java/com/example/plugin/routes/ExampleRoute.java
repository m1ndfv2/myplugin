package com.example.plugin.routes;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.models.NavbarItem;
import spark.Request;
import spark.Response;

public class ExampleRoute extends Shiina {
    
    private NavbarItem exampleItem;

    public ExampleRoute(NavbarItem exampleItem) {
        this.exampleItem = exampleItem;
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        shiina.data.put("actNav", exampleItem.getActNav());
        shiina.data.put("seo", new SEOBuilder("Example plugin page", App.customization.get("homeDescription").toString()));
        
        return renderTemplate("modules/plugins/example/example.html", shiina, res, req);
    }
}
