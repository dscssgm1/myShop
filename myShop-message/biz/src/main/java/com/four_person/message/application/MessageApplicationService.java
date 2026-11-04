package com.four_person.message.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.message.domain.model.Message;
import com.four_person.message.domain.model.MessageSession;
import com.four_person.message.domain.model.Notice;
import com.four_person.message.enums.MessageErrorCode;
import com.four_person.message.infrastructure.persistence.mapper.MessageMapper;
import com.four_person.message.infrastructure.persistence.mapper.MessageSessionMapper;
import com.four_person.message.infrastructure.persistence.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageApplicationService {

    private final MessageSessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final NoticeMapper noticeMapper;

    // ---------------- 买家 ----------------

    public List<MessageDtos.SessionVO> buyerSessions() {
        return sessionMapper.selectList(Wrappers.<MessageSession>lambdaQuery()
                        .eq(MessageSession::getCustomerId, UserContext.getUserId())
                        .orderByDesc(MessageSession::getLastTime)
                        .orderByDesc(MessageSession::getId))
                .stream().map(this::toVO).toList();
    }

    /** 获取或创建与某店铺的客服会话 */
    @Transactional
    public MessageDtos.SessionDetailVO shopSession(Long shopId, String shopName, String shopIcon) {
        Long customerId = UserContext.getUserId();
        MessageSession session = sessionMapper.selectOne(Wrappers.<MessageSession>lambdaQuery()
                .eq(MessageSession::getType, MessageSession.TYPE_SHOP)
                .eq(MessageSession::getShopId, shopId)
                .eq(MessageSession::getCustomerId, customerId)
                .last("LIMIT 1"));
        if (session == null) {
            session = new MessageSession();
            session.setType(MessageSession.TYPE_SHOP);
            session.setShopId(shopId);
            session.setShopName(shopName == null ? "" : shopName);
            session.setShopIcon(shopIcon == null ? "" : shopIcon);
            session.setCustomerId(customerId);
            session.setCustomerName("");
            session.setBuyerUnread(0);
            session.setCsUnread(0);
            session.setLastTime(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        return detail(session.getId());
    }

    /** 平台客服会话（买家侧） */
    @Transactional
    public MessageDtos.SessionDetailVO platformSession() {
        Long customerId = UserContext.getUserId();
        MessageSession session = sessionMapper.selectOne(Wrappers.<MessageSession>lambdaQuery()
                .eq(MessageSession::getType, MessageSession.TYPE_PLATFORM)
                .eq(MessageSession::getCustomerId, customerId)
                .last("LIMIT 1"));
        if (session == null) {
            session = new MessageSession();
            session.setType(MessageSession.TYPE_PLATFORM);
            session.setShopName("平台客服");
            session.setShopIcon("🛡️");
            session.setCustomerId(customerId);
            session.setCustomerName("");
            session.setBuyerUnread(0);
            session.setCsUnread(0);
            session.setLastTime(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        return detail(session.getId());
    }

    public MessageDtos.SessionDetailVO detail(Long sessionId) {
        MessageSession session = require(sessionId);
        if (!session.getCustomerId().equals(UserContext.getUserId())
                && !"MERCHANT".equals(UserContext.getGroup())
                && !"PLATFORM".equals(UserContext.getGroup())) {
            throw new BusinessException(MessageErrorCode.SESSION_FORBIDDEN);
        }
        MessageDtos.SessionDetailVO detail = new MessageDtos.SessionDetailVO();
        detail.setSession(toVO(session));
        detail.setMessages(messages(sessionId));
        return detail;
    }

    @Transactional
    public MessageDtos.MessageVO send(Long sessionId, String content) {
        MessageSession session = require(sessionId);
        if (!session.getCustomerId().equals(UserContext.getUserId())) {
            throw new BusinessException(MessageErrorCode.SESSION_FORBIDDEN);
        }
        return append(session, Message.SENDER_BUYER, content);
    }

    @Transactional
    public void readByBuyer(Long sessionId) {
        MessageSession session = require(sessionId);
        if (!session.getCustomerId().equals(UserContext.getUserId())) {
            throw new BusinessException(MessageErrorCode.SESSION_FORBIDDEN);
        }
        session.setBuyerUnread(0);
        sessionMapper.updateById(session);
    }

    // ---------------- 客服 ----------------

    public List<MessageDtos.SessionVO> csSessions(String type, Long shopId) {
        AccessGuard.requirePerm("merchant:cs", "admin:cs");
        String sessionType = StringUtils.hasText(type) ? type : MessageSession.TYPE_SHOP;
        Long scopeShopId = shopId != null ? shopId : UserContext.getShopId();
        return sessionMapper.selectList(Wrappers.<MessageSession>lambdaQuery()
                        .eq(MessageSession::getType, sessionType)
                        .eq(MessageSession.TYPE_SHOP.equals(sessionType) && scopeShopId != null,
                                MessageSession::getShopId, scopeShopId)
                        .orderByDesc(MessageSession::getLastTime)
                        .orderByDesc(MessageSession::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public MessageDtos.MessageVO csReply(Long sessionId, String content) {
        AccessGuard.requirePerm("merchant:cs", "admin:cs");
        MessageSession session = require(sessionId);
        return append(session, Message.SENDER_CS, content);
    }

    @Transactional
    public void readByCs(Long sessionId) {
        AccessGuard.requirePerm("merchant:cs", "admin:cs");
        MessageSession session = require(sessionId);
        session.setCsUnread(0);
        sessionMapper.updateById(session);
    }

    // ---------------- 通知 ----------------

    public List<MessageDtos.NoticeVO> notices() {
        return noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                        .eq(Notice::getUserId, UserContext.getUserId())
                        .orderByDesc(Notice::getId))
                .stream().map(this::toNoticeVO).toList();
    }

    public void readNotice(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice != null && notice.getUserId().equals(UserContext.getUserId())) {
            notice.setReadFlag(1);
            noticeMapper.updateById(notice);
        }
    }

    /** 内部接口：发送系统通知 */
    public void pushNotice(MessageDtos.NoticeRequest request) {
        Notice notice = new Notice();
        notice.setUserId(request.getUserId());
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setReadFlag(0);
        noticeMapper.insert(notice);
    }

    // ---------------- 私有方法 ----------------

    private MessageSession require(Long sessionId) {
        MessageSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(MessageErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private List<MessageDtos.MessageVO> messages(Long sessionId) {
        return messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getId))
                .stream().map(message -> {
                    MessageDtos.MessageVO vo = new MessageDtos.MessageVO();
                    vo.setId(message.getId());
                    vo.setSenderType(message.getSenderType());
                    vo.setContent(message.getContent());
                    vo.setCreatedAt(message.getCreatedAt());
                    return vo;
                }).toList();
    }

    private MessageDtos.MessageVO append(MessageSession session, String senderType, String content) {
        Message message = new Message();
        message.setSessionId(session.getId());
        message.setSenderType(senderType);
        message.setContent(content);
        messageMapper.insert(message);

        session.setLastContent(content.length() > 100 ? content.substring(0, 100) : content);
        session.setLastTime(LocalDateTime.now());
        if (Message.SENDER_BUYER.equals(senderType)) {
            session.setBuyerUnread(0);
            session.setCsUnread((session.getCsUnread() == null ? 0 : session.getCsUnread()) + 1);
        } else {
            session.setCsUnread(0);
            session.setBuyerUnread((session.getBuyerUnread() == null ? 0 : session.getBuyerUnread()) + 1);
        }
        sessionMapper.updateById(session);

        MessageDtos.MessageVO vo = new MessageDtos.MessageVO();
        vo.setId(message.getId());
        vo.setSenderType(senderType);
        vo.setContent(content);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    private MessageDtos.SessionVO toVO(MessageSession session) {
        MessageDtos.SessionVO vo = new MessageDtos.SessionVO();
        vo.setId(session.getId());
        vo.setType(session.getType());
        vo.setShopId(session.getShopId());
        vo.setShopName(session.getShopName());
        vo.setShopIcon(session.getShopIcon());
        vo.setCustomerId(session.getCustomerId());
        vo.setCustomerName(session.getCustomerName());
        vo.setLastContent(session.getLastContent());
        vo.setLastTime(session.getLastTime());
        vo.setBuyerUnread(session.getBuyerUnread());
        vo.setCsUnread(session.getCsUnread());
        return vo;
    }

    private MessageDtos.NoticeVO toNoticeVO(Notice notice) {
        MessageDtos.NoticeVO vo = new MessageDtos.NoticeVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setRead(notice.getReadFlag() != null && notice.getReadFlag() == 1);
        vo.setCreatedAt(notice.getCreatedAt());
        return vo;
    }
}
