package com.onmeet.common.config

import com.onmeet.common.web.ApiResponseWrappingAdvice
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(ApiResponseWrappingAdvice::class)
class CommonWebAutoConfiguration
