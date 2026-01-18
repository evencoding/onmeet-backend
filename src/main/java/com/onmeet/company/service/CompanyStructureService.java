package com.onmeet.company.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.company.dto.DepartmentCreateRequest;
import com.onmeet.company.dto.DepartmentResponse;
import com.onmeet.company.dto.PositionCreateRequest;
import com.onmeet.company.dto.PositionResponse;
import com.onmeet.company.entity.company.Company;
import com.onmeet.company.entity.company.CompanyStatus;
import com.onmeet.company.entity.department.Department;
import com.onmeet.company.entity.department.DepartmentStatus;
import com.onmeet.company.entity.position.Position;
import com.onmeet.company.entity.position.PositionStatus;
import com.onmeet.company.repository.CompanyRepository;
import com.onmeet.company.repository.DepartmentRepository;
import com.onmeet.company.repository.PositionRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyStructureService {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    public CompanyStructureService(
        CompanyRepository companyRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository
    ) {
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional
    public DepartmentResponse createDepartment(String companyId, DepartmentCreateRequest request) {
        Company company = getActiveCompany(companyId);
        if (departmentRepository.existsByCompanyIdAndNameIgnoreCase(companyId, request.name())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이미 존재하는 부서명입니다");
        }
        Department department = departmentRepository.save(
            new Department(company, request.name().trim(), DepartmentStatus.ACTIVE)
        );
        return toDepartmentResponse(department);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments(String companyId) {
        getActiveCompany(companyId);
        return departmentRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
            .map(this::toDepartmentResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public PositionResponse createPosition(String companyId, PositionCreateRequest request) {
        Company company = getActiveCompany(companyId);
        if (positionRepository.existsByCompanyIdAndNameIgnoreCase(companyId, request.name())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이미 존재하는 직급명입니다");
        }
        Position position = positionRepository.save(
            new Position(company, request.name().trim(), PositionStatus.ACTIVE)
        );
        return toPositionResponse(position);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(String companyId) {
        getActiveCompany(companyId);
        return positionRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
            .map(this::toPositionResponse)
            .collect(Collectors.toList());
    }

    private Company getActiveCompany(String companyId) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "회사를 찾을 수 없습니다"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "비활성화된 회사입니다");
        }
        return company;
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        return new DepartmentResponse(
            department.getId(),
            department.getCompany().getId(),
            department.getName(),
            department.getStatus(),
            department.getCreatedAt(),
            department.getUpdatedAt()
        );
    }

    private PositionResponse toPositionResponse(Position position) {
        return new PositionResponse(
            position.getId(),
            position.getCompany().getId(),
            position.getName(),
            position.getStatus(),
            position.getCreatedAt(),
            position.getUpdatedAt()
        );
    }
}
