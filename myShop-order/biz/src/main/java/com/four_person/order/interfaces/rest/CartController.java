package com.four_person.order.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.order.api.dto.CartAddRequest;
import com.four_person.order.api.dto.CartUpdateRequest;
import com.four_person.order.api.dto.CartVO;
import com.four_person.order.application.CartApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartApplicationService cartApplicationService;

    @GetMapping
    public Result<CartVO> get() {
        return Result.success(cartApplicationService.get(UserContext.getUserId()));
    }

    @PostMapping("/items")
    public Result<CartVO> add(@Valid @RequestBody CartAddRequest request) {
        return Result.success(cartApplicationService.add(UserContext.getUserId(), request));
    }

    @PutMapping("/items/{id}")
    public Result<CartVO> update(@PathVariable Long id, @Valid @RequestBody CartUpdateRequest request) {
        return Result.success(cartApplicationService.update(UserContext.getUserId(), id, request));
    }

    @DeleteMapping("/items/{id}")
    public Result<CartVO> remove(@PathVariable Long id) {
        return Result.success(cartApplicationService.remove(UserContext.getUserId(), id));
    }

    @DeleteMapping
    public Result<Void> clear() {
        cartApplicationService.clear(UserContext.getUserId());
        return Result.success();
    }
}
