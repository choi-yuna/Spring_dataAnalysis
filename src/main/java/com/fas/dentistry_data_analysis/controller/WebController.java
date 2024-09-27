package com.fas.dentistry_data_analysis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

    @Controller
    public class WebController {

        @RequestMapping(value = "/{path:[^.]*}")  // 간단한 패턴
        public String forward() {
            return "forward:/index.html";
        }
    }
