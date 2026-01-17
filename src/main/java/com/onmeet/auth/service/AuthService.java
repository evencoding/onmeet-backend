package com.onmeet.auth.service;

import com.onmeet.auth.JwtTokenProvider;
import com.onmeet.auth.dto.AuthResponse;
import com.onmeet.auth.dto.CompanyLoginRequest;
import com.onmeet.auth.dto.CompanyResponse;
import com.onmeet.auth.dto.CompanySignupRequest;
import com.onmeet.auth.dto.EmployeeInviteCreateRequest;
import com.onmeet.auth.dto.EmployeeInviteResponse;
import com.onmeet.auth.dto.EmployeeInviteSignupRequest;
import com.onmeet.auth.dto.EmployeeLoginRequest;
import com.onmeet.auth.dto.EmployeeResponse;
import com.onmeet.auth.dto.EmployeeSignupRequest;
import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.common.util.ClockProvider;
import com.onmeet.company.entity.Company;
import com.onmeet.company.entity.CompanyStatus;
import com.onmeet.company.entity.Employee;
import com.onmeet.company.entity.EmployeeInvite;
import com.onmeet.company.entity.EmployeeInviteStatus;
import com.onmeet.company.entity.EmployeeRole;
import com.onmeet.company.entity.EmployeeStatus;
import com.onmeet.company.repository.EmployeeInviteRepository;
import com.onmeet.company.repository.CompanyRepository;
import com.onmeet.company.repository.EmployeeRepository;
import com.onmeet.user.dto.UserResponse;
import com.onmeet.user.entity.User;
import com.onmeet.user.entity.UserStatus;
import com.onmeet.user.repository.UserRepository;
import com.onmeet.user.service.UserMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeInviteRepository employeeInviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ClockProvider clockProvider;

    public AuthService(
        UserRepository userRepository,
        CompanyRepository companyRepository,
        EmployeeRepository employeeRepository,
        EmployeeInviteRepository employeeInviteRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        UserMapper userMapper,
        ClockProvider clockProvider
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.employeeInviteRepository = employeeInviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public AuthResponse companySignup(CompanySignupRequest request) {
        ensureEmailAvailable(request.representativeEmail());
        ensureCompanyDomainAvailable(request.domain());

        String derivedName = deriveName(request.representativeEmail(), request.companyName());
        User user = userRepository.save(new User(
            request.representativeEmail(),
            derivedName,
            passwordEncoder.encode(request.password()),
            UserStatus.ACTIVE,
            null
        ));

        Company company = companyRepository.save(new Company(
            request.companyName(),
            request.domain().trim(),
            CompanyStatus.ACTIVE
        ));

        Employee employee = employeeRepository.save(new Employee(
            user,
            company,
            EmployeeRole.OWNER,
            blankToNull(request.employeeNo()),
            EmployeeStatus.ACTIVE
        ));

        return toAuthResponse(user, company, employee);
    }

    @Transactional
    public AuthResponse employeeSignup(EmployeeSignupRequest request) {
        ensureEmailAvailable(request.email());
        Company company = resolveCompany(request.companyId(), request.companyDomain());

        User user = userRepository.save(new User(
            request.email(),
            request.name(),
            passwordEncoder.encode(request.password()),
            UserStatus.ACTIVE,
            null
        ));

        Employee employee = employeeRepository.save(new Employee(
            user,
            company,
            EmployeeRole.MEMBER,
            blankToNull(request.employeeNo()),
            EmployeeStatus.ACTIVE
        ));

        return toAuthResponse(user, company, employee);
    }

    @Transactional(readOnly = true)
    public AuthResponse companyLogin(CompanyLoginRequest request) {
        User user = authenticateUser(request.email(), request.password());
        Company company = resolveCompany(request.companyId(), request.companyDomain());
        Employee employee = employeeRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "Not a company employee"));
        if (employee.getRole() != EmployeeRole.OWNER && employee.getRole() != EmployeeRole.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "Insufficient company role");
        }
        return toAuthResponse(user, company, employee);
    }

    @Transactional(readOnly = true)
    public AuthResponse employeeLogin(EmployeeLoginRequest request) {
        User user = authenticateUser(request.email(), request.password());
        Company company = resolveCompany(request.companyId(), request.companyDomain());
        Employee employee = employeeRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "Not an employee"));
        return toAuthResponse(user, company, employee);
    }

    @Transactional
    public EmployeeInviteResponse createEmployeeInvite(EmployeeInviteCreateRequest request) {
        Company company = resolveCompany(request.companyId(), null);
        Employee inviter = employeeRepository.findByUserIdAndCompanyId(request.inviterUserId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "Only company members can invite"));
        if (inviter.getRole() != EmployeeRole.OWNER && inviter.getRole() != EmployeeRole.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "Only admins can invite employees");
        }

        employeeInviteRepository.findByCompanyIdAndEmailAndStatus(
            company.getId(),
            request.email(),
            EmployeeInviteStatus.INVITED
        ).ifPresent(invite -> {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invite already pending for this email");
        });

        String token = UUID.randomUUID().toString();
        Instant expiresAt = clockProvider.now().plus(7, ChronoUnit.DAYS);
        EmployeeInvite invite = new EmployeeInvite(
            company,
            request.email(),
            request.role(),
            blankToNull(request.employeeNo()),
            token,
            expiresAt
        );
        EmployeeInvite saved = employeeInviteRepository.save(invite);

        return new EmployeeInviteResponse(
            saved.getId(),
            company.getId(),
            saved.getEmail(),
            saved.getRole(),
            saved.getEmployeeNo(),
            saved.getStatus(),
            saved.getToken(),
            saved.getCreatedAt(),
            saved.getExpiresAt()
        );
    }

    @Transactional
    public AuthResponse employeeInviteSignup(EmployeeInviteSignupRequest request) {
        EmployeeInvite invite = employeeInviteRepository.findByToken(request.token())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Invite not found"));
        if (invite.getStatus() != EmployeeInviteStatus.INVITED) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invite is not active");
        }
        Instant now = clockProvider.now();
        if (invite.getExpiresAt() != null && now.isAfter(invite.getExpiresAt())) {
            invite.markExpired(invite.getExpiresAt());
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invite expired");
        }
        if (!invite.getEmail().equalsIgnoreCase(request.email())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invite email does not match");
        }
        if (invite.getEmployeeNo() != null && !invite.getEmployeeNo().equals(request.employeeNo())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Employee number does not match invite");
        }
        if (invite.getRole() != null && invite.getRole() != request.role()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Role does not match invite");
        }
        ensureEmailAvailable(request.email());

        User user = userRepository.save(new User(
            request.email(),
            request.name(),
            passwordEncoder.encode(request.password()),
            UserStatus.ACTIVE,
            null
        ));

        Employee employee = employeeRepository.save(new Employee(
            user,
            invite.getCompany(),
            invite.getRole() == null ? request.role() : invite.getRole(),
            request.employeeNo(),
            EmployeeStatus.ACTIVE
        ));
        invite.markAccepted(now);
        return toAuthResponse(user, invite.getCompany(), employee);
    }

    private User authenticateUser(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "User is not active");
        }
        return user;
    }

    private Company resolveCompany(String companyId, String companyDomain) {
        if (hasText(companyId)) {
            return companyRepository.findById(companyId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Company not found"));
        }
        if (hasText(companyDomain)) {
            return companyRepository.findByDomain(companyDomain.trim())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Company not found"));
        }
        throw new BizException(ErrorCode.INVALID_REQUEST, "Company identifier is required");
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Email already in use");
        }
    }

    private void ensureCompanyDomainAvailable(String companyDomain) {
        if (hasText(companyDomain)) {
            Optional<Company> existing = companyRepository.findByDomain(companyDomain.trim());
            if (existing.isPresent()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "Company domain already in use");
            }
        }
    }

    private AuthResponse toAuthResponse(User user, Company company, Employee employee) {
        String token = jwtTokenProvider.createToken(user);
        UserResponse userResponse = userMapper.toResponse(user);
        CompanyResponse companyResponse = new CompanyResponse(
            company.getId(),
            company.getName(),
            company.getDomain(),
            company.getStatus(),
            company.getCreatedAt(),
            company.getUpdatedAt()
        );
        EmployeeResponse employeeResponse = new EmployeeResponse(
            employee.getId(),
            user.getId(),
            company.getId(),
            employee.getRole(),
            employee.getStatus(),
            employee.getEmployeeNo(),
            employee.getCreatedAt()
        );
        return new AuthResponse(token, "Bearer", userResponse, companyResponse, employeeResponse);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String deriveName(String email, String fallback) {
        if (email != null && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).trim();
            if (!localPart.isEmpty()) {
                return localPart;
            }
        }
        return fallback;
    }
}
