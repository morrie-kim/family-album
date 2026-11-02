package com.familyalbum.web;

import com.familyalbum.security.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

@ControllerAdvice(basePackages = "com.familyalbum.web")
public class WebAdvice {

    private static final String TOO_LARGE = "한 번에 올리는 사진의 총 용량이 너무 큽니다. 나누어 올려 주세요.";

    /** 모든 뷰에서 ${me} 로 로그인 사용자 접근 */
    @ModelAttribute("me")
    AppPrincipal me(@AuthenticationPrincipal AppPrincipal principal) {
        return principal;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<String> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView handleTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"uploaded\":[],\"failed\":[],\"error\":\"" + TOO_LARGE + "\"}");
            return null; // 응답을 직접 처리함
        }
        RequestContextUtils.getOutputFlashMap(request).put("error", TOO_LARGE);
        return new ModelAndView("redirect:/photos");
    }
}
