package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.model.UserLoginRecord;
import com.four_person.system.domain.model.UserTrustedDevice;
import com.four_person.system.domain.repository.LoginRecordRepository;
import com.four_person.system.domain.repository.TrustedDeviceRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.mail.MailService;
import com.four_person.system.infrastructure.security.JwtTokenProvider;
import com.four_person.system.infrastructure.support.ClientIpUtils;
import com.four_person.system.infrastructure.support.DeviceUtils;
import com.four_person.system.infrastructure.support.SendCodeRateLimiter;
import com.four_person.system.infrastructure.support.VerificationCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 账号安全用例：登录记录、信任设备、邮箱身份验证。
 * <p>
 * 浏览器无法读取 MAC 地址，设备以前端生成的持久 UUID（X-Device-Id 头）标识；
 * 完成任一邮箱验证码验证（注册/找回密码/改邮箱/改密码）后可将设备标记为信任，
 * 信任设备修改密码/邮箱时免「向当前邮箱发码」的身份验证步骤。
 */
@Service
@RequiredArgsConstructor
public class SecurityApplicationService {

    private static final String PURPOSE_SECURITY_VERIFY = "SECURITY_VERIFY";
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int RECENT_LOGIN_LIMIT = 10;

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final VerificationCodeStore verificationCodeStore;
    private final SendCodeRateLimiter sendCodeRateLimiter;
    private final MailService mailService;
    private final JwtTokenProvider tokenProvider;

    /** 与登录验证码一致：原型/联调阶段直接回传验证码，生产环境应关闭 */
    @Value("${myshop.security.email-code.debug:true}")
    private boolean emailCodeDebug;

    // ==================== 登录记录 ====================

    /** 登录/注册成功后追加一条登录记录 */
    public void recordLogin(Long userId, String ip, String deviceId, String deviceName) {
        UserLoginRecord record = new UserLoginRecord();
        record.setUserId(userId);
        record.setIp(ip);
        record.setDeviceId(deviceId == null ? "" : deviceId);
        record.setDeviceName(deviceName == null ? "" : deviceName);
        record.setLoginAt(LocalDateTime.now());
        loginRecordRepository.save(record);
    }

    // ==================== 安全概览 ====================

    public ProfileDtos.SecurityOverviewVO overview(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        String currentDeviceId = DeviceUtils.currentDeviceId();

        ProfileDtos.SecurityOverviewVO vo = new ProfileDtos.SecurityOverviewVO();
        vo.setMaskedEmail(maskEmail(user.getEmail()));
        vo.setCurrentDeviceTrusted(isTrusted(userId, currentDeviceId));
        vo.setCurrentDeviceId(currentDeviceId);
        vo.setCurrentDeviceName(DeviceUtils.currentDeviceName());
        vo.setRecentLogins(loginRecordRepository.findRecent(userId, RECENT_LOGIN_LIMIT).stream()
                .map(this::toLoginVO).toList());
        vo.setTrustedDevices(trustedDeviceRepository.findByUserId(userId).stream()
                .map(device -> toDeviceVO(device, currentDeviceId)).toList());
        return vo;
    }

    private ProfileDtos.LoginRecordVO toLoginVO(UserLoginRecord record) {
        ProfileDtos.LoginRecordVO vo = new ProfileDtos.LoginRecordVO();
        vo.setId(record.getId());
        vo.setIp(record.getIp());
        vo.setDeviceName(record.getDeviceName());
        vo.setLoginAt(record.getLoginAt());
        return vo;
    }

    private ProfileDtos.TrustedDeviceVO toDeviceVO(UserTrustedDevice device, String currentDeviceId) {
        ProfileDtos.TrustedDeviceVO vo = new ProfileDtos.TrustedDeviceVO();
        vo.setId(device.getId());
        vo.setDeviceName(device.getDeviceName());
        vo.setTrustedAt(device.getTrustedAt());
        vo.setCurrent(StringUtils.hasText(currentDeviceId) && currentDeviceId.equals(device.getDeviceId()));
        return vo;
    }

    public boolean isTrusted(Long userId, String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return false;
        }
        return trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId).isPresent();
    }

    // ==================== 身份验证（向当前邮箱发码） ====================

    /** 向账号当前邮箱发送身份验证码（邮箱由服务端解析，不信任客户端传入） */
    public AuthDtos.EmailCodeResponse sendIdentityCode(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }

        sendCodeRateLimiter.check(user.getEmail(), ClientIpUtils.currentIp());
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        verificationCodeStore.save(user.getEmail(), PURPOSE_SECURITY_VERIFY, code,
                Duration.ofMinutes(CODE_EXPIRE_MINUTES));
        mailService.sendVerifyCode(user.getEmail(), code, CODE_EXPIRE_MINUTES);

        AuthDtos.EmailCodeResponse response = new AuthDtos.EmailCodeResponse();
        response.setExpiresIn(CODE_EXPIRE_MINUTES * 60);
        response.setCode(emailCodeDebug ? code : null);
        return response;
    }

    /** 校验身份验证码，通过后签发短时效身份验证凭证 */
    public ProfileDtos.VerifyIdentityResponse verifyIdentity(Long userId, String code) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        String stored = verificationCodeStore.findValid(user.getEmail(), PURPOSE_SECURITY_VERIFY)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID));
        if (!stored.equals(code)) {
            throw new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID);
        }
        verificationCodeStore.invalidate(user.getEmail(), PURPOSE_SECURITY_VERIFY);

        ProfileDtos.VerifyIdentityResponse response = new ProfileDtos.VerifyIdentityResponse();
        response.setVerifyToken(tokenProvider.generateDeviceToken(userId));
        response.setExpiresIn(600);
        return response;
    }

    // ==================== 信任设备 ====================

    /** 标记当前设备为信任设备，需出示有效的身份验证凭证 */
    @Transactional
    public ProfileDtos.TrustedDeviceVO trustDevice(Long userId, ProfileDtos.TrustDeviceRequest request) {
        Long tokenUserId = tokenProvider.parseDeviceTokenUserId(request.getVerifyToken());
        if (tokenUserId == null || !tokenUserId.equals(userId)) {
            throw new BusinessException(SystemErrorCode.DEVICE_VERIFY_INVALID);
        }
        UserTrustedDevice device = trustedDeviceRepository
                .findByUserIdAndDeviceId(userId, request.getDeviceId())
                .orElseGet(UserTrustedDevice::new);
        device.setUserId(userId);
        device.setDeviceId(request.getDeviceId());
        device.setDeviceName(StringUtils.hasText(request.getDeviceName())
                ? request.getDeviceName() : request.getDeviceId());
        device.setTrustedAt(LocalDateTime.now());
        trustedDeviceRepository.save(device);
        return toDeviceVO(device, request.getDeviceId());
    }

    public void removeTrustedDevice(Long userId, Long id) {
        trustedDeviceRepository.deleteById(id, userId);
    }

    /** 邮箱脱敏：123****@qq.com */
    static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "未绑定";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String masked = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.substring(0, 2) + "***";
        return masked + email.substring(at);
    }
}
