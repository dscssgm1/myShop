package com.four_person.system.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 邮件投递：异步发送 HTML 邮件（Thymeleaf 模板），失败只记录日志不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Async("mailExecutor")
    public void sendVerifyCode(String to, String code, int expireMinutes) {
        try {
            Context context = new Context();
            context.setVariable("code", code);
            context.setVariable("expireMinutes", expireMinutes);
            String html = templateEngine.process("mail/verify-code", context);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("【myShop】邮箱验证码");
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("验证码邮件已投递: to={}", to);
        } catch (Exception ex) {
            log.warn("验证码邮件投递失败: to={}, cause={}", to, ex.getMessage());
        }
    }
}
