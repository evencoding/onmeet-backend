package com.onmeet.company.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.company.dto.DepartmentCreateRequest;
import com.onmeet.company.dto.DepartmentResponse;
import com.onmeet.company.dto.PositionCreateRequest;
import com.onmeet.company.dto.PositionResponse;
import com.onmeet.company.service.CompanyStructureService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
public class CompanyStructureController {

    private final CompanyStructureService companyStructureService;

    public CompanyStructureController(CompanyStructureService companyStructureService) {
        this.companyStructureService = companyStructureService;
    }

    @PostMapping("/departments")
    public ApiResponse<DepartmentResponse> createDepartment(
        @PathVariable String companyId,
        @Valid @RequestBody DepartmentCreateRequest request
    ) {
        return ApiResponse.ok(companyStructureService.createDepartment(companyId, request));
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentResponse>> listDepartments(@PathVariable String companyId) {
        return ApiResponse.ok(companyStructureService.listDepartments(companyId));
    }

    @PostMapping("/positions")
    public ApiResponse<PositionResponse> createPosition(
        @PathVariable String companyId,
        @Valid @RequestBody PositionCreateRequest request
    ) {
        return ApiResponse.ok(companyStructureService.createPosition(companyId, request));
    }

    @GetMapping("/positions")
    public ApiResponse<List<PositionResponse>> listPositions(@PathVariable String companyId) {
        return ApiResponse.ok(companyStructureService.listPositions(companyId));
    }
}
