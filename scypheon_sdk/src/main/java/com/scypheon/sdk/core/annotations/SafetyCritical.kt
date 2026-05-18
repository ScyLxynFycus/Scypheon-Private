package com.scypheon.sdk.core.annotations

/**
 * Marks a class as safety-critical for automated coverage gating.
 * Classes with this annotation must meet higher line and branch coverage thresholds.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SafetyCritical
