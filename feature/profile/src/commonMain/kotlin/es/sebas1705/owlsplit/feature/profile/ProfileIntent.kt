package es.sebas1705.owlsplit.feature.profile

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.data.models.User

sealed interface ProfileIntent : MVIBaseIntent {
    data object Load                          : ProfileIntent
    data class  Save(val user: User)          : ProfileIntent
    data object ClearSuccess                  : ProfileIntent
    data object ClearError                    : ProfileIntent
}
