package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.config.AppConfig
import es.sebas1705.owlsplit.data.remote.FirestoreInviteService
import es.sebas1705.owlsplit.data.repository.invite.InviteService
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import es.sebas1705.owlsplit.domain.usecases.invite.RegenerateInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import es.sebas1705.owlsplit.feature.invite.InviteViewModel
import es.sebas1705.owlsplit.feature.invite.JoinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Invitations: the link to share and joining with a code. Needs [groupsModule] and a platform `TextSharer`. */
val inviteModule = module {
    single<InviteService> { FirestoreInviteService(get(), get()) }
    factory { ShareInvite(get(), get(), get(), get(), AppConfig.INVITE_BASE_URL) }
    factory { RegenerateInvite(get(), get(), get(), get()) }
    factory { PreviewInvite(get()) }
    factory { JoinGroup(get(), get(), get()) }
    factory { ClaimMember(get(), get(), get(), get(), get()) }

    viewModel { (groupId: String) -> InviteViewModel(groupId, get(), get(), get()) }
    viewModel { (code: String?) -> JoinViewModel(code, get(), get(), get(), get(), get()) }
}
