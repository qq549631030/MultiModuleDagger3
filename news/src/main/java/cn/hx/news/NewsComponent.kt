package cn.hx.news

import cn.hx.base.BaseDispatchingInjector
import dagger.Subcomponent

@NewsScope
@Subcomponent(modules = [NewsModule::class, NewsBindModule::class])
interface NewsComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): NewsComponent
    }

    fun inject(baseDispatchingInjector: BaseDispatchingInjector)
}