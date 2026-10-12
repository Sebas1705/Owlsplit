package es.sebas1705.owlsplit.config

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

/** iOS installs come from TestFlight or the App Store, which tell about updates themselves. */
actual val updatableVersionCode: Long? = null
