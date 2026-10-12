package es.sebas1705.owlsplit.config

import es.sebas1705.owlsplit.BuildConfig

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val updatableVersionCode: Long? = if (BuildConfig.DEBUG) null else BuildConfig.VERSION_CODE.toLong()
