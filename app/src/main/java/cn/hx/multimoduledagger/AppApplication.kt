package cn.hx.multimoduledagger

import cn.hx.base.BaseApplication
import cn.hx.base.BaseDispatchingInjector
import cn.hx.base.MultiModuleAndroidInjector
import cn.hx.news.NewsComponent
import cn.hx.news.NewsComponentHolder
import cn.hx.news.NewsComponentProvider
import cn.hx.user.UserComponent
import cn.hx.user.UserComponentHolder
import cn.hx.user.UserComponentProvider
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector

class AppApplication : BaseApplication(), NewsComponentProvider, UserComponentProvider,
    HasAndroidInjector {

    lateinit var appComponent: AppComponent

    private val multiModuleAndroidInjector = MultiModuleAndroidInjector()

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        val userDispatchingInjector = BaseDispatchingInjector()
        UserComponentHolder.userComponent.inject(userDispatchingInjector)
        multiModuleAndroidInjector.addInjector(userDispatchingInjector)
        val newsDispatchingInjector = BaseDispatchingInjector()
        NewsComponentHolder.newsComponent.inject(newsDispatchingInjector)
        multiModuleAndroidInjector.addInjector(newsDispatchingInjector)
    }

    override fun provideNewsComponent(): NewsComponent {
        return appComponent.newsComponentFactory().create()
    }

    override fun provideUserComponent(): UserComponent {
        return appComponent.userComponentFactory().create()
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return multiModuleAndroidInjector
    }
}