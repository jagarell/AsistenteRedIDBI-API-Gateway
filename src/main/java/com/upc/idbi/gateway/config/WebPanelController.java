package com.upc.idbi.gateway.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Panel web (Network Assistant): entrega la página principal de {@code static/web} en {@code /web}. */
@Controller
public class WebPanelController {

    @GetMapping({"/web", "/web/"})
    public String panel() {
        return "forward:/web/index.html";
    }
}
