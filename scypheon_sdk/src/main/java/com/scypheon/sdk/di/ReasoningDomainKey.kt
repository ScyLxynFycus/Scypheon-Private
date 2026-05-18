package com.scypheon.sdk.di

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain
import dagger.MapKey

@MapKey
annotation class ReasoningDomainKey(val value: ReasoningDomain)
