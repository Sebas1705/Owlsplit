package es.sebas1705.owlsplit.domain.usecases.user

import es.sebas1705.owlsplit.core.common.utils.AppResult
import es.sebas1705.owlsplit.data.models.User
import es.sebas1705.owlsplit.data.repository.IUserRepository
import es.sebas1705.owlsplit.domain.usecases.NoParamUseCase

class GetMeUseCase(private val repository: IUserRepository) : NoParamUseCase<User>() {
    override suspend fun execute(): AppResult<User> = repository.getMe()
}
