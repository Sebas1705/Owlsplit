package es.sebas1705.owlsplit.domain.usecases.user

import es.sebas1705.owlsplit.core.common.utils.AppResult
import es.sebas1705.owlsplit.data.models.User
import es.sebas1705.owlsplit.data.repository.IUserRepository
import es.sebas1705.owlsplit.domain.usecases.UseCase

class UpdateProfileUseCase(private val repository: IUserRepository) : UseCase<User, User>() {
    override suspend fun execute(params: User): AppResult<User> = repository.updateProfile(params)
}
