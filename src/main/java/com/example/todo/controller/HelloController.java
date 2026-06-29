package com.example.todo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HelloController {

    @GetMapping("/")
    public String root() {
        return "redirect:/todo";
    }

    @GetMapping("/hello")
    public String hello(Model model) {
        model.addAttribute("message", "Hello! Spring Boot");
        return "hello";
    }
}
