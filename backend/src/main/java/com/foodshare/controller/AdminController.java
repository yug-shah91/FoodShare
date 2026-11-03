package com.foodshare.controller;

import com.foodshare.dto.AdminDtos.Stats;
import com.foodshare.dto.AdminDtos.UserSummary;
import com.foodshare.dto.DonationDtos.DonationResponse;
import com.foodshare.dto.PageResponse;
import com.foodshare.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public Stats dashboard() {
        return adminService.getStats();
    }

    // Both tables are loaded one page at a time (?page=0&size=20, max 50)
    @GetMapping("/users")
    public PageResponse<UserSummary> users(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return adminService.getUsers(page, size);
    }

    @GetMapping("/donations")
    public PageResponse<DonationResponse> donations(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminService.getDonations(page, size);
    }
}
