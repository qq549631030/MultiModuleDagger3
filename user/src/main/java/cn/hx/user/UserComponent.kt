package cn.hx.user

import cn.hx.base.BaseDispatchingInjector
import dagger.Subcomponent

@UserScope
@Subcomponent(modules = [UserModule::class, UserBindModule::class])
interface UserComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(): UserComponent
    }

    fun inject(baseDispatchingInjector: BaseDispatchingInjector)
}