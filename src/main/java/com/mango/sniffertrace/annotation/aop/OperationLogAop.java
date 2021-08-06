package com.mango.sniffertrace.annotation.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.sniffertrace.annotation.OperationLog;
import com.mango.sniffertrace.request.MDCTrace;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @description:
 * @author: caozhibo
 * @date: 2021/6/24 11:31
 */
@Slf4j
@Aspect
@Configuration
public class OperationLogAop {

    @Resource
    private ObjectMapper objectMapper;


    /**
     * 定义切面
     */
    @Pointcut("@annotation(com.mango.sniffertrace.annotation.OperationLog)")
    public void pointCut() {
    }

    @Around("pointCut()")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        // 方法开始时间
        long startTime = System.currentTimeMillis();

        // 从请求头获取日志唯一id
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes != null) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            String traceId = request.getHeader(MDCTrace.HEADER_TRACE);
            if (traceId != null && !"".equals(traceId)) {
                MDCTrace.put(traceId);
            }
        }

        /*
         * MDC
         * 判断是否有唯一id，没有则生成一个并存至MDC
         */
        if (MDCTrace.get() == null || "".equals(MDCTrace.get())) {
            MDCTrace.put();
        }

        // 获取方法签名
        Signature signature = pjp.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        OperationLog operationLog = method.getAnnotation(OperationLog.class);

        StringBuilder header = new StringBuilder();
        String[] headerKey = operationLog.printHeader();
        if (headerKey.length > 0 && servletRequestAttributes != null) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            for (String key : headerKey) {
                header.append(request.getHeader(key));
            }
        }

        log.info("method name:"+ signature.getName()
                        + ",declaringType:" + signature.getDeclaringType()
                        + ",method desc:" + operationLog.desc()
                        + ",request header:" + header
                        + ",request params:" + Arrays.toString(pjp.getArgs()));

        Object result = pjp.proceed();

        log.info("request result:" + objectMapper.writeValueAsString(result)
                        + ",request time consuming:" + (System.currentTimeMillis() - startTime) + "ms");

        MDCTrace.remove();

        return result;
    }
}
