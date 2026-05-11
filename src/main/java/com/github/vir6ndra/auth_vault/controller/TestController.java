//package com.github.vir6ndra.auth_vault.controller;
//
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/test")
//public class TestController {
//
//    @GetMapping("/secured")
//    public ResponseEntity<String> getSecuredData() {
//        return ResponseEntity.ok("Success! Your JWT is working and you have access to this secured endpoint.");
//    }
//
//    @GetMapping("/admin")
//    public ResponseEntity<String> getAdminData() {
//        return ResponseEntity.ok("Hello Admin! You have special privileges.");
//    }
//}



package com.github.vir6ndra.auth_vault.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/secured")
    public ResponseEntity<String> secured(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok("Hello " + userDetails.getUsername() + " — you are authenticated!");
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<String> adminOnly() {
        return ResponseEntity.ok("Welcome Admin!");
    }
}
