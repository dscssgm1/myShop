package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.web.AccessGuard;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.domain.model.LogLogin;
import com.four_person.platform.domain.model.LogOperation;
import com.four_person.platform.domain.model.LogSystem;
import com.four_person.platform.infrastructure.persistence.mapper.LogLoginMapper;
import com.four_person.platform.infrastructure.persistence.mapper.LogOperationMapper;
import com.four_person.platform.infrastructure.persistence.mapper.LogSystemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogApplicationService {

    private final LogOperationMapper operationMapper;
    private final LogLoginMapper loginMapper;
    private final LogSystemMapper systemMapper;

    public void recordOperation(PlatformDtos.OperationLogRequest request) {
        LogOperation log = new LogOperation();
        log.setUserId(request.getUserId());
        log.setUserName(request.getUserName());
        log.setAction(request.getAction());
        log.setModule(request.getModule());
        log.setDetail(request.getDetail());
        operationMapper.insert(log);
    }

    public void recordLogin(PlatformDtos.LoginLogRequest request) {
        LogLogin log = new LogLogin();
        log.setUserId(request.getUserId());
        log.setUsername(request.getUsername());
        log.setMethod(request.getMethod());
        log.setIp(request.getIp());
        log.setResult(request.getResult());
        loginMapper.insert(log);
    }

    public void recordSystem(PlatformDtos.SystemLogRequest request) {
        LogSystem log = new LogSystem();
        log.setLevel(request.getLevel() == null ? "INFO" : request.getLevel());
        log.setModule(request.getModule());
        log.setMessage(request.getMessage());
        systemMapper.insert(log);
    }

    public PageResult<PlatformDtos.OperationLogVO> pageOperation(int page, int size) {
        AccessGuard.requirePerm("admin:logs");
        Page<LogOperation> result = operationMapper.selectPage(new Page<>(pageNo(page), pageSize(size)),
                Wrappers.<LogOperation>lambdaQuery().orderByDesc(LogOperation::getId));
        return PageResult.of(result.getRecords().stream().map(log -> {
            PlatformDtos.OperationLogVO vo = new PlatformDtos.OperationLogVO();
            vo.setId(log.getId());
            vo.setUserName(log.getUserName());
            vo.setAction(log.getAction());
            vo.setModule(log.getModule());
            vo.setDetail(log.getDetail());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList(), result.getTotal(), pageNo(page), pageSize(size));
    }

    public PageResult<PlatformDtos.LoginLogVO> pageLogin(int page, int size) {
        AccessGuard.requirePerm("admin:logs");
        Page<LogLogin> result = loginMapper.selectPage(new Page<>(pageNo(page), pageSize(size)),
                Wrappers.<LogLogin>lambdaQuery().orderByDesc(LogLogin::getId));
        return PageResult.of(result.getRecords().stream().map(log -> {
            PlatformDtos.LoginLogVO vo = new PlatformDtos.LoginLogVO();
            vo.setId(log.getId());
            vo.setUsername(log.getUsername());
            vo.setMethod(log.getMethod());
            vo.setIp(log.getIp());
            vo.setResult(log.getResult());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList(), result.getTotal(), pageNo(page), pageSize(size));
    }

    public PageResult<PlatformDtos.SystemLogVO> pageSystem(int page, int size) {
        AccessGuard.requirePerm("admin:logs");
        Page<LogSystem> result = systemMapper.selectPage(new Page<>(pageNo(page), pageSize(size)),
                Wrappers.<LogSystem>lambdaQuery().orderByDesc(LogSystem::getId));
        return PageResult.of(result.getRecords().stream().map(log -> {
            PlatformDtos.SystemLogVO vo = new PlatformDtos.SystemLogVO();
            vo.setId(log.getId());
            vo.setLevel(log.getLevel());
            vo.setModule(log.getModule());
            vo.setMessage(log.getMessage());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList(), result.getTotal(), pageNo(page), pageSize(size));
    }

    private int pageNo(int page) {
        return Math.max(page, 1);
    }

    private int pageSize(int size) {
        return size < 1 ? 20 : Math.min(size, 200);
    }
}
