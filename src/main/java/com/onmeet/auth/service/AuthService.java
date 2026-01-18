package com.onmeet.auth.service;

import com.onmeet.auth.JwtTokenProvider;
import com.onmeet.auth.dto.AuthResponse;
import com.onmeet.auth.dto.CompanyEmailVerificationConfirmRequest;
import com.onmeet.auth.dto.CompanyEmailVerificationRequest;
import com.onmeet.auth.dto.CompanyEmailVerificationResponse;
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
import com.onmeet.company.entity.company.Company;
import com.onmeet.company.entity.company.CompanyEmailVerification;
import com.onmeet.company.entity.company.CompanyEmailVerificationStatus;
import com.onmeet.company.entity.company.CompanyStatus;
import com.onmeet.company.entity.department.Department;
import com.onmeet.company.entity.department.DepartmentStatus;
import com.onmeet.company.entity.employee.Employee;
import com.onmeet.company.entity.employee.EmployeeInvite;
import com.onmeet.company.entity.employee.EmployeeInviteStatus;
import com.onmeet.company.entity.employee.EmployeeRole;
import com.onmeet.company.entity.employee.EmployeeStatus;
import com.onmeet.company.entity.position.Position;
import com.onmeet.company.entity.position.PositionStatus;
import com.onmeet.company.repository.EmployeeInviteRepository;
import com.onmeet.company.repository.CompanyEmailVerificationRepository;
import com.onmeet.company.repository.CompanyRepository;
import com.onmeet.company.repository.DepartmentRepository;
import com.onmeet.company.repository.EmployeeRepository;
import com.onmeet.company.repository.PositionRepository;
import com.onmeet.user.dto.UserResponse;
import com.onmeet.user.entity.User;
import com.onmeet.user.entity.UserStatus;
import com.onmeet.user.repository.UserRepository;
import com.onmeet.user.service.UserMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeInviteRepository employeeInviteRepository;
    private final CompanyEmailVerificationRepository companyEmailVerificationRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ClockProvider clockProvider;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String verificationBaseUrl;

    public AuthService(
        UserRepository userRepository,
        CompanyRepository companyRepository,
        EmployeeRepository employeeRepository,
        EmployeeInviteRepository employeeInviteRepository,
        CompanyEmailVerificationRepository companyEmailVerificationRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        UserMapper userMapper,
        ClockProvider clockProvider,
        JavaMailSender mailSender,
        @Value("${app.mail.from:no-reply@onmeet.com}") String mailFrom,
        @Value("${app.company-verification.base-url:https://onmeet.example.com/verify-company}") String verificationBaseUrl
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.employeeInviteRepository = employeeInviteRepository;
        this.companyEmailVerificationRepository = companyEmailVerificationRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.clockProvider = clockProvider;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Transactional
    public CompanyEmailVerificationResponse requestCompanyEmailVerification(CompanyEmailVerificationRequest request) {
        ensureEmailAvailable(request.representativeEmail());
        ensureCompanyDomainAvailable(request.domain());

        companyEmailVerificationRepository.findByEmailAndStatus(
            request.representativeEmail(),
            CompanyEmailVerificationStatus.PENDING
        ).ifPresent(existing -> {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이미 이메일 인증이 진행 중입니다");
        });

        String token = UUID.randomUUID().toString();
        Instant expiresAt = clockProvider.now().plus(30, ChronoUnit.MINUTES);
        CompanyEmailVerification verification = new CompanyEmailVerification(
            request.representativeEmail(),
            request.companyName(),
            request.domain().trim(),
            request.companySize(),
            token,
            expiresAt
        );
        CompanyEmailVerification saved = companyEmailVerificationRepository.save(verification);

        sendVerificationEmail(saved);
        return toVerificationResponse(saved);
    }

    @Transactional
    public CompanyEmailVerificationResponse confirmCompanyEmailVerification(CompanyEmailVerificationConfirmRequest request) {
        CompanyEmailVerification verification = companyEmailVerificationRepository.findByToken(request.token())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "인증 토큰을 찾을 수 없습니다"));
        if (verification.getStatus() != CompanyEmailVerificationStatus.PENDING) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "대기 중인 인증 요청이 아닙니다");
        }
        Instant now = clockProvider.now();
        if (verification.getExpiresAt() != null && now.isAfter(verification.getExpiresAt())) {
            verification.markExpired(verification.getExpiresAt());
            throw new BizException(ErrorCode.INVALID_REQUEST, "인증 시간이 만료되었습니다");
        }
        verification.markVerified(now);
        return toVerificationResponse(verification);
    }

    @Transactional
    public AuthResponse companySignup(CompanySignupRequest request) {
        CompanyEmailVerification verification = companyEmailVerificationRepository.findByToken(request.verificationToken())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "인증 토큰을 찾을 수 없습니다"));
        if (verification.getStatus() != CompanyEmailVerificationStatus.VERIFIED) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이메일 인증이 완료되지 않았습니다");
        }
        if (!verification.getEmail().equalsIgnoreCase(request.representativeEmail())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "대표자 이메일이 인증 정보와 일치하지 않습니다");
        }
        if (!verification.getCompanyName().equals(request.companyName())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "회사명이 인증 정보와 일치하지 않습니다");
        }
        if (!verification.getDomain().equalsIgnoreCase(request.domain().trim())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "회사 도메인이 인증 정보와 일치하지 않습니다");
        }
        ensureEmailAvailable(request.representativeEmail());
        ensureCompanyDomainAvailable(request.domain());

        User user = userRepository.save(new User(
            request.representativeEmail(),
            request.representativeName(),
            passwordEncoder.encode(request.password()),
            UserStatus.ACTIVE,
            null
        ));

        Company company = companyRepository.save(new Company(
            request.companyName(),
            request.domain().trim(),
            verification.getCompanySize(),
            CompanyStatus.ACTIVE
        ));

        Employee employee = employeeRepository.save(new Employee(
            user,
            company,
            null,
            null,
            EmployeeRole.OWNER,
            blankToNull(request.employeeNo()),
            EmployeeStatus.ACTIVE
        ));

        verification.markConsumed(clockProvider.now());
        return toAuthResponse(user, company, employee);
    }

    @Transactional
    public AuthResponse employeeSignup(EmployeeSignupRequest request) {
        ensureEmailAvailable(request.email());
        Company company = resolveCompany(request.companyId(), request.companyDomain());
        ensureCompanyActive(company);
        Department department = resolveDepartment(company, request.departmentId(), "부서");
        Position position = resolvePosition(company, request.positionId(), "직급");

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
            department,
            position,
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
        ensureCompanyActive(company);
        Employee employee = employeeRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "회사 소속 사원이 아닙니다"));
        if (employee.getRole() != EmployeeRole.OWNER && employee.getRole() != EmployeeRole.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "회사 권한이 없습니다");
        }
        ensureEmployeeActive(employee);
        return toAuthResponse(user, company, employee);
    }

    @Transactional(readOnly = true)
    public AuthResponse employeeLogin(EmployeeLoginRequest request) {
        User user = authenticateUser(request.email(), request.password());
        Company company = resolveCompany(request.companyId(), request.companyDomain());
        ensureCompanyActive(company);
        Employee employee = employeeRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "사원 정보가 없습니다"));
        ensureEmployeeActive(employee);
        return toAuthResponse(user, company, employee);
    }

    @Transactional
    public EmployeeInviteResponse createEmployeeInvite(EmployeeInviteCreateRequest request) {
        Company company = resolveCompany(request.companyId(), null);
        ensureCompanyActive(company);
        Employee inviter = employeeRepository.findByUserIdAndCompanyId(request.inviterUserId(), company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "회사 소속만 초대할 수 있습니다"));
        ensureEmployeeActive(inviter);
        if (inviter.getRole() != EmployeeRole.OWNER && inviter.getRole() != EmployeeRole.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "관리자만 사원을 초대할 수 있습니다");
        }
        ensureEmailAvailable(request.email());

        Department department = resolveDepartment(company, request.departmentId(), "부서");
        Position position = resolvePosition(company, request.positionId(), "직급");

        employeeInviteRepository.findByCompanyIdAndEmailAndStatus(
            company.getId(),
            request.email(),
            EmployeeInviteStatus.INVITED
        ).ifPresent(invite -> {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이미 초대가 발송된 이메일입니다");
        });

        String token = UUID.randomUUID().toString();
        Instant expiresAt = clockProvider.now().plus(7, ChronoUnit.DAYS);
        EmployeeInvite invite = new EmployeeInvite(
            company,
            request.email(),
            department,
            position,
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
            saved.getDepartment() == null ? null : saved.getDepartment().getId(),
            saved.getPosition() == null ? null : saved.getPosition().getId(),
            saved.getStatus(),
            saved.getToken(),
            saved.getCreatedAt(),
            saved.getExpiresAt()
        );
    }

    @Transactional
    public AuthResponse employeeInviteSignup(EmployeeInviteSignupRequest request) {
        EmployeeInvite invite = employeeInviteRepository.findByToken(request.token())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "초대 정보를 찾을 수 없습니다"));
        if (invite.getStatus() != EmployeeInviteStatus.INVITED) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "유효하지 않은 초대입니다");
        }
        Instant now = clockProvider.now();
        if (invite.getExpiresAt() != null && now.isAfter(invite.getExpiresAt())) {
            invite.markExpired(invite.getExpiresAt());
            throw new BizException(ErrorCode.INVALID_REQUEST, "초대가 만료되었습니다");
        }
        ensureCompanyActive(invite.getCompany());
        if (!invite.getEmail().equalsIgnoreCase(request.email())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "초대 이메일이 일치하지 않습니다");
        }
        if (invite.getEmployeeNo() != null && !invite.getEmployeeNo().equals(request.employeeNo())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "사번이 초대 정보와 일치하지 않습니다");
        }
        if (invite.getRole() != request.role()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "직급이 초대 정보와 일치하지 않습니다");
        }
        Department department = resolveInviteDepartment(invite, request.departmentId());
        Position position = resolveInvitePosition(invite, request.positionId());
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
            department,
            position,
            invite.getRole(),
            request.employeeNo(),
            EmployeeStatus.ACTIVE
        ));
        invite.markAccepted(now);
        return toAuthResponse(user, invite.getCompany(), employee);
    }

    private User authenticateUser(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "비활성화된 사용자입니다");
        }
        return user;
    }

    private Company resolveCompany(String companyId, String companyDomain) {
        if (hasText(companyId)) {
            return companyRepository.findById(companyId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "회사를 찾을 수 없습니다"));
        }
        if (hasText(companyDomain)) {
            return companyRepository.findByDomain(companyDomain.trim())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "회사를 찾을 수 없습니다"));
        }
        throw new BizException(ErrorCode.INVALID_REQUEST, "회사 식별 정보가 필요합니다");
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "이미 사용 중인 이메일입니다");
        }
    }

    private void ensureCompanyActive(Company company) {
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "비활성화된 회사입니다");
        }
    }

    private void ensureEmployeeActive(Employee employee) {
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "비활성화된 사원입니다");
        }
    }

    private Department resolveDepartment(Company company, String departmentId, String label) {
        if (departmentId == null || departmentId.isBlank()) {
            return null;
        }
        Department department = departmentRepository.findByIdAndCompanyId(departmentId, company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, label + " 정보를 찾을 수 없습니다"));
        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, label + "가 비활성화 상태입니다");
        }
        return department;
    }

    private Position resolvePosition(Company company, String positionId, String label) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        Position position = positionRepository.findByIdAndCompanyId(positionId, company.getId())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, label + " 정보를 찾을 수 없습니다"));
        if (position.getStatus() != PositionStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, label + "이 비활성화 상태입니다");
        }
        return position;
    }

    private Department resolveInviteDepartment(EmployeeInvite invite, String departmentId) {
        if (invite.getDepartment() != null) {
            if (departmentId != null && !invite.getDepartment().getId().equals(departmentId)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "부서가 초대 정보와 일치하지 않습니다");
            }
            return invite.getDepartment();
        }
        return resolveDepartment(invite.getCompany(), departmentId, "부서");
    }

    private Position resolveInvitePosition(EmployeeInvite invite, String positionId) {
        if (invite.getPosition() != null) {
            if (positionId != null && !invite.getPosition().getId().equals(positionId)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "직급이 초대 정보와 일치하지 않습니다");
            }
            return invite.getPosition();
        }
        return resolvePosition(invite.getCompany(), positionId, "직급");
    }

    private void sendVerificationEmail(CompanyEmailVerification verification) {
        String link = verificationBaseUrl + "?token=" + verification.getToken();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(verification.getEmail());
        message.setSubject("[onMeet] 회사 이메일 인증");
        message.setText("아래 링크를 열어 회사 이메일 인증을 완료해 주세요:\n" + link);
        mailSender.send(message);
    }

    private CompanyEmailVerificationResponse toVerificationResponse(CompanyEmailVerification verification) {
        return new CompanyEmailVerificationResponse(
            verification.getId(),
            verification.getEmail(),
            verification.getCompanyName(),
            verification.getDomain(),
            verification.getCompanySize(),
            verification.getStatus(),
            verification.getToken(),
            verification.getCreatedAt(),
            verification.getVerifiedAt(),
            verification.getExpiresAt()
        );
    }

    private void ensureCompanyDomainAvailable(String companyDomain) {
        if (hasText(companyDomain)) {
            Optional<Company> existing = companyRepository.findByDomain(companyDomain.trim());
            if (existing.isPresent()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "이미 사용 중인 회사 도메인입니다");
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
            company.getCompanySize(),
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
            employee.getDepartment() == null ? null : employee.getDepartment().getId(),
            employee.getPosition() == null ? null : employee.getPosition().getId(),
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

}
